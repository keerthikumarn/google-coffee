package com.googlecoffee.support;

import com.googlecoffee.model.Order;
import com.googlecoffee.port.OrderFeed;

import java.util.List;
import java.util.function.Consumer;

/** OrderFeed driven by the test: call {@link #publishActive} to simulate the database changing. */
public class ManualOrderFeed implements OrderFeed {

    private Consumer<List<Order>> onActive = orders -> {};

    @Override
    public void start(Consumer<List<Order>> onActive, Consumer<Order> onFinal) {
        this.onActive = onActive;
    }

    @Override
    public void stop() {}

    public void publishActive(List<Order> orders) {
        onActive.accept(orders);
    }
}
