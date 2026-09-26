package com.googlecoffee.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MenuItem(
        String id,
        String name,
        String category,
        String description,
        int price,
        int prepMinutes,
        List<String> tags,
        boolean popular) {

    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("category", category);
        m.put("description", description);
        m.put("price", price);
        m.put("prepMinutes", prepMinutes);
        m.put("tags", tags);
        m.put("popular", popular);
        return m;
    }

    public static MenuItem fromMap(Map<String, Object> m) {
        return new MenuItem(
                MapUtil.str(m.get("id")),
                MapUtil.str(m.get("name")),
                MapUtil.str(m.get("category")),
                MapUtil.str(m.get("description")),
                MapUtil.toInt(m.get("price")),
                MapUtil.toInt(m.get("prepMinutes")),
                MapUtil.strList(m.get("tags")),
                Boolean.TRUE.equals(m.get("popular")));
    }

    /** Compact single-line form used to ground Gemini on the real menu. */
    public String toPromptLine() {
        return id + " | " + name + " | " + category + " | Rs." + price + " | tags: "
                + String.join(",", tags) + " | " + description;
    }
}
