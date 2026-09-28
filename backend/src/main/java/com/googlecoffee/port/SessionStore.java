package com.googlecoffee.port;

import com.googlecoffee.model.Session;
import java.util.Optional;
 
public interface SessionStore {
    void save(Session session);
 
    Optional<Session> find(String id);
}
