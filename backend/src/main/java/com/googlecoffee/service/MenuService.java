package com.googlecoffee.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.model.MenuItem;
import com.googlecoffee.port.MenuStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu lives in the configured store (Firestore or Postgres). On first start it is
 * seeded from menu-seed.json. Cached in memory because it changes rarely and is read
 * on every AI call.
 */
@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);

    private final MenuStore store;
    private final ObjectMapper mapper;
    private volatile Map<String, MenuItem> byId = Map.of();

    public MenuService(MenuStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        List<MenuItem> seed = readSeed();
        try {
            List<MenuItem> items = store.findAll();
            if (items.isEmpty()) {
                store.saveAll(seed);
                log.info("Seeded {} menu items", seed.size());
                setMenu(seed);
            } else {
                setMenu(items);
                log.info("Loaded {} menu items", items.size());
            }
        } catch (Exception e) {
            log.error("Could not reach the database ({}). Serving the bundled sample menu.", e.getMessage());
            setMenu(seed);
        }
    }

    private void setMenu(List<MenuItem> items) {
        Map<String, MenuItem> map = new LinkedHashMap<>();
        items.stream()
                .sorted(Comparator.comparing(MenuItem::category).thenComparing(MenuItem::price))
                .forEach(item -> map.put(item.id(), item));
        byId = map;
    }

    private List<MenuItem> readSeed() {
        try (InputStream inputStream = new ClassPathResource("menu-seed.json").getInputStream()) {
            return mapper.readValue(inputStream, new TypeReference<List<MenuItem>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("menu-seed.json missing or invalid", e);
        }
    }

    public List<MenuItem> all() {
        return List.copyOf(byId.values());
    }

    public MenuItem find(String id) {
        return id == null ? null : byId.get(id);
    }

    public String promptBlock() {
        StringBuilder sb = new StringBuilder();
        byId.values().forEach(item -> sb.append(item.toPromptLine()).append('\n'));
        return sb.toString();
    }
}