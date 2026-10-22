package com.googlecoffee.service;

import com.googlecoffee.support.Fixtures;
import com.googlecoffee.support.InMemorySettingsStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsServiceTest {

    @Test
    void startsWithTheConfiguredDefault() {
        assertEquals(3, new SettingsService(new InMemorySettingsStore(), Fixtures.props(3)).activeBaristas());
    }

    @Test
    void defaultIsNeverBelowOne() {
        assertEquals(1, new SettingsService(new InMemorySettingsStore(), Fixtures.props(0)).activeBaristas());
    }

    @Test
    void initLoadsThePersistedValue() {
        SettingsService service = new SettingsService(new InMemorySettingsStore().withBaristas(4), Fixtures.props(1));

        service.init();

        assertEquals(4, service.activeBaristas());
    }

    @Test
    void initKeepsTheDefaultWhenTheStoreIsUnreachable() {
        SettingsService service = new SettingsService(new InMemorySettingsStore().failingReads(), Fixtures.props(2));

        service.init();

        assertEquals(2, service.activeBaristas());
    }

    @Test
    void setClampsToBetweenOneAndEightAndPersists() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        SettingsService service = new SettingsService(store, Fixtures.props(1));

        assertEquals(8, service.setActiveBaristas(50));
        assertEquals(1, service.setActiveBaristas(-3));
        assertEquals(5, service.setActiveBaristas(5));
        assertEquals(5, store.activeBaristas().getAsInt());
        assertEquals(5, service.activeBaristas());
    }

    @Test
    void setNotifiesListeners() {
        SettingsService service = new SettingsService(new InMemorySettingsStore(), Fixtures.props(1));
        List<Integer> seen = new ArrayList<>();
        service.onChange(seen::add);

        service.setActiveBaristas(3);

        assertEquals(List.of(3), seen);
    }

    @Test
    void changeMadeByAnotherInstanceIsAppliedAndAnnounced() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        SettingsService service = new SettingsService(store, Fixtures.props(1));
        List<Integer> seen = new ArrayList<>();
        service.onChange(seen::add);
        service.init();

        store.changeElsewhere(6);

        assertEquals(6, service.activeBaristas());
        assertEquals(List.of(6), seen);
    }

    @Test
    void remoteChangesThatAreInvalidOrUnchangedAreIgnored() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        SettingsService service = new SettingsService(store, Fixtures.props(2));
        List<Integer> seen = new ArrayList<>();
        service.onChange(seen::add);
        service.init();

        store.changeElsewhere(0);
        store.changeElsewhere(-1);
        store.changeElsewhere(2);

        assertEquals(2, service.activeBaristas());
        assertEquals(List.of(), seen);
    }
}
