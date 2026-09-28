package com.googlecoffee.adapter.gcp;

import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.port.OrderFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * One Firestore snapshot listener on active orders. Every Cloud Run instance runs
 * its own listener, so live updates work across instances with no extra infra.
 */
@Component
@Profile("gcp")
public class FirestoreOrderFeed implements OrderFeed {

    private static final Logger log = LoggerFactory.getLogger(FirestoreOrderFeed.class);

    private final Firestore db;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private ListenerRegistration registration;

    public FirestoreOrderFeed(Firestore db) {
        this.db = db;
    }

    @Override
    public void start(Consumer<List<Order>> onActive, Consumer<Order> onFinal) {
        List<Object> statuses = new ArrayList<>(OrderStatus.ACTIVE.stream().map(Enum::name).toList());
        registration = db.collection(FirestoreOrderStore.COLLECTION)
                .whereIn("status", statuses)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        log.error("Order listener error: {}", err.getMessage());
                        return;
                    }
                    if (snap == null) return;
                    List<Order> active = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap.getDocuments()) active.add(Order.fromMap(d.getData()));

                    for (DocumentChange change : snap.getDocumentChanges()) {
                        if (change.getType() != DocumentChange.Type.REMOVED) continue;
                        String id = change.getDocument().getId();
                        // Order left the active set: fetch its final state for the guest.
                        executor.submit(() -> {
                            try {
                                DocumentSnapshot s = db.collection(FirestoreOrderStore.COLLECTION).document(id).get().get();
                                if (s.exists() && s.getData() != null) onFinal.accept(Order.fromMap(s.getData()));
                            } catch (Exception e) {
                                log.warn("Could not load final state of order {}: {}", id, e.getMessage());
                            }
                        });
                    }
                    onActive.accept(active);
                });
        log.info("Listening for live order changes (Firestore)");
    }

    @Override
    public void stop() {
        if (registration != null) registration.remove();
        executor.shutdown();
    }
}
