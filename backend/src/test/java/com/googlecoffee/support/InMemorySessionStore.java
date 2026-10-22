package com.googlecoffee.support;

import com.googlecoffee.model.Session;
import com.googlecoffee.port.SessionStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemorySessionStore implements SessionStore {

    private final Map<String, Session> sessions = new HashMap<>();

    @Override
    public void save(Session session) {
        sessions.put(session.id(), session);
    }

    @Override
    public Optional<Session> find(String id) {
        return Optional.ofNullable(sessions.get(id));
    }
}
