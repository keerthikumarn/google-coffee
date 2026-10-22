package com.googlecoffee.adapter.local;

import com.googlecoffee.model.MenuItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgMenuStoreTest extends PostgresTestBase {

    private final PgMenuStore store = new PgMenuStore(jdbc, JSON);

    private static MenuItem item(String id, String name, int price) {
        return new MenuItem(id, name, "Espresso bar", "desc", price, 3, List.of("hot", "vegan"), true);
    }

    @Test
    void emptyTableGivesEmptyMenu() {
        assertTrue(store.findAll().isEmpty());
    }

    @Test
    void savedItemsRoundTripIncludingTagsAndFlags() {
        MenuItem espresso = item("espresso", "Espresso", 160);
        MenuItem latte = new MenuItem("latte", "Latte", "Espresso bar", "", 240, 4, List.of(), false);

        store.saveAll(List.of(espresso, latte));

        List<MenuItem> found = store.findAll();
        assertEquals(2, found.size());
        assertTrue(found.contains(espresso));
        assertTrue(found.contains(latte));
    }

    @Test
    void savingAnExistingIdNeverOverwritesIt() {
        store.saveAll(List.of(item("espresso", "Espresso", 160)));

        store.saveAll(List.of(item("espresso", "Renamed", 999), item("mocha", "Mocha", 260)));

        List<MenuItem> found = store.findAll();
        assertEquals(2, found.size());
        assertEquals("Espresso", found.stream().filter(i -> i.id().equals("espresso")).findFirst().orElseThrow().name());
    }
}
