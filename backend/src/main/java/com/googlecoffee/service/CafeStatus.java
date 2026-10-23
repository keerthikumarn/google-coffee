package com.googlecoffee.service;

import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;

import java.util.List;

/** How busy the bar is right now, as shown to guests before they order. */
public record CafeStatus(int activeBaristas, int ordersInQueue, int waitMinutesForCoffee, String busyLevel) {

    /** Prep time of a typical single coffee, used to quote the wait for "a coffee if you order now". */
    static final int STANDARD_COFFEE_MINUTES = 4;
    /** Waits up to this many minutes read as "Quiet". */
    static final int QUIET_UP_TO_MINUTES = 5;
    /** Waits up to this many minutes read as "Steady"; anything longer is "Busy". */
    static final int STEADY_UP_TO_MINUTES = 12;

    public static CafeStatus of(List<Order> active, int baristas) {
        int inQueue = (int) active.stream()
                .filter(o -> o.status() == OrderStatus.PLACED || o.status() == OrderStatus.PREPARING)
                .count();
        int wait = EtaCalculator.estimateNew(active, baristas, STANDARD_COFFEE_MINUTES);
        String level = wait <= QUIET_UP_TO_MINUTES ? "Quiet" : wait <= STEADY_UP_TO_MINUTES ? "Steady" : "Busy";
        return new CafeStatus(baristas, inQueue, wait, level);
    }
}
