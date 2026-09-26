package com.googlecoffee.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.googlecoffee.model.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu lives in Firestore (collection "menu_items"). On first start the
 * collection is seeded from menu-seed.json. The menu is cached in memory
 * because it changes rarely and is read on every AI call.
 */
@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);
    static final String COLLECTION = "menu_items";

    private final Firestore db;
    private final ObjectMapper mapper;
    private volatile Map<String, MenuItem> byId = Map.of();

    public MenuService(Firestore db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        List<MenuItem> seed = readSeed();
        try {
            List<QueryDocumentSnapshot> docs = Fs.await(db.collection(COLLECTION).get()).getDocuments();
            if (docs.isEmpty()) {
                WriteBatch batch = db.batch();
                seed.forEach(i -> batch.set(db.collection(COLLECTION).document(i.id()), i.toMap()));
                Fs.await(batch.commit());
                log.info("Seeded {} menu items into Firestore", seed.size());
                setMenu(seed);
            } else {
                List<MenuItem> items = new ArrayList<>();
                docs.forEach(d -> items.add(MenuItem.fromMap(d.getData())));
                setMenu(items);
                log.info("Loaded {} menu items from Firestore", items.size());
            }
        } catch (Exception e) {
            log.error("Could not reach Firestore ({}). Serving the bundled sample menu. "
                    + "Check `gcloud auth application-default login` and that the Firestore database exists.",
                    e.getMessage());
            setMenu(seed);
        }
    }

    private void setMenu(List<MenuItem> items) {
        Map<String, MenuItem> map = new LinkedHashMap<>();
        items.stream()
                .sorted(Comparator.comparing(MenuItem::category).thenComparing(MenuItem::price))
                .forEach(i -> map.put(i.id(), i));
        byId = map;
    }

    private List<MenuItem> readSeed() {
        try (InputStream in = new ClassPathResource("menu-seed.json").getInputStream()) {
            return mapper.readValue(in, new TypeReference<List<MenuItem>>() {});
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
        byId.values().forEach(i -> sb.append(i.toPromptLine()).append('\n'));
        return sb.toString();
    }
}
