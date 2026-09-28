package com.googlecoffee.adapter.gcp;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.googlecoffee.model.Session;
import com.googlecoffee.port.SessionStore;
import com.googlecoffee.service.Fs;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile("gcp")
public class FirestoreSessionStore implements SessionStore {

    private static final String COLLECTION = "sessions";
    private final Firestore db;

    public FirestoreSessionStore(Firestore db) {
        this.db = db;
    }

    @Override
    public void save(Session session) {
        Fs.await(db.collection(COLLECTION).document(session.id()).set(session.toMap()));
    }

    @Override
    public Optional<Session> find(String id) {
        DocumentSnapshot snap = Fs.await(db.collection(COLLECTION).document(id).get());
        if (!snap.exists() || snap.getData() == null) return Optional.empty();
        return Optional.of(Session.fromMap(snap.getData()));
    }
}
