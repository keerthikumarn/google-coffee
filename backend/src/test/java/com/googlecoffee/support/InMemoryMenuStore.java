package com.googlecoffee.support;

import com.googlecoffee.model.MenuItem;
import com.googlecoffee.port.MenuStore;

import java.util.ArrayList;
import java.util.List;

public class InMemoryMenuStore implements MenuStore {

    private final List<MenuItem> items = new ArrayList<>();

    public InMemoryMenuStore(MenuItem... initial) {
        items.addAll(List.of(initial));
    }

    @Override
    public List<MenuItem> findAll() {
        return List.copyOf(items);
    }

    @Override
    public void saveAll(List<MenuItem> toSave) {
        items.clear();
        items.addAll(toSave);
    }
}
