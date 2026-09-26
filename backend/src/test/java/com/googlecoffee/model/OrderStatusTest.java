package com.googlecoffee.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {
    @Test
    void followsTheBarFlow() {
        assertTrue(OrderStatus.PLACED.canTransitionTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY));
        assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.COLLECTED));
        assertFalse(OrderStatus.PLACED.canTransitionTo(OrderStatus.READY));
        assertFalse(OrderStatus.COLLECTED.canTransitionTo(OrderStatus.PLACED));
    }
}
