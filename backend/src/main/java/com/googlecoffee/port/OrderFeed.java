package com.googlecoffee.port;

import com.googlecoffee.model.Order;
 
import java.util.List;
import java.util.function.Consumer;
 
/**
 * Source of real-time order changes for the live hub.
 * gcp: Firestore snapshot listener. local: events published after each database write.
 */
public interface OrderFeed {
 
    /**
     * @param onActive called with the full set of active orders whenever it changes
     * @param onFinal  called with an order that just left the active set (collected / cancelled)
     */
    void start(Consumer<List<Order>> onActive, Consumer<Order> onFinal);
 
    void stop();
}
