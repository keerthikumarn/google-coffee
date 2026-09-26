package com.googlecoffee.live;

import com.googlecoffee.model.Order;

/** An order plus its live wait estimate, as sent to customers and staff. */
public record OrderView(Order order, int etaMinutes, int queuePosition) {}
