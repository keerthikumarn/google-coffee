package com.googlecoffee.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Session(String id, String name, String table, List<String> preferences, long createdAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("table", table);
        m.put("preferences", preferences);
        m.put("createdAt", createdAt);
        return m;
    }

    public static Session fromMap(Map<String, Object> m) {
        return new Session(
                MapUtil.str(m.get("id")),
                MapUtil.str(m.get("name")),
                MapUtil.str(m.get("table")),
                MapUtil.strList(m.get("preferences")),
                MapUtil.toLong(m.get("createdAt")));
    }
}
