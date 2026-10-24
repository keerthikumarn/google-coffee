package com.googlecoffee.live;

import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.service.SettingsService;
import com.googlecoffee.support.Fixtures;
import com.googlecoffee.support.InMemorySettingsStore;
import com.googlecoffee.support.ManualOrderFeed;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/** The live hub: what each guest and the staff board see, and when they are told. */
class LiveOrderHubTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private ManualOrderFeed feed;
    private SettingsService settings;
    private LiveOrderHub hub;
    private MockMvc mvc;

    /** Exposes the hub's two stream subscriptions over HTTP so a test can read what is pushed. */
    @RestController
    static class Streams {
        private final LiveOrderHub hub;
        private final Map<String, Order> orders = new ConcurrentHashMap<>();

        Streams(LiveOrderHub hub) {
            this.hub = hub;
        }

        volatile SseEmitter lastEmitter;

        @GetMapping(value = "/guest/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter guest(@PathVariable String id) {
            return lastEmitter = hub.subscribeCustomer(orders.get(id));
        }

        @GetMapping(value = "/staff", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter staff() {
            return lastEmitter = hub.subscribeStaff();
        }
    }

    private Streams streams;

    @BeforeEach
    void setUp() {
        feed = new ManualOrderFeed();
        settings = new SettingsService(new InMemorySettingsStore(), Fixtures.props(1));
        hub = new LiveOrderHub(feed, settings, registry);
        hub.start();
        streams = new Streams(hub);
        mvc = MockMvcBuilders.standaloneSetup(streams).build();
    }

    private static Order order(String id, OrderStatus status, int prep, long createdAt) {
        return Fixtures.order(id, "s-" + id, status, prep, createdAt);
    }

    private MvcResult openGuestStream(Order order) throws Exception {
        streams.orders.put(order.id(), order);
        return mvc.perform(get("/guest/" + order.id())).andExpect(request().asyncStarted()).andReturn();
    }

    private MvcResult openStaffStream() throws Exception {
        return mvc.perform(get("/staff")).andExpect(request().asyncStarted()).andReturn();
    }

    private static String received(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString();
    }

    private static int count(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }

    // ---------- the active set ----------

    @Test
    void startsEmptyAndFollowsTheFeedReplacingTheWholeSet() {
        assertTrue(hub.activeOrders().isEmpty());

        feed.publishActive(List.of(order("a", OrderStatus.PLACED, 3, 1), order("b", OrderStatus.PREPARING, 3, 2)));
        assertEquals(2, hub.activeOrders().size());

        feed.publishActive(List.of(order("b", OrderStatus.READY, 3, 2)));
        assertEquals(List.of("b"), hub.activeOrders().stream().map(Order::id).toList());
    }

    // ---------- views of one order ----------

    @Test
    void finishedOrdersHaveNoWaitAndNoPlaceInTheQueue() {
        for (OrderStatus done : List.of(OrderStatus.COLLECTED, OrderStatus.CANCELLED)) {
            OrderView view = hub.viewOf(order("x", done, 3, 1));

            assertEquals(0, view.etaMinutes());
            assertEquals(0, view.queuePosition());
        }
    }

    @Test
    void anOrderJustPlacedJoinsTheEndOfTheQueueBeforeTheFeedHasCaughtUp() {
        feed.publishActive(List.of(order("ahead", OrderStatus.PLACED, 6, 1)));

        OrderView view = hub.viewOf(order("new", OrderStatus.PLACED, 4, 2));

        assertEquals(2, view.queuePosition());
        assertEquals(10, view.etaMinutes());   // 6 minutes ahead + its own 4, one barista
    }

    @Test
    void theQueueUsesTheLatestStateOfTheOrderBeingViewedNotAStaleCopy() {
        feed.publishActive(List.of(order("a", OrderStatus.PLACED, 8, 1)));

        // The same order, now being made, counts for half its prep time.
        OrderView view = hub.viewOf(order("a", OrderStatus.PREPARING, 8, 1));

        assertEquals(1, view.queuePosition());
        assertEquals(4, view.etaMinutes());
    }

    @Test
    void aReadyOrderShowsNoWait() {
        feed.publishActive(List.of(order("a", OrderStatus.READY, 5, 1)));

        OrderView view = hub.viewOf(order("a", OrderStatus.READY, 5, 1));

        assertEquals(0, view.etaMinutes());
        assertEquals(0, view.queuePosition());
    }

    // ---------- the staff board ----------

    @Test
    void anEmptyBoardStillReportsTheBaristas() {
        StaffBoard board = hub.board();

        assertTrue(board.orders().isEmpty());
        assertEquals(1, board.activeBaristas());
        assertTrue(Math.abs(board.serverTime() - System.currentTimeMillis()) < 5_000);
    }

    @Test
    void theBoardListsOrdersOldestFirstWithRunningEtasAndPositions() {
        feed.publishActive(List.of(
                order("second", OrderStatus.PLACED, 4, 20),
                order("ready", OrderStatus.READY, 4, 30),
                order("first", OrderStatus.PLACED, 6, 10)));

        List<OrderView> views = hub.board().orders();

        assertEquals(List.of("first", "second", "ready"), views.stream().map(v -> v.order().id()).toList());
        assertEquals(new OrderView(views.get(0).order(), 6, 1), views.get(0));
        assertEquals(new OrderView(views.get(1).order(), 10, 2), views.get(1));
        assertEquals(new OrderView(views.get(2).order(), 0, 0), views.get(2));
    }

    @Test
    void moreBaristasShortenEveryWaitOnTheBoard() {
        feed.publishActive(List.of(order("a", OrderStatus.PLACED, 6, 1), order("b", OrderStatus.PLACED, 6, 2)));
        assertEquals(12, hub.board().orders().get(1).etaMinutes());

        settings.setActiveBaristas(2);

        assertEquals(2, hub.board().activeBaristas());
        assertEquals(6, hub.board().orders().get(1).etaMinutes());
    }

    // ---------- live pushes to a guest ----------

    @Test
    void aGuestWhoOpensTheTrackerImmediatelyGetsTheCurrentOrder() throws Exception {
        MvcResult stream = openGuestStream(order("a", OrderStatus.PLACED, 3, 1));

        String text = received(stream);
        assertEquals(1, count(text, "event:order"));
        assertTrue(text.contains("\"status\":\"PLACED\""));
        assertTrue(text.contains("\"queuePosition\":1"));
    }

    @Test
    void aGuestIsToldTheLatestStatusEvenIfTheirCopyIsOld() throws Exception {
        feed.publishActive(List.of(order("a", OrderStatus.PREPARING, 3, 1)));

        MvcResult stream = openGuestStream(order("a", OrderStatus.PLACED, 3, 1));

        assertTrue(received(stream).contains("\"status\":\"PREPARING\""));
    }

    @Test
    void aGuestHearsAboutTheirOwnOrderChangingButNotAboutOthers() throws Exception {
        feed.publishActive(List.of(order("mine", OrderStatus.PLACED, 3, 1), order("other", OrderStatus.PLACED, 3, 2)));
        MvcResult stream = openGuestStream(order("mine", OrderStatus.PLACED, 3, 1));
        int before = count(received(stream), "event:order");

        feed.publishActive(List.of(order("mine", OrderStatus.PREPARING, 3, 1), order("other", OrderStatus.READY, 3, 2)));

        String text = received(stream);
        assertEquals(before + 1, count(text, "event:order"));
        assertTrue(text.contains("\"status\":\"PREPARING\""));
        assertFalse(text.contains("\"id\":\"other\""));
    }

    @Test
    void whenAnOrderIsFinishedTheGuestGetsTheFinalStateAndHearsNothingMore() throws Exception {
        feed.publishActive(List.of(order("a", OrderStatus.READY, 3, 1)));
        MvcResult stream = openGuestStream(order("a", OrderStatus.READY, 3, 1));

        feed.publishFinal(order("a", OrderStatus.COLLECTED, 3, 1));
        String afterFinal = received(stream);
        assertTrue(afterFinal.contains("\"status\":\"COLLECTED\""));

        feed.publishActive(List.of(order("a", OrderStatus.READY, 3, 1)));   // would be a stray later update
        assertEquals(afterFinal, received(stream));
    }

    // ---------- live pushes to staff ----------

    @Test
    void staffImmediatelyGetTheWholeBoard() throws Exception {
        feed.publishActive(List.of(order("a", OrderStatus.PLACED, 3, 1)));

        MvcResult stream = openStaffStream();

        String text = received(stream);
        assertEquals(1, count(text, "event:board"));
        assertTrue(text.contains("\"id\":\"a\""));
        assertTrue(text.contains("\"activeBaristas\":1"));
    }

    @Test
    void staffGetANewBoardWheneverTheActiveOrdersChange() throws Exception {
        MvcResult stream = openStaffStream();

        feed.publishActive(List.of(order("a", OrderStatus.PLACED, 3, 1)));
        feed.publishActive(List.of(order("a", OrderStatus.PREPARING, 3, 1)));

        String text = received(stream);
        assertEquals(3, count(text, "event:board"));
        assertTrue(text.contains("\"status\":\"PREPARING\""));
    }

    @Test
    void changingTheBaristaCountUpdatesStaffAndGuestsAtOnce() throws Exception {
        feed.publishActive(List.of(order("a", OrderStatus.PLACED, 6, 1), order("b", OrderStatus.PLACED, 6, 2)));
        MvcResult staff = openStaffStream();
        MvcResult guest = openGuestStream(order("b", OrderStatus.PLACED, 6, 2));
        assertTrue(received(guest).contains("\"etaMinutes\":12"));

        settings.setActiveBaristas(2);

        assertEquals(2, count(received(staff), "event:board"));
        assertTrue(received(staff).contains("\"activeBaristas\":2"));
        assertTrue(received(guest).contains("\"etaMinutes\":6"));
    }

    // ---------- housekeeping ----------

    @Test
    void theHeartbeatPingsEveryOpenStreamAndIsHarmlessWithNone() throws Exception {
        hub.heartbeat();   // nobody connected: must not fail
        MvcResult staff = openStaffStream();
        MvcResult guest = openGuestStream(order("a", OrderStatus.PLACED, 3, 1));

        hub.heartbeat();

        assertEquals(1, count(received(staff), "\n:ping\n"));
        assertEquals(1, count(received(guest), "\n:ping\n"));
    }

    @Test
    void shuttingDownStopsTheFeed() {
        hub.stop();

        assertTrue(feed.stopped());
    }

    // ---------- metrics ----------

    private double subscribers(String stream) {
        var gauge = registry.find("sse.subscribers").tag("stream", stream).gauge();
        return gauge == null ? -1 : gauge.value();
    }

    private double sendFailures(String event) {
        var counter = registry.find("sse.send.failures").tag("event", event).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void theSubscriberGaugesStartAtZero() {
        assertEquals(0, subscribers("customer"));
        assertEquals(0, subscribers("staff"));
    }

    @Test
    void theGaugesCountOpenStaffAndGuestStreams() throws Exception {
        openStaffStream();
        openStaffStream();
        openGuestStream(order("a", OrderStatus.PLACED, 3, 1));
        openGuestStream(order("a", OrderStatus.PLACED, 3, 1));   // the same guest on a second device
        openGuestStream(order("b", OrderStatus.PLACED, 3, 2));

        assertEquals(2, subscribers("staff"));
        assertEquals(3, subscribers("customer"));
    }

    @Test
    void aGuestStreamIsNoLongerCountedOnceTheOrderIsFinished() throws Exception {
        feed.publishActive(List.of(order("a", OrderStatus.READY, 3, 1)));
        openGuestStream(order("a", OrderStatus.READY, 3, 1));
        assertEquals(1, subscribers("customer"));

        feed.publishFinal(order("a", OrderStatus.COLLECTED, 3, 1));

        assertEquals(0, subscribers("customer"));
    }

    @Test
    void healthyStreamsRecordNoSendFailures() throws Exception {
        openStaffStream();
        openGuestStream(order("a", OrderStatus.PLACED, 3, 1));

        feed.publishActive(List.of(order("a", OrderStatus.PREPARING, 3, 1)));
        hub.heartbeat();

        assertEquals(0, sendFailures("board") + sendFailures("order") + sendFailures("heartbeat"));
    }

    @Test
    void aBrokenStaffStreamIsCountedAsASendFailure() throws Exception {
        openStaffStream();
        streams.lastEmitter.completeWithError(new IOException("client went away"));
        // The container has already dropped the connection, but the hub does not know yet.

        feed.publishActive(List.of(order("a", OrderStatus.PLACED, 3, 1)));

        assertTrue(sendFailures("board") >= 1, "a push to a dead staff stream must be counted");
    }

    @Test
    void aBrokenGuestStreamIsCountedAsASendFailure() throws Exception {
        feed.publishActive(List.of(order("a", OrderStatus.PLACED, 3, 1)));
        openGuestStream(order("a", OrderStatus.PLACED, 3, 1));
        streams.lastEmitter.completeWithError(new IOException("client went away"));

        feed.publishActive(List.of(order("a", OrderStatus.PREPARING, 3, 1)));

        assertTrue(sendFailures("order") >= 1, "a push to a dead guest stream must be counted");
    }

    @Test
    void aBrokenStreamIsCountedWhenTheHeartbeatFindsIt() throws Exception {
        openStaffStream();
        streams.lastEmitter.completeWithError(new IOException("client went away"));

        hub.heartbeat();

        assertTrue(sendFailures("heartbeat") >= 1, "a ping to a dead stream must be counted");
    }
}
