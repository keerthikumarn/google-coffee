package com.googlecoffee.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.googlecoffee.model.Feedback;
import com.googlecoffee.model.Session;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FeedbackService {

    static final String COLLECTION = "feedback";
    private final Firestore db;
    private final SessionService sessions;

    public FeedbackService(Firestore db, SessionService sessions) {
        this.db = db;
        this.sessions = sessions;
    }

    public Feedback submit(String sessionId, String orderId, int rating, String comment) {
        Session s = sessions.get(sessionId);
        String clean = comment == null ? "" : comment.trim();
        Feedback f = new Feedback(UUID.randomUUID().toString(), s.id(), orderId, s.table(),
                rating, clean, System.currentTimeMillis());
        Fs.await(db.collection(COLLECTION).document(f.id()).set(f.toMap()));
        return f;
    }

    public List<Feedback> since(long sinceMillis) {
        List<Feedback> out = new ArrayList<>();
        for (QueryDocumentSnapshot d : Fs.await(db.collection(COLLECTION)
                .whereGreaterThanOrEqualTo("createdAt", sinceMillis).limit(150).get()).getDocuments()) {
            out.add(Feedback.fromMap(d.getData()));
        }
        return out;
    }
}
