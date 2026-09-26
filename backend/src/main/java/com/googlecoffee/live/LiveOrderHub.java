package com.googlecoffee.live;

import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.service.EtaCalculator;
import com.googlecoffee.service.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single source of real-time truth: one Firestore snapshot listener on active
 * orders, fanned out to browsers over Server-Sent Events. Every Cloud Run
 * instance runs its own listener, so it scales horizontally with no extra infra.
 */
@Component
public class LiveOrderHub {

    private static final Logger log = LoggerFactory.getLogger(LiveOrderHub.class);
    private static final String COLLECTION = "orders";

    private final Firestore db;
    private final SettingsService settings;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile Map<String, Order> active = Map.of();
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> customers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> staff = new CopyOnWriteArrayList<>();
    private ListenerRegistration registration;

    public LiveOrderHub(Firestore db, SettingsService settings) {
        this.db = db;
        this.settings = settings;
        settings.onChange(n -> broadcastAll());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        List<String> statuses = OrderStatus.ACTIVE.stream().map(Enum::name).toList();
        registration = db.collection(COLLECTION)
                .whereIn("status", new ArrayList<Object>(statuses))
                .addSnapshotListener(this::onSnapshot);
        log.info("Listening for live order changes");
    }

    private void onSnapshot(QuerySnapshot snap, FirestoreException err) {
        if (err != null) {
            log.error("Order listener error: {}", err.getMessage());
            return;
        }
        if (snap == null) return;
        Map<String, Order> fresh = new HashMap<>();
        for (QueryDocumentSnapshot d : snap.getDocuments()) {
            fresh.put(d.getId(), Order.fromMap(d.getData()));
        }
        active = fresh;

        for (DocumentChange change : snap.getDocumentChanges()) {
            String id = change.getDocument().getId();
            if (change.getType() == DocumentChange.Type.REMOVED) {
                // Order left the active set (collected / cancelled): fetch its final state for the guest.
                executor.submit(() -> {
                    try {
                        DocumentSnapshot s = db.collection(COLLECTION).document(id).get().get();
                        if (s.exists() && s.getData() != null) sendToCustomers(viewOf(Order.fromMap(s.getData())));
                    } catch (Exception e) {
                        log.warn("Could not load final state of order {}: {}", id, e.getMessage());
                    }
                });
            }
        }
        broadcastAll();
    }

    // ---------- views ----------

    public List<Order> activeOrders() {
        return new ArrayList<>(active.values());
    }

    public OrderView viewOf(Order order) {
        if (!OrderStatus.ACTIVE.contains(order.status())) return new OrderView(order, 0, 0);
        List<Order> list = activeOrders();
        list.removeIf(o -> o.id().equals(order.id()));
        list.add(order);
        EtaCalculator.Eta eta = EtaCalculator.compute(list, settings.activeBaristas()).get(order.id());
        return eta == null ? new OrderView(order, 0, 0) : new OrderView(order, eta.minutes(), eta.position());
    }

    public StaffBoard board() {
        List<Order> list = activeOrders();
        Map<String, EtaCalculator.Eta> etas = EtaCalculator.compute(list, settings.activeBaristas());
        List<OrderView> views = list.stream()
                .sorted(Comparator.comparingLong(Order::createdAt))
                .map(o -> {
                    EtaCalculator.Eta e = etas.get(o.id());
                    return new OrderView(o, e == null ? 0 : e.minutes(), e == null ? 0 : e.position());
                })
                .toList();
        return new StaffBoard(views, settings.activeBaristas(), System.currentTimeMillis());
    }

    // ---------- subscriptions ----------

    public SseEmitter subscribeCustomer(Order order) {
        SseEmitter emitter = register(customers.computeIfAbsent(order.id(), k -> new CopyOnWriteArrayList<>()));
        send(emitter, "order", viewOf(active.getOrDefault(order.id(), order)));
        return emitter;
    }

    public SseEmitter subscribeStaff() {
        SseEmitter emitter = register(staff);
        send(emitter, "board", board());
        return emitter;
    }

    private SseEmitter register(List<SseEmitter> bucket) {
        SseEmitter emitter = new SseEmitter(0L);
        bucket.add(emitter);
        Runnable remove = () -> bucket.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    // ---------- fan-out ----------

    private void broadcastAll() {
        for (String orderId : customers.keySet()) {
            Order o = active.get(orderId);
            if (o != null) sendToCustomers(viewOf(o));
        }
        StaffBoard b = board();
        staff.forEach(e -> send(e, "board", b));
    }

    private void sendToCustomers(OrderView view) {
        List<SseEmitter> list = customers.get(view.order().id());
        if (list == null) return;
        list.forEach(e -> send(e, "order", view));
        if (!OrderStatus.ACTIVE.contains(view.order().status())) {
            // Terminal state delivered; close the streams.
            list.forEach(SseEmitter::complete);
            customers.remove(view.order().id());
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        List<SseEmitter> all = new ArrayList<>(staff);
        customers.values().forEach(all::addAll);
        for (SseEmitter e : all) {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                e.completeWithError(ex);
            }
        }
    }

    @PreDestroy
    void stop() {
        if (registration != null) registration.remove();
        executor.shutdown();
    }
}
