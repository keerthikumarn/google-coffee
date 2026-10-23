package com.googlecoffee.service;

import com.googlecoffee.live.LiveOrderHub;
import com.googlecoffee.live.OrderView;
import com.googlecoffee.model.MenuItem;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderItem;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.model.Session;
import com.googlecoffee.port.OrderStore;
import com.googlecoffee.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderStore store;
    private final MenuService menu;
    private final SessionService sessions;
    private final LiveOrderHub hub;
    private final SettingsService settings;

    public OrderService(OrderStore store, MenuService menu, SessionService sessions,
                        LiveOrderHub hub, SettingsService settings) {
        this.store = store;
        this.menu = menu;
        this.sessions = sessions;
        this.hub = hub;
        this.settings = settings;
    }

    public record LineRequest(String itemId, int qty, String note) {}

    /** Prices and prep times always come from the server-side menu, never from the client. */
    public List<OrderItem> resolveLines(List<LineRequest> lines) {
        if (lines == null || lines.isEmpty()) throw ApiException.badRequest("Your cart is empty");
        if (lines.size() > 15) throw ApiException.badRequest("Too many different items in one order");
        List<OrderItem> items = new ArrayList<>();
        for (LineRequest l : lines) {
            MenuItem m = menu.find(l.itemId());
            if (m == null) throw ApiException.badRequest("Unknown menu item: " + l.itemId());
            if (l.qty() < 1 || l.qty() > 10) throw ApiException.badRequest("Quantity must be between 1 and 10");
            String note = l.note() == null ? "" : l.note().trim();
            if (note.length() > 80) note = note.substring(0, 80);
            items.add(new OrderItem(m.id(), m.name(), l.qty(), m.price(), note));
        }
        return items;
    }

    public int estimateMinutes(List<LineRequest> lines) {
        List<OrderItem> items = resolveLines(lines);
        int prep = EtaCalculator.orderPrepMinutes(items, menu::find);
        return EtaCalculator.estimateNew(hub.activeOrders(), settings.activeBaristas(), prep);
    }

    public OrderView place(String sessionId, List<LineRequest> lines) {
        Session s = sessions.get(sessionId);
        List<OrderItem> items = resolveLines(lines);
        int total = items.stream().mapToInt(i -> i.unitPrice() * i.qty()).sum();
        int prep = EtaCalculator.orderPrepMinutes(items, menu::find);
        long now = System.currentTimeMillis();
        Set<String> inUse = hub.activeOrders().stream().map(Order::code).collect(Collectors.toSet());
        String code = pickupCode(inUse, RANDOM::nextInt);
        Order order = new Order(UUID.randomUUID().toString(), code, s.id(), s.name(), s.table(),
                items, total, prep, OrderStatus.PLACED, now, now);
        store.insert(order);
        return hub.viewOf(order);
    }

    /**
     * A three-digit code the barista can call out. Never one that an open order is already using, so two
     * guests are not called by the same number. Random first (the common case), then the first free
     * three-digit code, and only when all 900 are taken a four-digit one.
     */
    static String pickupCode(Set<String> inUse, IntUnaryOperator nextInt) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = String.valueOf(100 + nextInt.applyAsInt(900));
            if (!inUse.contains(code)) return code;
        }
        for (int n = 100; ; n++) {
            String code = String.valueOf(n);
            if (!inUse.contains(code)) return code;
        }
    }

    public Order get(String orderId) {
        return store.find(orderId).orElseThrow(() -> ApiException.notFound("Order"));
    }

    public Order getForSession(String orderId, String sessionId) {
        Order o = get(orderId);
        if (!o.sessionId().equals(sessionId)) throw ApiException.notFound("Order");
        return o;
    }

    /**
     * A guest may cancel their own order until the bar starts on it. Staff can still cancel later
     * through {@link #updateStatus}. A staff move landing between the check and the update can
     * let one cancellation through a moment after the bar started; the store has no "only if
     * still PLACED" operation to prevent that.
     */
    public Order cancelForSession(String orderId, String sessionId) {
        Order order = getForSession(orderId, sessionId);
        if (order.status() != OrderStatus.PLACED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This order is already " + order.status().name().toLowerCase()
                            + ", so it can't be cancelled here. Please ask a barista.");
        }
        return store.updateStatus(orderId, OrderStatus.CANCELLED);
    }

    public List<Order> historyFor(String sessionId) {
        return store.findBySession(sessionId, 10);
    }

    /** The store makes the transition atomic, so two staff tablets can't make an illegal jump. */
    public Order updateStatus(String orderId, OrderStatus next) {
        return store.updateStatus(orderId, next);
    }
}