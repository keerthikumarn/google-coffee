package com.googlecoffee.model;

import java.util.HashMap;
import java.util.Map;

public record OrderItem(String itemId, String name, int qty, int unitPrice, String note) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("itemId", itemId);
        m.put("name", name);
        m.put("qty", qty);
        m.put("unitPrice", unitPrice);
        m.put("note", note == null ? "" : note);
        return m;
    }

    public static OrderItem fromMap(Map<String, Object> m) {
        return new OrderItem(
                MapUtil.str(m.get("itemId")),
                MapUtil.str(m.get("name")),
                MapUtil.toInt(m.get("qty")),
                MapUtil.toInt(m.get("unitPrice")),
                MapUtil.str(m.get("note")));
    }
}
