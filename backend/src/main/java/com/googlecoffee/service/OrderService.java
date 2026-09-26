package com.googlecoffee.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.googlecoffee.live.LiveOrderHub;
import com.googlecoffee.live.OrderView;
import com.googlecoffee.model.MenuItem;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderItem;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.model.Session;
import com.googlecoffee.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    static final String COLLECTION = "orders";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Firestore db;
    private final MenuService menu;
    private final SessionService sessions;
    private final LiveOrderHub hub;
    private final SettingsService settings;

    public OrderService(Firestore db, MenuService menu, SessionService sessions,
                        LiveOrderHub hub, SettingsService settings) {
        this.db = db;
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
        String code = String.valueOf(100 + RANDOM.nextInt(900));
        Order order = new Order(UUID.randomUUID().toString(), code, s.id(), s.name(), s.table(),
                items, total, prep, OrderStatus.PLACED, now, now);
        Fs.await(db.collection(COLLECTION).document(order.id()).set(order.toMap()));
        return hub.viewOf(order);
    }

    public Order get(String orderId) {
        DocumentSnapshot snap = Fs.await(db.collection(COLLECTION).document(orderId).get());
        if (!snap.exists() || snap.getData() == null) throw ApiException.notFound("Order");
        return Order.fromMap(snap.getData());
    }

    public Order getForSession(String orderId, String sessionId) {
        Order o = get(orderId);
        if (!o.sessionId().equals(sessionId)) throw ApiException.notFound("Order");
        return o;
    }

    public List<Order> historyFor(String sessionId) {
        List<Order> out = new ArrayList<>();
        for (QueryDocumentSnapshot d : Fs.await(db.collection(COLLECTION)
                .whereEqualTo("sessionId", sessionId).limit(10).get()).getDocuments()) {
            out.add(Order.fromMap(d.getData()));
        }
        return out;
    }

    /** Status changes run in a transaction so two staff tablets can't make an illegal jump. */
    public Order updateStatus(String orderId, OrderStatus next) {
        DocumentReference ref = db.collection(COLLECTION).document(orderId);
        return Fs.await(db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref).get();
            if (!snap.exists() || snap.getData() == null) throw ApiException.notFound("Order");
            Order current = Order.fromMap(snap.getData());
            if (!current.status().canTransitionTo(next)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Order is " + current.status() + " and can't move to " + next);
            }
            Map<String, Object> patch = new HashMap<>();
            patch.put("status", next.name());
            patch.put("updatedAt", System.currentTimeMillis());
            tx.update(ref, patch);
            return new Order(current.id(), current.code(), current.sessionId(), current.customerName(),
                    current.table(), current.items(), current.total(), current.prepMinutes(),
                    next, current.createdAt(), System.currentTimeMillis());
        }));
    }
}
