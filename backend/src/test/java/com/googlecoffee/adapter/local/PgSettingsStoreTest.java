package com.googlecoffee.adapter.local;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgSettingsStoreTest extends PostgresTestBase {

    private final PgSettingsStore store = new PgSettingsStore(jdbc);

    @Test
    void nothingStoredMeansNoValue() {
        assertTrue(store.activeBaristas().isEmpty());
    }

    @Test
    void savedValueIsReadBack() {
        store.saveActiveBaristas(3);

        assertEquals(OptionalInt.of(3), store.activeBaristas());
    }

    @Test
    void savingAgainReplacesTheValue() {
        store.saveActiveBaristas(3);
        store.saveActiveBaristas(5);

        assertEquals(OptionalInt.of(5), store.activeBaristas());
    }

    @Test
    void garbageOrNonPositiveValuesAreTreatedAsMissing() {
        for (String bad : new String[] {"abc", "0", "-2", ""}) {
            jdbc.sql("INSERT INTO settings (key, value) VALUES ('activeBaristas', :v) "
                    + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value").param("v", bad).update();

            assertTrue(store.activeBaristas().isEmpty(), "value '" + bad + "' should be ignored");
        }
    }

    @Test
    void valueWithSurroundingWhitespaceIsAccepted() {
        jdbc.sql("INSERT INTO settings (key, value) VALUES ('activeBaristas', ' 4 ')").update();

        assertEquals(OptionalInt.of(4), store.activeBaristas());
    }
}
