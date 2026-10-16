package com.nexcentauri.dayanisupercenter.util;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProductSnapshotCache {
    private static final Map<String, DocumentSnapshot> products = new LinkedHashMap<>();
    private ProductSnapshotCache() {}

    public static synchronized void replace(QuerySnapshot snapshot) {
        products.clear();
        for (DocumentSnapshot document : snapshot.getDocuments()) products.put(document.getId(), document);
    }

    public static synchronized void put(DocumentSnapshot document) { products.put(document.getId(), document); }

    public static synchronized DocumentSnapshot get(String id) {
        DocumentSnapshot direct = products.get(id);
        if (direct != null) return direct;
        for (DocumentSnapshot document : products.values()) {
            if (id.equals(document.getString("id"))) return document;
        }
        return null;
    }

    public static synchronized List<DocumentSnapshot> all() { return new ArrayList<>(products.values()); }
}
