package com.googlecoffee.service;

import com.googlecoffee.model.Feedback;
import com.googlecoffee.model.Session;
import com.googlecoffee.port.FeedbackStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FeedbackService {

    private final FeedbackStore store;
    private final SessionService sessions;

    public FeedbackService(FeedbackStore store, SessionService sessions) {
        this.store = store;
        this.sessions = sessions;
    }

    public Feedback submit(String sessionId, String orderId, int rating, String comment) {
        Session s = sessions.get(sessionId);
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