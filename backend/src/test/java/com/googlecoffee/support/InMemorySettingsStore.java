package com.googlecoffee.support;

import com.googlecoffee.port.SettingsStore;

import java.util.OptionalInt;
import java.util.function.IntConsumer;

public class InMemorySettingsStore implements SettingsStore {

    private OptionalInt baristas = OptionalInt.empty();
    private IntConsumer watcher = n -> {};
    private boolean failOnRead;

    public InMemorySettingsStore withBaristas(int value) {
        baristas = OptionalInt.of(value);
        return this;
    }

    /** Makes {@link #activeBaristas()} throw, like an unreachable database. */
    public InMemorySettingsStore failingReads() {
        failOnRead = true;
        return this;
    }

    /** Simulates another instance changing the setting. */
    public void changeElsewhere(int value) {
        baristas = OptionalInt.of(value);
        watcher.accept(value);
    }

    @Override
    public OptionalInt activeBaristas() {
        if (failOnRead) throw new IllegalStateException("database down");
        return baristas;
    }

    @Override
    public void saveActiveBaristas(int value) {
        baristas = OptionalInt.of(value);
    }

    @Override
    public void watch(IntConsumer onChange) {
        watcher = onChange;
    }
}
