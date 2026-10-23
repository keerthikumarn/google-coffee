package com.googlecoffee.support;

import com.googlecoffee.model.Order;
import com.googlecoffee.port.OrderFeed;

import java.util.List;
import java.util.function.Consumer;

/** OrderFeed driven by the test: call {@link #publishActive} to simulate the database changing. */
public class ManualOrderFeed implements OrderFeed {

    private Consumer<List<Order>> onActive = orders -> {};
    private Consumer<Order> onFinal = order -> {};
    private boolean stopped;

    @Override
    public void start(Consumer<List<Order>> onActive, Consumer<Order> onFinal) {
        this.onActive = onActive;
        this.onFinal = onFinal;
    }

    @Override
    public void stop() {
        stopped = true;
    }

    public boolean stopped() {
        return stopped;
    }

    public void publishActive(List<Order> orders) {
        onActive.accept(orders);
    }

    /** An order just left the active set (collected or cancelled). */
    public void publishFinal(Order order) {
        onFinal.accept(order);
    }
}
