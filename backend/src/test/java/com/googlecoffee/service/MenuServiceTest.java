package com.googlecoffee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.model.MenuItem;
import com.googlecoffee.support.Fixtures;
import com.googlecoffee.support.InMemoryMenuStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuServiceTest {

    private static MenuService serviceOver(InMemoryMenuStore store) {
        return new MenuService(store, new ObjectMapper());
    }

    @Test
    void emptyStoreIsSeededFromTheBundledMenu() {
        InMemoryMenuStore store = new InMemoryMenuStore();
        MenuService service = serviceOver(store);

        service.load();

        assertFalse(service.all().isEmpty());
        assertEquals(service.all().size(), store.findAll().size());
        assertNotNull(service.find("espresso"));
    }

    @Test
    void existingStoreContentWinsOverTheSeed() {
        InMemoryMenuStore store = new InMemoryMenuStore(Fixtures.espresso());
        MenuService service = serviceOver(store);

        service.load();

        assertEquals(1, service.all().size());
        assertEquals(1, store.findAll().size());
    }

    @Test
    void unreachableStoreFallsBackToTheBundledMenu() {
        MenuService service = serviceOver(new InMemoryMenuStore().failing());

        service.load();

        assertFalse(service.all().isEmpty());
        assertNotNull(service.find("espresso"));
    }

    @Test
    void itemsAreOrderedAlphabeticallyByCategoryThenByPrice() {
        MenuItem cheapBakery = new MenuItem("bun", "Bun", "Bakery & bites", "", 100, 1, List.of(), false);
        MenuService service = serviceOver(new InMemoryMenuStore(Fixtures.toast(), Fixtures.espresso(), cheapBakery));

        service.load();

        assertEquals(List.of("bun", "toast", "espresso"), service.all().stream().map(MenuItem::id).toList());
    }

    @Test
    void findHandlesNullAndUnknownIds() {
        MenuService service = serviceOver(new InMemoryMenuStore(Fixtures.espresso()));
        service.load();

        assertNull(service.find(null));
        assertNull(service.find("unicorn-latte"));
    }

    @Test
    void promptBlockHasOneLinePerItem() {
        MenuService service = serviceOver(new InMemoryMenuStore(Fixtures.espresso(), Fixtures.toast()));
        service.load();

        String[] lines = service.promptBlock().split("\n");

        assertEquals(2, lines.length);
        assertTrue(List.of(lines).contains(Fixtures.espresso().toPromptLine()));
    }
}
