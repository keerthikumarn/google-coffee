package com.googlecoffee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.live.LiveOrderHub;
import com.googlecoffee.live.OrderView;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.model.Session;
import com.googlecoffee.service.OrderService.LineRequest;
import com.googlecoffee.support.Fixtures;
import com.googlecoffee.support.InMemoryMenuStore;
import com.googlecoffee.support.InMemoryOrderStore;
import com.googlecoffee.support.InMemorySessionStore;
import com.googlecoffee.support.InMemorySettingsStore;
import com.googlecoffee.support.ManualOrderFeed;
import com.googlecoffee.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceTest {

    private InMemoryOrderStore orders;
    private ManualOrderFeed feed;
    private SettingsService settings;
    private SessionService sessions;
    private OrderService service;
    private Session guest;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrderStore();
        feed = new ManualOrderFeed();
        settings = new SettingsService(new InMemorySettingsStore(), Fixtures.props(1));
        MenuService menu = new MenuService(new InMemoryMenuStore(Fixtures.espresso(), Fixtures.toast()), new ObjectMapper());
        menu.load();
        sessions = new SessionService(new InMemorySessionStore());
        LiveOrderHub hub = new LiveOrderHub(feed, settings);
        hub.start();
        service = new OrderService(orders, menu, sessions, hub, settings);
        guest = sessions.create("Asha", "7", List.of());
    }

    private static LineRequest line(String itemId, int qty) {
        return new LineRequest(itemId, qty, "");
    }

    private static void assertApiError(HttpStatus expected, Runnable action) {
        ApiException e = assertThrows(ApiException.class, action::run);
        assertEquals(expected, e.status());
    }

    // ---------- placing an order ----------

    @Test
    void placeUsesServerSidePricesAndTotals() {
        OrderView view = service.place(guest.id(), List.of(line("espresso", 2), line("toast", 1)));

        Order order = service.get(view.order().id());
        assertEquals(160 * 2 + 320, order.total());
        assertEquals("Espresso", order.items().get(0).name());
        assertEquals(160, order.items().get(0).unitPrice());
    }

    @Test
    void placeCopiesGuestDetailsAndStartsAsPlaced() {
        Order order = service.place(guest.id(), List.of(line("espresso", 1))).order();

        assertEquals(OrderStatus.PLACED, order.status());
        assertEquals("Asha", order.customerName());
        assertEquals("7", order.table());
        assertEquals(guest.id(), order.sessionId());
        assertTrue(order.code().matches("[1-9]\\d\\d"), "pickup code is three digits: " + order.code());
    }

    @Test
    void placeSetsPrepTimeFromSlowestItemPlusExtraItems() {
        Order order = service.place(guest.id(), List.of(line("espresso", 2), line("toast", 1))).order();

        assertEquals(7 + 2, order.prepMinutes());
    }

    @Test
    void placeReturnsViewWithQueuePositionAndEta() {
        OrderView view = service.place(guest.id(), List.of(line("espresso", 1)));

        assertEquals(1, view.queuePosition());
        assertEquals(2, view.etaMinutes());
    }

    @Test
    void placeRejectsUnknownSession() {
        assertApiError(HttpStatus.NOT_FOUND, () -> service.place("nope", List.of(line("espresso", 1))));
    }

    @Test
    void placeRejectsBlankSession() {
        assertApiError(HttpStatus.BAD_REQUEST, () -> service.place(" ", List.of(line("espresso", 1))));
    }

    // ---------- pickup codes ----------

    @Test
    void pickupCodeSkipsCodesUsedByOpenOrders() {
        int[] draws = {23, 24};   // "123" is taken, "124" is free
        int[] next = {0};
        IntUnaryOperator random = bound -> draws[next[0]++];

        assertEquals("124", OrderService.pickupCode(Set.of("123"), random));
    }

    @Test
    void pickupCodeFallsBackToTheFirstFreeCodeWhenRandomKeepsHittingTakenOnes() {
        Set<String> taken = Set.of("100", "101", "102");

        assertEquals("103", OrderService.pickupCode(taken, bound -> 0));
    }

    @Test
    void pickupCodeGrowsToFourDigitsOnlyWhenEveryThreeDigitCodeIsTaken() {
        Set<String> all = IntStream.rangeClosed(100, 999).mapToObj(String::valueOf).collect(java.util.stream.Collectors.toSet());

        assertEquals("1000", OrderService.pickupCode(all, bound -> 0));
    }

    @Test
    void placedOrderNeverReusesTheCodeOfAnOpenOrder() {
        List<Order> open = IntStream.rangeClosed(100, 999)
                .filter(code -> code != 555)
                .mapToObj(code -> new Order("o" + code, String.valueOf(code), "s", "Guest", "1", List.of(), 0, 3,
                        OrderStatus.PLACED, code, code))
                .toList();
        feed.publishActive(open);

        Order order = service.place(guest.id(), List.of(line("espresso", 1))).order();

        assertEquals("555", order.code());
    }

    // ---------- cart validation ----------

    @Test
    void rejectsEmptyOrMissingCart() {
        assertApiError(HttpStatus.BAD_REQUEST, () -> service.resolveLines(List.of()));
        assertApiError(HttpStatus.BAD_REQUEST, () -> service.resolveLines(null));
    }

    @Test
    void rejectsMoreThanFifteenDistinctLines() {
        List<LineRequest> lines = java.util.Collections.nCopies(16, line("espresso", 1));
        assertApiError(HttpStatus.BAD_REQUEST, () -> service.resolveLines(lines));
    }

    @Test
    void rejectsUnknownMenuItem() {
        assertApiError(HttpStatus.BAD_REQUEST, () -> service.resolveLines(List.of(line("unicorn-latte", 1))));
    }

    @Test
    void rejectsQuantityOutsideOneToTen() {
        assertApiError(HttpStatus.BAD_REQUEST, () -> service.resolveLines(List.of(line("espresso", 0))));
        assertApiError(HttpStatus.BAD_REQUEST, () -> service.resolveLines(List.of(line("espresso", 11))));
        assertEquals(10, service.resolveLines(List.of(line("espresso", 10))).get(0).qty());
    }

    @Test
    void notesAreTrimmedAndCappedAtEightyCharacters() {
        var items = service.resolveLines(List.of(
                new LineRequest("espresso", 1, "  extra hot  "),
                new LineRequest("toast", 1, "x".repeat(200)),
                new LineRequest("espresso", 1, null)));

        assertEquals("extra hot", items.get(0).note());
        assertEquals(80, items.get(1).note().length());
        assertEquals("", items.get(2).note());
    }

    // ---------- lookups ----------

    @Test
    void getUnknownOrderIsNotFound() {
        assertApiError(HttpStatus.NOT_FOUND, () -> service.get("missing"));
    }

    @Test
    void getForSessionHidesOtherGuestsOrders() {
        Order mine = service.place(guest.id(), List.of(line("espresso", 1))).order();
        Session other = sessions.create("Ravi", "2", List.of());

        assertEquals(mine.id(), service.getForSession(mine.id(), guest.id()).id());
        assertApiError(HttpStatus.NOT_FOUND, () -> service.getForSession(mine.id(), other.id()));
    }

    @Test
    void historyIsLimitedToTenOrdersOfTheSession() {
        for (int i = 0; i < 12; i++) service.place(guest.id(), List.of(line("espresso", 1)));
        Session other = sessions.create("Ravi", "2", List.of());
        service.place(other.id(), List.of(line("espresso", 1)));

        assertEquals(10, service.historyFor(guest.id()).size());
        assertEquals(1, service.historyFor(other.id()).size());
    }

    // ---------- status changes ----------

    @Test
    void updateStatusFollowsAllowedTransitions() {
        Order order = service.place(guest.id(), List.of(line("espresso", 1))).order();

        assertEquals(OrderStatus.PREPARING, service.updateStatus(order.id(), OrderStatus.PREPARING).status());
        assertEquals(OrderStatus.READY, service.updateStatus(order.id(), OrderStatus.READY).status());
    }

    @Test
    void updateStatusRejectsIllegalJumpWithConflict() {
        Order order = service.place(guest.id(), List.of(line("espresso", 1))).order();

        assertApiError(HttpStatus.CONFLICT, () -> service.updateStatus(order.id(), OrderStatus.READY));
        assertEquals(OrderStatus.PLACED, service.get(order.id()).status());
    }

    @Test
    void updateStatusOnMissingOrderIsNotFound() {
        assertApiError(HttpStatus.NOT_FOUND, () -> service.updateStatus("missing", OrderStatus.PREPARING));
    }

    // ---------- guest cancellation ----------

    @Test
    void guestCanCancelTheirOwnOrderWhileItIsStillPlaced() {
        Order order = service.place(guest.id(), List.of(line("espresso", 1))).order();

        Order cancelled = service.cancelForSession(order.id(), guest.id());

        assertEquals(OrderStatus.CANCELLED, cancelled.status());
        assertEquals(OrderStatus.CANCELLED, service.get(order.id()).status());
    }

    @Test
    void guestCannotCancelAnotherGuestsOrder() {
        Order order = service.place(guest.id(), List.of(line("espresso", 1))).order();
        Session other = sessions.create("Ravi", "2", List.of());

        assertApiError(HttpStatus.NOT_FOUND, () -> service.cancelForSession(order.id(), other.id()));
        assertEquals(OrderStatus.PLACED, service.get(order.id()).status());
    }

    @Test
    void cancellingAnUnknownOrderIsNotFound() {
        assertApiError(HttpStatus.NOT_FOUND, () -> service.cancelForSession("missing", guest.id()));
    }

    @Test
    void guestCannotCancelOnceTheBarHasStarted() {
        for (OrderStatus status : List.of(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COLLECTED)) {
            Order order = service.place(guest.id(), List.of(line("espresso", 1))).order();
            orders.updateStatus(order.id(), OrderStatus.PREPARING);
            if (status != OrderStatus.PREPARING) orders.updateStatus(order.id(), OrderStatus.READY);
            if (status == OrderStatus.COLLECTED) orders.updateStatus(order.id(), OrderStatus.COLLECTED);

            assertApiError(HttpStatus.CONFLICT, () -> service.cancelForSession(order.id(), guest.id()));
            assertEquals(status, service.get(order.id()).status());
        }
    }

    @Test
    void cancellingTwiceIsAConflictNotASilentSuccess() {
        Order order = service.place(guest.id(), List.of(line("espresso", 1))).order();
        service.cancelForSession(order.id(), guest.id());

        assertApiError(HttpStatus.CONFLICT, () -> service.cancelForSession(order.id(), guest.id()));
    }

    // ---------- estimates ----------

    @Test
    void estimateWithEmptyQueueIsJustThePrepTime() {
        assertEquals(2, service.estimateMinutes(List.of(line("espresso", 1))));
    }

    @Test
    void estimateAddsTheQueueAheadAndSharesItAcrossBaristas() {
        feed.publishActive(List.of(Fixtures.order("a", "s", OrderStatus.PLACED, 6, 1)));
        assertEquals(6 + 2, service.estimateMinutes(List.of(line("espresso", 1))));

        settings.setActiveBaristas(2);
        assertEquals((6 + 2) / 2, service.estimateMinutes(List.of(line("espresso", 1))));
    }

    @Test
    void estimateRejectsAnInvalidCart() {
        assertApiError(HttpStatus.BAD_REQUEST, () -> service.estimateMinutes(List.of()));
    }
}
