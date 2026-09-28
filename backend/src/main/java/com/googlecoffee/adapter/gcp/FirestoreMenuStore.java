package com.googlecoffee.adapter.gcp;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.googlecoffee.model.MenuItem;
import com.googlecoffee.port.MenuStore;
import com.googlecoffee.service.Fs;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("gcp")
public class FirestoreMenuStore implements MenuStore {

    private static final String COLLECTION = "menu_items";
    private final Firestore db;

    public FirestoreMenuStore(Firestore db) {
        this.db = db;
    }

    @Override
    public List<MenuItem> findAll() {
        List<MenuItem> items = new ArrayList<>();
        for (QueryDocumentSnapshot d : Fs.await(db.collection(COLLECTION).get()).getDocuments()) {
            items.add(MenuItem.fromMap(d.getData()));
        }
        return items;
    }

    @Override
    public void saveAll(List<MenuItem> items) {
        WriteBatch batch = db.batch();
        items.forEach(i -> batch.set(db.collection(COLLECTION).document(i.id()), i.toMap()));
        Fs.await(batch.commit());
    }
}
