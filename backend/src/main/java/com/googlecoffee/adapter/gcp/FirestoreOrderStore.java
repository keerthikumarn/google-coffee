package com.googlecoffee.adapter.gcp;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.port.OrderStore;
import com.googlecoffee.service.Fs;
import com.googlecoffee.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("gcp")
public class FirestoreOrderStore implements OrderStore {

    static final String COLLECTION = "orders";
    private final Firestore db;

    public FirestoreOrderStore(Firestore db) {
        this.db = db;
    }

    @Override
    public void insert(Order order) {
        Fs.await(db.collection(COLLECTION).document(order.id()).set(order.toMap()));
    }

    @Override
    public Optional<Order> find(String id) {
        DocumentSnapshot snap = Fs.await(db.collection(COLLECTION).document(id).get());
        if (!snap.exists() || snap.getData() == null) return Optional.empty();
        return Optional.of(Order.fromMap(snap.getData()));
    }

    @Override
    public List<Order> findBySession(String sessionId, int limit) {
        List<Order> out = new ArrayList<>();
        for (QueryDocumentSnapshot d : Fs.await(db.collection(COLLECTION)
                .whereEqualTo("sessionId", sessionId).limit(limit).get()).getDocuments()) {
            out.add(Order.fromMap(d.getData()));
        }
        return out;
    }

    @Override
    public List<Order> findActive() {
        List<Object> statuses = new ArrayList<>(OrderStatus.ACTIVE.stream().map(Enum::name).toList());
        List<Order> out = new ArrayList<>();
        for (QueryDocumentSnapshot d : Fs.await(db.collection(COLLECTION)
                .whereIn("status", statuses).get()).getDocuments()) {
            out.add(Order.fromMap(d.getData()));
        }
        return out;
    }

    /** Runs in a Firestore transaction so two staff tablets can't make an illegal jump. */
    @Override
    public Order updateStatus(String id, OrderStatus next) {
        DocumentReference ref = db.collection(COLLECTION).document(id);
        return Fs.await(db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref).get();
            if (!snap.exists() || snap.getData() == null) throw ApiException.notFound("Order");
            Order current = Order.fromMap(snap.getData());
            if (!current.status().canTransitionTo(next)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Order is " + current.status() + " and can't move to " + next);
            }
            long now = System.currentTimeMillis();
            Map<String, Object> patch = new HashMap<>();
            patch.put("status", next.name());
            patch.put("updatedAt", now);
            tx.update(ref, patch);
            return new Order(current.id(), current.code(), current.sessionId(), current.customerName(),
                    current.table(), current.items(), current.total(), current.prepMinutes(),
                    next, current.createdAt(), now);
        }));
    }
}
