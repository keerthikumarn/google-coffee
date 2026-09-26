package com.googlecoffee.live;

import java.util.List;

public record StaffBoard(List<OrderView> orders, int activeBaristas, long serverTime) {}
