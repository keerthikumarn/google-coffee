package com.googlecoffee.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.googlecoffee.model.Session;
import com.googlecoffee.web.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Anonymous guest sessions, one per table visit (created from the table QR link). */
@Service
public class SessionService {

    static final String COLLECTION = "sessions";
    private final Firestore db;

    public SessionService(Firestore db) {
        this.db = db;
    }

    public Session create(String name, String table, List<String> preferences) {
        Session s = new Session(UUID.randomUUID().toString(), name.trim(), table.trim(),
                PreferenceRules.sanitize(preferences), System.currentTimeMillis());
        Fs.await(db.collection(COLLECTION).document(s.id()).set(s.toMap()));
        return s;
    }

    public Session get(String id) {
        if (id == null || id.isBlank()) throw ApiException.badRequest("sessionId is required");
        DocumentSnapshot snap = Fs.await(db.collection(COLLECTION).document(id).get());
        if (!snap.exists() || snap.getData() == null) throw ApiException.notFound("Session");
        return Session.fromMap(snap.getData());
    }

    public Session updatePreferences(String id, List<String> preferences) {
        Session current = get(id);
        Session updated = new Session(current.id(), current.name(), current.table(),
                PreferenceRules.sanitize(preferences), current.createdAt());
        Fs.await(db.collection(COLLECTION).document(id).set(updated.toMap()));
        return updated;
    }
}
