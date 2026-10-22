package com.googlecoffee.support;

import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.port.OrderStore;
import com.googlecoffee.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory OrderStore that honours the same contract as the Postgres and Firestore adapters. */
public class InMemoryOrderStore implements OrderStore {

    private final Map<String, Order> orders = new LinkedHashMap<>();

    @Override
    public void insert(Order order) {
        orders.put(order.id(), order);
    }

    @Override
    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findBySession(String sessionId, int limit) {
        return orders.values().stream()
                .filter(o -> o.sessionId().equals(sessionId))
                .sorted(Comparator.comparingLong(Order::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<Order> findActive() {
        return new ArrayList<>(orders.values().stream()
                .filter(o -> OrderStatus.ACTIVE.contains(o.status()))
                .toList());
    }

    @Override
    public Order updateStatus(String id, OrderStatus next) {
        Order current = orders.get(id);
        if (current == null) throw ApiException.notFound("Order");
        if (!current.status().canTransitionTo(next)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Cannot move an order from " + current.status() + " to " + next);
        }
        Order updated = new Order(current.id(), current.code(), current.sessionId(), current.customerName(),
                current.table(), current.items(), current.total(), current.prepMinutes(), next,
                current.createdAt(), System.currentTimeMillis());
        orders.put(id, updated);
        return updated;
    }
}
