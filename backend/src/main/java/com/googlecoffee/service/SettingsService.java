package com.googlecoffee.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.ListenerRegistration;
import com.googlecoffee.config.AppProperties;
import com.googlecoffee.model.MapUtil;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/** Café-wide settings (active baristas). Kept in sync across Cloud Run instances via a Firestore listener. */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final DocumentReference doc;
    private volatile int activeBaristas;
    private final CopyOnWriteArrayList<IntConsumer> listeners = new CopyOnWriteArrayList<>();
    private ListenerRegistration registration;

    public SettingsService(Firestore db, AppProperties props) {
        this.doc = db.collection("settings").document("cafe");
        this.activeBaristas = Math.max(1, props.defaultBaristas());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void listen() {
        registration = doc.addSnapshotListener((snap, err) -> {
            if (err != null) {
                log.warn("Settings listener error: {}", err.getMessage());
                return;
            }
            if (snap != null && snap.exists() && snap.getData() != null) {
                int n = MapUtil.toInt(snap.getData().get("activeBaristas"));
                if (n > 0 && n != activeBaristas) {
                    activeBaristas = n;
                    listeners.forEach(l -> l.accept(n));
                }
            }
        });
    }

    public int activeBaristas() {
        return activeBaristas;
    }

    public int setActiveBaristas(int n) {
        int value = Math.max(1, Math.min(8, n));
        Fs.await(doc.set(Map.of("activeBaristas", value)));
        activeBaristas = value;
        listeners.forEach(l -> l.accept(value));
        return value;
    }

    public void onChange(IntConsumer listener) {
        listeners.add(listener);
    }

    @PreDestroy
    void stop() {
        if (registration != null) registration.remove();
    }
}
