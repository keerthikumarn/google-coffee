package com.googlecoffee.support;

import com.googlecoffee.model.MenuItem;
import com.googlecoffee.port.MenuStore;

import java.util.ArrayList;
import java.util.List;

public class InMemoryMenuStore implements MenuStore {

    private final List<MenuItem> items = new ArrayList<>();
    private boolean failing;

    public InMemoryMenuStore(MenuItem... initial) {
        items.addAll(List.of(initial));
    }

    /** Makes every call throw, like an unreachable database. */
    public InMemoryMenuStore failing() {
        failing = true;
        return this;
    }

    @Override
    public List<MenuItem> findAll() {
        if (failing) throw new IllegalStateException("database down");
        return List.copyOf(items);
    }

    @Override
    public void saveAll(List<MenuItem> toSave) {
        if (failing) throw new IllegalStateException("database down");
        items.clear();
        items.addAll(toSave);
    }
}
