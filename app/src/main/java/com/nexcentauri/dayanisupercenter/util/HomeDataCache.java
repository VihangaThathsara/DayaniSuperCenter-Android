package com.nexcentauri.dayanisupercenter.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.nexcentauri.dayanisupercenter.model.HomeCategory;
import com.nexcentauri.dayanisupercenter.model.HomeProduct;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HomeDataCache {
    private static final List<HomeCategory> categories = new ArrayList<>();
    private static final List<HomeProduct> products = new ArrayList<>();
    private static boolean ready = false;
    private static final long MAX_AGE_MS = 86_400_000L;
    private static final Gson gson = new Gson();
    private static Context applicationContext;
    private static String account;
    private static SharedPreferences preferences;
    private static CatalogDatabase database;
    private static long revision;
    private static final ExecutorService databaseWorker = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "dayani-catalog-database"));

    private static final class Catalog {
        long savedAt;
        List<HomeCategory> categories;
        List<HomeProduct> products;
    }

    private HomeDataCache() {}

    public static synchronized void initialize(Context context) {
        applicationContext = context.getApplicationContext();
        if (database == null) database = new CatalogDatabase(applicationContext);
        ensureAccount();
    }

    private static void ensureAccount() {
        String uid = FirebaseAuth.getInstance().getUid();
        String current = uid == null ? "guest" : uid;
        if (applicationContext == null || current.equals(account)) return;
        account = current;
        long expectedRevision = ++revision;
        categories.clear();
        products.clear();
        ready = false;
        preferences = applicationContext.getSharedPreferences("dayani_catalog_" + current, Context.MODE_PRIVATE);
        Catalog local = null;
        try {
            Catalog saved = gson.fromJson(preferences.getString("catalog", ""), Catalog.class);
            if (saved != null && saved.categories != null && saved.products != null
                    && System.currentTimeMillis() - saved.savedAt < MAX_AGE_MS) {
                for (HomeCategory category : saved.categories) if (category != null) categories.add(category);
                for (HomeProduct product : saved.products) if (product != null && product.isActive()) products.add(product);
                ready = true;
                local = saved;
            }
        } catch (RuntimeException ignored) {
            preferences.edit().remove("catalog").apply();
        }
        // Keep the existing immediate preferences cache; all SQLite I/O is off the UI thread.
        Catalog legacy = local;
        String cacheAccount = account;
        SharedPreferences cachePreferences = preferences;
        CatalogDatabase cacheDatabase = database;
        databaseWorker.execute(() -> {
            try {
                CatalogDatabase.Snapshot stored = cacheDatabase.read(cacheAccount);
                synchronized (HomeDataCache.class) {
                    if (expectedRevision != revision || !cacheAccount.equals(account)) return;
                    if (!ready && stored != null
                            && System.currentTimeMillis() - stored.savedAt < MAX_AGE_MS) {
                        for (HomeCategory category : stored.categories) if (category != null) categories.add(category);
                        for (HomeProduct product : stored.products) if (product != null && product.isActive()) products.add(product);
                        ready = true;
                        Catalog restored = new Catalog();
                        restored.savedAt = stored.savedAt;
                        restored.categories = new ArrayList<>(categories);
                        restored.products = new ArrayList<>(products);
                        cachePreferences.edit().putString("catalog", gson.toJson(restored)).apply();
                    }
                }
                if (legacy != null && (stored == null || stored.savedAt < legacy.savedAt)) {
                    cacheDatabase.write(cacheAccount, new CatalogDatabase.Snapshot(legacy.savedAt,
                            legacy.categories, legacy.products));
                }
            } catch (RuntimeException error) {
                Log.w("HomeDataCache", "SQLite cache unavailable; retaining existing cache.");
            }
        });
    }

    public static synchronized void setData(List<HomeCategory> newCategories, List<HomeProduct> newProducts) {
        ensureAccount();
        ++revision;
        categories.clear();
        products.clear();
        if (newCategories != null) {
            categories.addAll(newCategories);
        }
        if (newProducts != null) {
            products.addAll(newProducts);
        }
        ready = true;
        if (preferences != null) {
            Catalog saved = new Catalog();
            saved.savedAt = System.currentTimeMillis();
            saved.categories = new ArrayList<>(categories);
            saved.products = new ArrayList<>(products);
            preferences.edit().putString("catalog", gson.toJson(saved)).apply();
            String cacheAccount = account;
            CatalogDatabase cacheDatabase = database;
            CatalogDatabase.Snapshot snapshot = new CatalogDatabase.Snapshot(saved.savedAt,
                    saved.categories, saved.products);
            databaseWorker.execute(() -> {
                try {
                    cacheDatabase.write(cacheAccount, snapshot);
                } catch (RuntimeException error) {
                    Log.w("HomeDataCache", "SQLite cache write unavailable; retaining existing cache.");
                }
            });
        }
    }

    public static synchronized List<HomeCategory> getCategories() {
        ensureAccount();
        return new ArrayList<>(categories);
    }

    public static synchronized List<HomeProduct> getProducts() {
        ensureAccount();
        return new ArrayList<>(products);
    }

    public static synchronized boolean isReady() {
        ensureAccount();
        return ready;
    }

    public static synchronized void clear() {
        ensureAccount();
        ++revision;
        categories.clear();
        products.clear();
        ready = false;
        if (preferences != null) preferences.edit().remove("catalog").apply();
        if (database != null && account != null) {
            String cacheAccount = account;
            CatalogDatabase cacheDatabase = database;
            databaseWorker.execute(() -> {
                try {
                    cacheDatabase.clear(cacheAccount);
                } catch (RuntimeException error) {
                    Log.w("HomeDataCache", "SQLite cache clear unavailable.");
                }
            });
        }
    }
}
