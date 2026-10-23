package com.googlecoffee.service;

import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CafeStatusTest {

    /** One barista and one open order of the given prep time: the wait is that prep plus the 4-minute coffee. */
    private static CafeStatus oneBaristaWithQueuedPrep(int prepMinutes) {
        return CafeStatus.of(List.of(Fixtures.order("a", "s", OrderStatus.PLACED, prepMinutes, 1)), 1);
    }

    @Test
    void emptyBarIsQuietWithTheBareCoffeeTime() {
        CafeStatus status = CafeStatus.of(List.of(), 2);

        assertEquals(new CafeStatus(2, 0, 2, "Quiet"), status);
    }

    @Test
    void quietUpToFiveMinutesThenSteady() {
        assertEquals("Quiet", oneBaristaWithQueuedPrep(1).busyLevel());    // 5 minutes
        assertEquals("Steady", oneBaristaWithQueuedPrep(2).busyLevel());   // 6 minutes
    }

    @Test
    void steadyUpToTwelveMinutesThenBusy() {
        assertEquals("Steady", oneBaristaWithQueuedPrep(8).busyLevel());   // 12 minutes
        assertEquals("Busy", oneBaristaWithQueuedPrep(9).busyLevel());     // 13 minutes
    }

    @Test
    void waitIsReportedInMinutes() {
        assertEquals(12, oneBaristaWithQueuedPrep(8).waitMinutesForCoffee());
    }

    @Test
    void moreBaristasShortenTheWaitAndTheLevel() {
        List<com.googlecoffee.model.Order> queue = List.of(
                Fixtures.order("a", "s", OrderStatus.PLACED, 10, 1),
                Fixtures.order("b", "s", OrderStatus.PLACED, 10, 2));

        assertEquals(new CafeStatus(1, 2, 24, "Busy"), CafeStatus.of(queue, 1));
        assertEquals(new CafeStatus(4, 2, 6, "Steady"), CafeStatus.of(queue, 4));
    }

    @Test
    void onlyOrdersWaitingOrBeingMadeCountAsQueued() {
        CafeStatus status = CafeStatus.of(List.of(
                Fixtures.order("a", "s", OrderStatus.PLACED, 3, 1),
                Fixtures.order("b", "s", OrderStatus.PREPARING, 3, 2),
                Fixtures.order("c", "s", OrderStatus.READY, 3, 3)), 1);

        assertEquals(2, status.ordersInQueue());
    }

    @Test
    void readyOrdersDoNotAddToTheWait() {
        CafeStatus withReady = CafeStatus.of(List.of(Fixtures.order("a", "s", OrderStatus.READY, 20, 1)), 1);

        assertEquals(CafeStatus.of(List.of(), 1).waitMinutesForCoffee(), withReady.waitMinutesForCoffee());
    }

    @Test
    void anOrderBeingMadeCountsForHalfItsPrepTime() {
        // 8-minute order already on the machine = 4 minutes left, plus the 4-minute coffee
        CafeStatus status = CafeStatus.of(List.of(Fixtures.order("a", "s", OrderStatus.PREPARING, 8, 1)), 1);

        assertEquals(8, status.waitMinutesForCoffee());
    }
}
