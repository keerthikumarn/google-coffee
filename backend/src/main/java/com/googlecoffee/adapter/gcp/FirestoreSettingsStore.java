package com.googlecoffee.adapter.gcp;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.ListenerRegistration;
import com.googlecoffee.model.MapUtil;
import com.googlecoffee.port.SettingsStore;
import com.googlecoffee.service.Fs;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.OptionalInt;
import java.util.function.IntConsumer;

/** Café settings in Firestore, kept in sync across Cloud Run instances with a snapshot listener. */
@Component
@Profile("gcp")
public class FirestoreSettingsStore implements SettingsStore {

    private static final Logger log = LoggerFactory.getLogger(FirestoreSettingsStore.class);
    private final DocumentReference doc;
    private ListenerRegistration registration;

    public FirestoreSettingsStore(Firestore db) {
        this.doc = db.collection("settings").document("cafe");
    }

    @Override
    public OptionalInt activeBaristas() {
        DocumentSnapshot snap = Fs.await(doc.get());
        if (!snap.exists() || snap.getData() == null) return OptionalInt.empty();
        int n = MapUtil.toInt(snap.getData().get("activeBaristas"));
        return n > 0 ? OptionalInt.of(n) : OptionalInt.empty();
    }

    @Override
    public void saveActiveBaristas(int value) {
        Fs.await(doc.set(Map.of("activeBaristas", value)));
    }

    @Override
    public void watch(IntConsumer onChange) {
        registration = doc.addSnapshotListener((snap, err) -> {
            if (err != null) {
                log.warn("Settings listener error: {}", err.getMessage());
                return;
            }
            if (snap != null && snap.exists() && snap.getData() != null) {
                int n = MapUtil.toInt(snap.getData().get("activeBaristas"));
                if (n > 0) onChange.accept(n);
            }
        });
    }

    @PreDestroy
    void stop() {
        if (registration != null) registration.remove();
    }
}
