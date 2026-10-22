package com.googlecoffee.support;

import com.googlecoffee.config.AppProperties;
import com.googlecoffee.model.MenuItem;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;

import java.util.List;

public final class Fixtures {

    private Fixtures() {}

    public static MenuItem espresso() {
        return new MenuItem("espresso", "Espresso", "Espresso bar", "Double shot", 160, 2,
                List.of("hot", "vegan"), false);
    }

    public static MenuItem toast() {
        return new MenuItem("toast", "Toast", "Bakery & bites", "Buttered sourdough", 320, 7,
                List.of("hot"), false);
    }

    public static AppProperties props(int defaultBaristas) {
        return new AppProperties("test-project", "(default)", "model", "location", 10,
                "0000", "secret", defaultBaristas, null);
    }

    public static Order order(String id, String sessionId, OrderStatus status, int prepMinutes, long createdAt) {
        return new Order(id, "123", sessionId, "Guest", "1", List.of(), 0, prepMinutes, status, createdAt, createdAt);
    }
}
