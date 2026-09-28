package com.googlecoffee.adapter.local;

import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.port.OrderFeed;
import com.googlecoffee.port.OrderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Live order feed for a single local instance: every write in PgOrderStore publishes an
 * OrderChangedEvent, and we re-read the (small) active set from Postgres.
 * To run several app instances, replace this with Postgres LISTEN/NOTIFY.
 */
@Component
@Profile("local")
public class LocalOrderFeed implements OrderFeed {

    private static final Logger log = LoggerFactory.getLogger(LocalOrderFeed.class);

    private final OrderStore store;
    private volatile Consumer<List<Order>> onActive;
    private volatile Consumer<Order> onFinal;

    public LocalOrderFeed(OrderStore store) {
        this.store = store;
    }

    @Override
    public void start(Consumer<List<Order>> onActive, Consumer<Order> onFinal) {
        this.onActive = onActive;
        this.onFinal = onFinal;
        try {
            onActive.accept(store.findActive());
        } catch (Exception e) {
            log.error("Could not load active orders from Postgres: {}", e.getMessage());
        }
        log.info("Listening for live order changes (in-process events)");
    }

    @EventListener
    public void onOrderChanged(OrderChangedEvent event) {
        if (onActive == null) return;
        try {
            onActive.accept(store.findActive());
            if (!OrderStatus.ACTIVE.contains(event.order().status())) onFinal.accept(event.order());
        } catch (Exception e) {
            // The write already succeeded; never fail the request because a live push failed.
            log.warn("Live update failed for order {}: {}", event.order().id(), e.getMessage());
        }
    }

    @Override
    public void stop() {
        onActive = null;
        onFinal = null;
    }
}
