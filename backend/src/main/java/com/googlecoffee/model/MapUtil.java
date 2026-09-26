package com.googlecoffee.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small helpers for reading Firestore maps defensively (numbers arrive as Long/Double). */
public final class MapUtil {
    private MapUtil() {}

    public static int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    public static long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    public static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<String> strList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) out.add(o.toString());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> mapList(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
        }
        return out;
    }
}
