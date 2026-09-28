package com.googlecoffee.adapter.local;

import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.port.OrderStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalOrderFeedTest {

    private static Order order(String id, OrderStatus status) {
        return new Order(id, "100", "s", "Guest", "1", List.of(), 0, 3, status, 1, 1);
    }

    /** In-memory stand-in for Postgres. */
    private static final class FakeStore implements OrderStore {
        final List<Order> active = new ArrayList<>();
        public void insert(Order o) { active.add(o); }
        public Optional<Order> find(String id) { return active.stream().filter(o -> o.id().equals(id)).findFirst(); }
        public List<Order> findBySession(String s, int l) { return List.of(); }
        public List<Order> findActive() { return List.copyOf(active); }
        public Order updateStatus(String id, OrderStatus next) { throw new UnsupportedOperationException(); }
    }

    @Test
    void pushesActiveSetOnStartAndOnEveryChange() {
        FakeStore store = new FakeStore();
        store.insert(order("a", OrderStatus.PLACED));
        LocalOrderFeed feed = new LocalOrderFeed(store);
        List<List<Order>> pushes = new ArrayList<>();
        List<Order> finals = new ArrayList<>();

        feed.start(pushes::add, finals::add);
        assertEquals(1, pushes.size());
        assertEquals(1, pushes.get(0).size());

        store.insert(order("b", OrderStatus.PLACED));
        feed.onOrderChanged(new OrderChangedEvent(order("b", OrderStatus.PLACED)));
        assertEquals(2, pushes.get(1).size());
        assertTrue(finals.isEmpty());
    }

    @Test
    void reportsOrdersThatLeaveTheActiveSet() {
        FakeStore store = new FakeStore();
        LocalOrderFeed feed = new LocalOrderFeed(store);
        List<Order> finals = new ArrayList<>();
        feed.start(list -> {}, finals::add);

        feed.onOrderChanged(new OrderChangedEvent(order("c", OrderStatus.COLLECTED)));
        assertEquals(1, finals.size());
        assertEquals("c", finals.get(0).id());
    }
}