package com.googlecoffee.service;

import com.googlecoffee.model.Feedback;
import com.googlecoffee.model.Session;
import com.googlecoffee.port.FeedbackStore;
import com.googlecoffee.port.OrderStore;
import com.googlecoffee.web.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FeedbackService {

    private final FeedbackStore store;
    private final SessionService sessions;
    private final OrderStore orders;

    public FeedbackService(FeedbackStore store, SessionService sessions, OrderStore orders) {
        this.store = store;
        this.sessions = sessions;
        this.orders = orders;
    }

    public Feedback submit(String sessionId, String orderId, int rating, String comment) {
        Session s = sessions.get(sessionId);
        // Feedback may be general (no order), but when it names an order that must be this guest's own.
        if (orderId != null && !orderId.isBlank()
                && orders.find(orderId).filter(o -> o.sessionId().equals(s.id())).isEmpty()) {
            throw ApiException.notFound("Order");
        }
        String clean = comment == null ? "" : comment.trim();
        Feedback f = new Feedback(UUID.randomUUID().toString(), s.id(), orderId, s.table(),
                rating, clean, System.currentTimeMillis());
        store.save(f);
        return f;
    }

    public List<Feedback> since(long sinceMillis) {
        return store.since(sinceMillis, 150);
    }
}
