package com.googlecoffee.model;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PLACED, PREPARING, READY, COLLECTED, CANCELLED;

    public static final Set<OrderStatus> ACTIVE = EnumSet.of(PLACED, PREPARING, READY);

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PLACED -> next == PREPARING || next == CANCELLED;
            case PREPARING -> next == READY || next == CANCELLED;
            case READY -> next == COLLECTED;
            case COLLECTED, CANCELLED -> false;
        };
    }
}
