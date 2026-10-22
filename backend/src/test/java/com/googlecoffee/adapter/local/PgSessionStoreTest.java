package com.googlecoffee.adapter.local;

import com.googlecoffee.model.Session;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgSessionStoreTest extends PostgresTestBase {

    private final PgSessionStore store = new PgSessionStore(jdbc, JSON);

    @Test
    void savedSessionRoundTripsWithItsPreferences() {
        Session s = new Session("s1", "Asha", "7", List.of("vegan", "cold"), 1_000L);

        store.save(s);

        assertEquals(s, store.find("s1").orElseThrow());
    }

    @Test
    void unknownSessionIsEmpty() {
        assertTrue(store.find("missing").isEmpty());
    }

    @Test
    void savingAgainUpdatesDetailsButKeepsTheOriginalCreationTime() {
        store.save(new Session("s1", "Asha", "7", List.of("vegan"), 1_000L));

        store.save(new Session("s1", "Asha K", "9", List.of(), 9_999L));

        Session found = store.find("s1").orElseThrow();
        assertEquals("Asha K", found.name());
        assertEquals("9", found.table());
        assertEquals(List.of(), found.preferences());
        assertEquals(1_000L, found.createdAt());
    }
}
