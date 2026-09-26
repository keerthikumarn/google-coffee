package com.googlecoffee.service;

import com.googlecoffee.model.MenuItem;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderItem;
import com.googlecoffee.model.OrderStatus;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Transparent wait-time model (no ML, no historical data needed):
 *  - an order's prep time = slowest item + 1 minute per extra item
 *  - orders in the queue share the active baristas
 *  - an order already being prepared counts for half its prep time
 */
public final class EtaCalculator {

    private EtaCalculator() {}

    public record Eta(int minutes, int position) {}

    public static int orderPrepMinutes(List<OrderItem> items, Function<String, MenuItem> menu) {
        int slowest = 0;
        int totalQty = 0;
        for (OrderItem it : items) {
            MenuItem m = menu.apply(it.itemId());
            int prep = m == null ? 3 : m.prepMinutes();
            slowest = Math.max(slowest, prep);
            totalQty += it.qty();
        }
        return totalQty == 0 ? 0 : slowest + Math.max(0, totalQty - 1);
    }

    private static int effectivePrep(Order o) {
        return o.status() == OrderStatus.PREPARING
                ? (int) Math.ceil(o.prepMinutes() / 2.0)
                : o.prepMinutes();
    }

    /** ETA for every active order, in first-come-first-served order. */
    public static Map<String, Eta> compute(List<Order> active, int baristas) {
        int crew = Math.max(1, baristas);
        List<Order> sorted = active.stream()
                .sorted(Comparator.comparingLong(Order::createdAt))
                .toList();
        Map<String, Eta> out = new HashMap<>();
        int cumulative = 0;
        int position = 0;
        for (Order o : sorted) {
            if (o.status() == OrderStatus.READY) {
                out.put(o.id(), new Eta(0, 0));
                continue;
            }
            if (o.status() != OrderStatus.PLACED && o.status() != OrderStatus.PREPARING) continue;
            cumulative += effectivePrep(o);
            position++;
            out.put(o.id(), new Eta(Math.max(1, (int) Math.ceil((double) cumulative / crew)), position));
        }
        return out;
    }

    /** Estimate for a new order of the given prep time if placed now. */
    public static int estimateNew(List<Order> active, int baristas, int newPrepMinutes) {
        int crew = Math.max(1, baristas);
        int queued = active.stream()
                .filter(o -> o.status() == OrderStatus.PLACED || o.status() == OrderStatus.PREPARING)
                .mapToInt(EtaCalculator::effectivePrep)
                .sum();
        return Math.max(1, (int) Math.ceil((double) (queued + newPrepMinutes) / crew));
    }
}
