package com.googlecoffee.service;

import com.googlecoffee.model.Session;
import com.googlecoffee.port.SessionStore;
import com.googlecoffee.web.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Anonymous guest sessions, one per table visit (created from the table QR link). */
@Service
public class SessionService {

    private final SessionStore store;

    public SessionService(SessionStore store) {
        this.store = store;
    }

    public Session create(String name, String table, List<String> preferences) {
        Session s = new Session(UUID.randomUUID().toString(), name.trim(), table.trim(),
                PreferenceRules.sanitize(preferences), System.currentTimeMillis());
        store.save(s);
        return s;
    }

    public Session get(String id) {
        if (id == null || id.isBlank()) throw ApiException.badRequest("sessionId is required");
        return store.find(id).orElseThrow(() -> ApiException.notFound("Session"));
    }

    public Session updatePreferences(String id, List<String> preferences) {
        Session current = get(id);
        Session updated = new Session(current.id(), current.name(), current.table(),
                PreferenceRules.sanitize(preferences), current.createdAt());
        store.save(updated);
        return updated;
    }
}