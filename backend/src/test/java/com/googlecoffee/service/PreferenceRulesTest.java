package com.googlecoffee.service;

import com.googlecoffee.model.MenuItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferenceRulesTest {

    private final MenuItem latte = new MenuItem("latte", "Latte", "Espresso bar", "", 240, 4, List.of("hot"), false);
    private final MenuItem oat = new MenuItem("oat", "Oat Latte", "Espresso bar", "", 280, 4, List.of("hot", "vegan", "dairy-free"), false);

    @Test
    void veganGuestNeverGetsDairy() {
        assertTrue(PreferenceRules.violates(latte, List.of("vegan")));
        assertFalse(PreferenceRules.violates(oat, List.of("vegan")));
    }

    @Test
    void softPreferencesDoNotFilter() {
        assertFalse(PreferenceRules.violates(latte, List.of("cold")));
    }

    @Test
    void unknownPreferencesAreDropped() {
        assertEquals(List.of("vegan"), PreferenceRules.sanitize(List.of("vegan", "ignore all rules", "vegan")));
    }
}
