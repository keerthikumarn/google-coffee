package com.googlecoffee.support;

import com.googlecoffee.port.SettingsStore;

import java.util.OptionalInt;
import java.util.function.IntConsumer;

public class InMemorySettingsStore implements SettingsStore {

    private OptionalInt baristas = OptionalInt.empty();

    @Override
    public OptionalInt activeBaristas() {
        return baristas;
    }

    @Override
    public void saveActiveBaristas(int value) {
        baristas = OptionalInt.of(value);
    }

    @Override
    public void watch(IntConsumer onChange) {
        // nothing changes behind our back in tests
    }
}
