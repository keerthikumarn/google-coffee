package com.googlecoffee.adapter.local;

import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderItem;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgOrderStoreTest extends PostgresTestBase {

    private final List<Object> events = Collections.synchronizedList(new ArrayList<>());
    private final PgOrderStore store = new PgOrderStore(jdbc, JSON, events::add);

    @BeforeEach
    void createSessions() {
        for (String id : List.of("s1", "s2")) {
            jdbc.sql("INSERT INTO sessions (id, name, table_no, created_at) VALUES (:id, 'Guest', '1', 0)")
                    .param("id", id).update();
        }
    }

    private static Order order(String id, String sessionId, OrderStatus status, long createdAt) {
        List<OrderItem> items = List.of(
                new OrderItem("espresso", "Espresso", 2, 160, "extra hot"),
                new OrderItem("toast", "Toast", 1, 320, ""));
        return new Order(id, "123", sessionId, "Asha", "7", items, 640, 9, status, createdAt, createdAt);
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        assertEquals(expected, assertThrows(ApiException.class, action::run).status());
    }

    // ---------- insert / find ----------

    @Test
    void insertedOrderRoundTripsWithItsLineItems() {
        Order o = order("o1", "s1", OrderStatus.PLACED, 1_000);

        store.insert(o);

        assertEquals(o, store.find("o1").orElseThrow());
    }

    @Test
    void unknownOrderIsEmpty() {
        assertTrue(store.find("missing").isEmpty());
    }

    @Test
    void insertPublishesAChangeEvent() {
        Order o = order("o1", "s1", OrderStatus.PLACED, 1_000);

        store.insert(o);

        assertEquals(List.of(new OrderChangedEvent(o)), events);
    }

    @Test
    void orderForAnUnknownSessionIsRejectedByTheDatabase() {
        assertThrows(DataIntegrityViolationException.class,
                () -> store.insert(order("o1", "no-such-session", OrderStatus.PLACED, 1_000)));
        assertTrue(events.isEmpty());
    }

    // ---------- queries ----------

    @Test
    void findBySessionIsNewestFirstLimitedAndScopedToTheSession() {
        store.insert(order("old", "s1", OrderStatus.COLLECTED, 1_000));
        store.insert(order("mid", "s1", OrderStatus.READY, 2_000));
        store.insert(order("new", "s1", OrderStatus.PLACED, 3_000));
        store.insert(order("other", "s2", OrderStatus.PLACED, 4_000));

        assertEquals(List.of("new", "mid", "old"), store.findBySession("s1", 10).stream().map(Order::id).toList());
        assertEquals(List.of("new", "mid"), store.findBySession("s1", 2).stream().map(Order::id).toList());
    }

    @Test
    void findActiveReturnsOnlyOpenOrdersOldestFirst() {
        store.insert(order("ready", "s1", OrderStatus.READY, 3_000));
        store.insert(order("collected", "s1", OrderStatus.COLLECTED, 1_000));
        store.insert(order("placed", "s1", OrderStatus.PLACED, 2_000));
        store.insert(order("cancelled", "s2", OrderStatus.CANCELLED, 500));
        store.insert(order("preparing", "s2", OrderStatus.PREPARING, 1_500));

        assertEquals(List.of("preparing", "placed", "ready"),
                store.findActive().stream().map(Order::id).toList());
    }

    // ---------- status transitions ----------

    @Test
    void allowedTransitionIsPersistedAndAnnounced() {
        store.insert(order("o1", "s1", OrderStatus.PLACED, 1_000));
        events.clear();

        Order updated = store.updateStatus("o1", OrderStatus.PREPARING);

        assertEquals(OrderStatus.PREPARING, updated.status());
        assertTrue(updated.updatedAt() > 1_000);
        assertEquals(updated, store.find("o1").orElseThrow());
        assertEquals(List.of(new OrderChangedEvent(updated)), events);
    }

    @Test
    void orderWalksThroughTheWholeHappyPath() {
        store.insert(order("o1", "s1", OrderStatus.PLACED, 1_000));

        store.updateStatus("o1", OrderStatus.PREPARING);
        store.updateStatus("o1", OrderStatus.READY);

        assertEquals(OrderStatus.COLLECTED, store.updateStatus("o1", OrderStatus.COLLECTED).status());
    }

    @Test
    void activeOrderCanBeCancelledButReadyOrderCannot() {
        store.insert(order("preparing", "s1", OrderStatus.PREPARING, 1_000));
        store.insert(order("ready", "s1", OrderStatus.READY, 2_000));

        assertEquals(OrderStatus.CANCELLED, store.updateStatus("preparing", OrderStatus.CANCELLED).status());
        assertStatus(HttpStatus.CONFLICT, () -> store.updateStatus("ready", OrderStatus.CANCELLED));
    }

    @Test
    void illegalTransitionIsAConflictAndChangesNothing() {
        store.insert(order("o1", "s1", OrderStatus.PLACED, 1_000));
        events.clear();

        assertStatus(HttpStatus.CONFLICT, () -> store.updateStatus("o1", OrderStatus.READY));

        assertEquals(OrderStatus.PLACED, store.find("o1").orElseThrow().status());
        assertTrue(events.isEmpty());
    }

    @Test
    void finishedOrdersCannotBeReopened() {
        store.insert(order("done", "s1", OrderStatus.COLLECTED, 1_000));
        store.insert(order("gone", "s1", OrderStatus.CANCELLED, 2_000));

        assertStatus(HttpStatus.CONFLICT, () -> store.updateStatus("done", OrderStatus.PLACED));
        assertStatus(HttpStatus.CONFLICT, () -> store.updateStatus("gone", OrderStatus.PREPARING));
    }

    @Test
    void updatingAMissingOrderIsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> store.updateStatus("missing", OrderStatus.PREPARING));
    }

    @Test
    void concurrentAttemptsToMakeTheSameMoveSucceedExactlyOnce() throws Exception {
        store.insert(order("o1", "s1", OrderStatus.PLACED, 1_000));
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                tasks.add(() -> {
                    try {
                        store.updateStatus("o1", OrderStatus.PREPARING);
                        return true;
                    } catch (ApiException e) {
                        assertEquals(HttpStatus.CONFLICT, e.status());
                        return false;
                    }
                });
            }
            long wins = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) if (f.get()) wins++;

            assertEquals(1, wins);
            assertEquals(OrderStatus.PREPARING, store.find("o1").orElseThrow().status());
        } finally {
            pool.shutdownNow();
        }
    }
}
