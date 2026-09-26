package com.googlecoffee.service;

import com.googlecoffee.model.MenuItem;

import java.util.List;
import java.util.Set;

/**
 * Hard dietary constraints. Used to reject any AI suggestion that conflicts
 * with what the guest told us, so the model can never override a preference.
 */
public final class PreferenceRules {

    public static final Set<String> ALLOWED = Set.of(
            "vegan", "dairy-free", "low-sugar", "caffeine-free", "cold", "hot");

    private PreferenceRules() {}

    public static boolean violates(MenuItem item, List<String> prefs) {
        if (prefs == null) return false;
        for (String p : prefs) {
            switch (p) {
                case "vegan" -> { if (!item.hasTag("vegan")) return true; }
                case "dairy-free" -> { if (!item.hasTag("dairy-free") && !item.hasTag("vegan")) return true; }
                case "low-sugar" -> { if (!item.hasTag("low-sugar")) return true; }
                case "caffeine-free" -> { if (!item.hasTag("caffeine-free")) return true; }
                default -> { /* soft preferences (hot/cold) are hints, not filters */ }
            }
        }
        return false;
    }

    public static List<String> sanitize(List<String> prefs) {
        if (prefs == null) return List.of();
        return prefs.stream().filter(ALLOWED::contains).distinct().toList();
    }
}
