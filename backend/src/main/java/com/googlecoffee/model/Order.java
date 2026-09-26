package com.googlecoffee.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Order(
        String id,
        String code,
        String sessionId,
        String customerName,
        String table,
        List<OrderItem> items,
        int total,
        int prepMinutes,
        OrderStatus status,
        long createdAt,
        long updatedAt) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("code", code);
        m.put("sessionId", sessionId);
        m.put("customerName", customerName);
        m.put("table", table);
        m.put("items", items.stream().map(OrderItem::toMap).toList());
        m.put("total", total);
        m.put("prepMinutes", prepMinutes);
        m.put("status", status.name());
        m.put("createdAt", createdAt);
        m.put("updatedAt", updatedAt);
        return m;
    }

    public static Order fromMap(Map<String, Object> m) {
        OrderStatus status;
        try {
            status = OrderStatus.valueOf(MapUtil.str(m.get("status")));
        } catch (IllegalArgumentException e) {
            status = OrderStatus.PLACED;
        }
        return new Order(
                MapUtil.str(m.get("id")),
                MapUtil.str(m.get("code")),
                MapUtil.str(m.get("sessionId")),
                MapUtil.str(m.get("customerName")),
                MapUtil.str(m.get("table")),
                MapUtil.mapList(m.get("items")).stream().map(OrderItem::fromMap).toList(),
                MapUtil.toInt(m.get("total")),
                MapUtil.toInt(m.get("prepMinutes")),
                status,
                MapUtil.toLong(m.get("createdAt")),
                MapUtil.toLong(m.get("updatedAt")));
    }
}
