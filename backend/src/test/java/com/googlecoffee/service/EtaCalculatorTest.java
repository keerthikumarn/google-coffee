package com.googlecoffee.service;

import com.googlecoffee.model.MenuItem;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderItem;
import com.googlecoffee.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EtaCalculatorTest {

    private static final Map<String, MenuItem> MENU = Map.of(
            "espresso", new MenuItem("espresso", "Espresso", "Espresso bar", "", 160, 2, List.of(), false),
            "toast", new MenuItem("toast", "Toast", "Bakery & bites", "", 320, 7, List.of(), false));

    private static Order order(String id, int prep, OrderStatus status, long createdAt) {
        return new Order(id, "100", "s", "Guest", "1", List.of(), 0, prep, status, createdAt, createdAt);
    }

    @Test
    void prepIsSlowestItemPlusOneMinutePerExtraItem() {
        List<OrderItem> items = List.of(
                new OrderItem("espresso", "Espresso", 2, 160, ""),
                new OrderItem("toast", "Toast", 1, 320, ""));
        assertEquals(7 + 2, EtaCalculator.orderPrepMinutes(items, MENU::get));
    }

    @Test
    void queueIsSharedAcrossBaristasInArrivalOrder() {
        List<Order> active = List.of(
                order("b", 4, OrderStatus.PLACED, 2),
                order("a", 6, OrderStatus.PREPARING, 1),
                order("c", 5, OrderStatus.READY, 0));
        Map<String, EtaCalculator.Eta> etas = EtaCalculator.compute(active, 2);
        assertEquals(new EtaCalculator.Eta(2, 1), etas.get("a")); // ceil(3/2)
        assertEquals(new EtaCalculator.Eta(4, 2), etas.get("b")); // ceil((3+4)/2)
        assertEquals(new EtaCalculator.Eta(0, 0), etas.get("c"));
    }

    @Test
    void newOrderEstimateNeverBelowOneMinute() {
        assertEquals(1, EtaCalculator.estimateNew(List.of(), 3, 1));
        assertEquals(5, EtaCalculator.estimateNew(List.of(order("x", 6, OrderStatus.PLACED, 1)), 2, 4));
    }

    @Test
    void zeroBaristasIsTreatedAsOne() {
        assertEquals(10, EtaCalculator.estimateNew(List.of(), 0, 10));
    }
}
