package com.googlecoffee.adapter.gcp;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.googlecoffee.model.Feedback;
import com.googlecoffee.port.FeedbackStore;
import com.googlecoffee.service.Fs;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("gcp")
public class FirestoreFeedbackStore implements FeedbackStore {

    private static final String COLLECTION = "feedback";
    private final Firestore db;

    public FirestoreFeedbackStore(Firestore db) {
        this.db = db;
    }

    @Override
    public void save(Feedback feedback) {
        Fs.await(db.collection(COLLECTION).document(feedback.id()).set(feedback.toMap()));
    }

    @Override
    public List<Feedback> since(long sinceMillis, int limit) {
        List<Feedback> out = new ArrayList<>();
        for (QueryDocumentSnapshot d : Fs.await(db.collection(COLLECTION)
                .whereGreaterThanOrEqualTo("createdAt", sinceMillis).limit(limit).get()).getDocuments()) {
            out.add(Feedback.fromMap(d.getData()));
        }
        return out;
    }
}
