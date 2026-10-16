package com.nexcentauri.dayanisupercenter.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.nexcentauri.dayanisupercenter.model.HomeCategory;
import com.nexcentauri.dayanisupercenter.model.HomeProduct;

import java.util.ArrayList;
import java.util.List;

/** Private, account-scoped copy of the existing server catalogue. */
final class CatalogDatabase extends SQLiteOpenHelper {
    static final class Snapshot {
        final long savedAt;
        final List<HomeCategory> categories;
        final List<HomeProduct> products;

        Snapshot(long savedAt, List<HomeCategory> categories, List<HomeProduct> products) {
            this.savedAt = savedAt;
            this.categories = categories;
            this.products = products;
        }
    }

    CatalogDatabase(Context context) {
        super(context.getApplicationContext(), "dayani_catalog.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS catalog_snapshots (account TEXT PRIMARY KEY, saved_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS catalog_categories (account TEXT NOT NULL, position INTEGER NOT NULL, "
                + "id TEXT, name TEXT, image_url TEXT, PRIMARY KEY(account, position))");
        db.execSQL("CREATE TABLE IF NOT EXISTS catalog_products (account TEXT NOT NULL, position INTEGER NOT NULL, "
                + "id TEXT, name TEXT, rating REAL NOT NULL, sold_count INTEGER NOT NULL, price INTEGER NOT NULL, "
                + "image_url TEXT, category_id TEXT, category_name TEXT, most_popular INTEGER NOT NULL, "
                + "active INTEGER NOT NULL, PRIMARY KEY(account, position))");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    Snapshot read(String account) {
        SQLiteDatabase db = getReadableDatabase();
        db.beginTransaction();
        try {
            long savedAt;
            try (Cursor row = db.query("catalog_snapshots", new String[]{"saved_at"},
                    "account = ?", new String[]{account}, null, null, null)) {
                if (!row.moveToNext()) return null;
                savedAt = row.getLong(0);
            }
            List<HomeCategory> categories = new ArrayList<>();
            try (Cursor rows = db.query("catalog_categories", new String[]{"id", "name", "image_url"},
                    "account = ?", new String[]{account}, null, null, "position ASC")) {
                while (rows.moveToNext()) categories.add(new HomeCategory(rows.getString(0),
                        rows.getString(1), rows.getString(2)));
            }
            List<HomeProduct> products = new ArrayList<>();
            try (Cursor rows = db.query("catalog_products", new String[]{"id", "name", "rating", "sold_count",
                    "price", "image_url", "category_id", "category_name", "most_popular", "active"},
                    "account = ?", new String[]{account}, null, null, "position ASC")) {
                while (rows.moveToNext()) products.add(new HomeProduct(rows.getString(0), rows.getString(1),
                        rows.getDouble(2), rows.getInt(3), rows.getInt(4), rows.getString(5),
                        rows.getString(6), rows.getString(7), rows.getInt(8) != 0, rows.getInt(9) != 0));
            }
            db.setTransactionSuccessful();
            return new Snapshot(savedAt, categories, products);
        } finally {
            db.endTransaction();
        }
    }

    void write(String account, Snapshot snapshot) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            deleteRows(db, account);
            for (int i = 0; i < snapshot.categories.size(); i++) {
                HomeCategory category = snapshot.categories.get(i);
                if (category == null) continue;
                ContentValues row = baseRow(account, i);
                row.put("id", category.getId());
                row.put("name", category.getName());
                row.put("image_url", category.getImageUrl());
                db.insertOrThrow("catalog_categories", null, row);
            }
            for (int i = 0; i < snapshot.products.size(); i++) {
                HomeProduct product = snapshot.products.get(i);
                if (product == null) continue;
                ContentValues row = baseRow(account, i);
                row.put("id", product.getId());
                row.put("name", product.getName());
                row.put("rating", product.getRating());
                row.put("sold_count", product.getSoldCount());
                row.put("price", product.getPrice());
                row.put("image_url", product.getImageUrl());
                row.put("category_id", product.getCategoryId());
                row.put("category_name", product.getCategoryName());
                row.put("most_popular", product.isMostPopular() ? 1 : 0);
                row.put("active", product.isActive() ? 1 : 0);
                db.insertOrThrow("catalog_products", null, row);
            }
            ContentValues row = new ContentValues();
            row.put("account", account);
            row.put("saved_at", snapshot.savedAt);
            db.insertOrThrow("catalog_snapshots", null, row);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void clear(String account) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            deleteRows(db, account);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static ContentValues baseRow(String account, int position) {
        ContentValues row = new ContentValues();
        row.put("account", account);
        row.put("position", position);
        return row;
    }

    private static void deleteRows(SQLiteDatabase db, String account) {
        String[] args = {account};
        db.delete("catalog_categories", "account = ?", args);
        db.delete("catalog_products", "account = ?", args);
        db.delete("catalog_snapshots", "account = ?", args);
    }
}
