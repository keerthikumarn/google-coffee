package com.googlecoffee.service;

import com.googlecoffee.model.Feedback;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.model.Session;
import com.googlecoffee.support.Fixtures;
import com.googlecoffee.support.InMemoryFeedbackStore;
import com.googlecoffee.support.InMemoryOrderStore;
import com.googlecoffee.support.InMemorySessionStore;
import com.googlecoffee.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackServiceTest {

    private InMemoryFeedbackStore store;
    private InMemoryOrderStore orders;
    private SessionService sessions;
    private FeedbackService service;
    private Session asha;
    private Session ravi;

    @BeforeEach
    void setUp() {
        store = new InMemoryFeedbackStore();
        orders = new InMemoryOrderStore();
        sessions = new SessionService(new InMemorySessionStore());
        service = new FeedbackService(store, sessions, orders);
        asha = sessions.create("Asha", "7", List.of());
        ravi = sessions.create("Ravi", "2", List.of());
        orders.insert(Fixtures.order("asha-order", asha.id(), OrderStatus.COLLECTED, 3, 1));
    }

    private static void assertNotFound(Runnable action) {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, action::run).status());
    }

    @Test
    void submitStoresRatingTrimmedCommentAndTheGuestsTable() {
        Feedback f = service.submit(asha.id(), "asha-order", 4, "  lovely flat white ");

        assertEquals(4, f.rating());
        assertEquals("lovely flat white", f.comment());
        assertEquals("7", f.table());
        assertEquals(asha.id(), f.sessionId());
        assertEquals(List.of(f), store.all());
    }

    @Test
    void missingCommentBecomesEmpty() {
        assertEquals("", service.submit(asha.id(), "asha-order", 5, null).comment());
    }

    @Test
    void feedbackWithoutAnOrderIsAllowed() {
        assertNull(service.submit(asha.id(), null, 3, "").orderId());
        service.submit(asha.id(), "  ", 3, "");

        assertEquals(2, store.all().size());
    }

    @Test
    void feedbackCannotNameAnotherGuestsOrder() {
        assertNotFound(() -> service.submit(ravi.id(), "asha-order", 1, "not mine"));

        assertTrue(store.all().isEmpty());
    }

    @Test
    void feedbackCannotNameAnOrderThatDoesNotExist() {
        assertNotFound(() -> service.submit(asha.id(), "no-such-order", 5, ""));

        assertTrue(store.all().isEmpty());
    }

    @Test
    void feedbackFromAnUnknownSessionIsRejected() {
        assertNotFound(() -> service.submit("ghost", null, 5, ""));
    }

    @Test
    void sinceReturnsAtMostOneHundredFiftyEntriesNewestFirst() {
        saveGeneralFeedback(160);

        List<Feedback> recent = service.since(0);

        assertEquals(150, recent.size());
        assertEquals("f159", recent.get(0).id());
    }

    @Test
    void sinceLeavesOutFeedbackOlderThanTheWindow() {
        saveGeneralFeedback(160);

        List<Feedback> recent = service.since(1_150L);

        assertEquals(10, recent.size());
        assertTrue(recent.stream().allMatch(f -> f.createdAt() >= 1_150L));
    }

    private void saveGeneralFeedback(int count) {
        for (int i = 0; i < count; i++) {
            store.save(new Feedback("f" + i, asha.id(), null, "7", 3, "", 1_000L + i));
        }
    }
}
