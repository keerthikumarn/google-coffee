package com.googlecoffee.model;

import java.util.HashMap;
import java.util.Map;

public record Feedback(String id, String sessionId, String orderId, String table, int rating, String comment, long createdAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("sessionId", sessionId);
        m.put("orderId", orderId == null ? "" : orderId);
        m.put("table", table);
        m.put("rating", rating);
        m.put("comment", comment == null ? "" : comment);
        m.put("createdAt", createdAt);
        return m;
    }

    public static Feedback fromMap(Map<String, Object> m) {
        return new Feedback(
                MapUtil.str(m.get("id")),
                MapUtil.str(m.get("sessionId")),
                MapUtil.str(m.get("orderId")),
                MapUtil.str(m.get("table")),
                MapUtil.toInt(m.get("rating")),
                MapUtil.str(m.get("comment")),
                MapUtil.toLong(m.get("createdAt")));
    }
}
