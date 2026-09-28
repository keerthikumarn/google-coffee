package com.googlecoffee.port;

import java.util.OptionalInt;
import java.util.function.IntConsumer;
 
public interface SettingsStore {
    OptionalInt activeBaristas();
 
    void saveActiveBaristas(int value);
 
    /** Notifies about changes made elsewhere (e.g. another instance). May be a no-op. */
    void watch(IntConsumer onChange);
}
