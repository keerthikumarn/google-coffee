package com.googlecoffee.port;

import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
 
import java.util.List;
import java.util.Optional;
 
public interface OrderStore {
    void insert(Order order);
 
    Optional<Order> find(String id);
 
    List<Order> findBySession(String sessionId, int limit);
 
    List<Order> findActive();
 
    /**
     * Atomically moves an order to the next status if the transition is allowed.
     * Throws ApiException 404 if missing, 409 if the transition is not allowed.
     */
    Order updateStatus(String id, OrderStatus next);
}
