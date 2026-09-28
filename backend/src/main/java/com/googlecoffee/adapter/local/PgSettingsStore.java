package com.googlecoffee.adapter.local;

import com.googlecoffee.port.SettingsStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.OptionalInt;
import java.util.function.IntConsumer;

@Component
@Profile("local")
public class PgSettingsStore implements SettingsStore {

    private static final String KEY = "activeBaristas";
    private final JdbcClient jdbc;

    public PgSettingsStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalInt activeBaristas() {
        return jdbc.sql("SELECT value FROM settings WHERE key = :key")
                .param("key", KEY)
                .query(String.class)
                .optional()
                .map(v -> {
                    try {
                        int n = Integer.parseInt(v.trim());
                        return n > 0 ? OptionalInt.of(n) : OptionalInt.empty();
                    } catch (NumberFormatException e) {
                        return OptionalInt.empty();
                    }
                })
                .orElse(OptionalInt.empty());
    }

    @Override
    public void saveActiveBaristas(int value) {
        jdbc.sql("""
                INSERT INTO settings (key, value) VALUES (:key, :value)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """)
                .param("key", KEY)
                .param("value", Integer.toString(value))
                .update();
    }

    /** Single instance locally: changes are applied in-process by SettingsService, nothing to watch. */
    @Override
    public void watch(IntConsumer onChange) {
        // no-op
    }
}
