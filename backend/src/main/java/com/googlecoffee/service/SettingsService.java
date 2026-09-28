package com.googlecoffee.service;

import com.googlecoffee.config.AppProperties;
import com.googlecoffee.port.SettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/** Café-wide settings (active baristas), persisted in the configured store. */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsStore store;
    private volatile int activeBaristas;
    private final CopyOnWriteArrayList<IntConsumer> listeners = new CopyOnWriteArrayList<>();

    public SettingsService(SettingsStore store, AppProperties props) {
        this.store = store;
        this.activeBaristas = Math.max(1, props.defaultBaristas());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            store.activeBaristas().ifPresent(this::apply);
        } catch (Exception e) {
            log.warn("Could not load settings ({}); using default of {} baristas", e.getMessage(), activeBaristas);
        }
        store.watch(this::apply);
    }

    private void apply(int n) {
        if (n > 0 && n != activeBaristas) {
            activeBaristas = n;
            listeners.forEach(l -> l.accept(n));
        }
    }

    public int activeBaristas() {
        return activeBaristas;
    }

    public int setActiveBaristas(int n) {
        int value = Math.max(1, Math.min(8, n));
        store.saveActiveBaristas(value);
        activeBaristas = value;
        listeners.forEach(l -> l.accept(value));
        return value;
    }

    public void onChange(IntConsumer listener) {
        listeners.add(listener);
    }
}