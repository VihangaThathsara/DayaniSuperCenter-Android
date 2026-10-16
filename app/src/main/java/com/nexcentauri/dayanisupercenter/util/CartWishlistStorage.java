package com.nexcentauri.dayanisupercenter.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CartWishlistStorage {

    private static final String PREF_NAME = "dayani_shop_data";
    private static final String KEY_CART = "cart_items";
    private static final String KEY_WISHLIST = "wishlist_items";
    private static final String COLLECTION_CART = "cart";
    private static final String COLLECTION_WISHLIST = "wishlist";
    private static final java.util.Set<String> pendingCartOwners = new java.util.HashSet<>();
    private static String cachedOwner;
    private static List<SavedProductItem> cachedCartItems;
    private static List<SavedProductItem> cachedWishlistItems;

    public interface MutationCallback {
        void onSaved();
        void onError(String message);
    }

    public interface ItemsCallback {
        void onLoaded(List<SavedProductItem> items);
    }

    private CartWishlistStorage() {
    }

    private static SharedPreferences prefs(Context context) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        String owner = uid == null ? "guest" : uid;
        if (!owner.equals(cachedOwner)) {
            cachedOwner = owner;
            cachedCartItems = null;
            cachedWishlistItems = null;
        }
        return context.getSharedPreferences(PREF_NAME + "_" + (uid == null ? "guest" : uid), Context.MODE_PRIVATE);
    }

    public static synchronized List<SavedProductItem> getCartItems(Context context) {
        prefs(context);
        if (cachedCartItems == null) {
            cachedCartItems = readItems(prefs(context).getString(KEY_CART, "[]"));
        }
        return copyOf(cachedCartItems);
    }

    public static synchronized List<SavedProductItem> getWishlistItems(Context context) {
        prefs(context);
        if (cachedWishlistItems == null) {
            cachedWishlistItems = readItems(prefs(context).getString(KEY_WISHLIST, "[]"));
        }
        return copyOf(cachedWishlistItems);
    }

    public static void getCartItems(Context context, @NonNull ItemsCallback callback) {
        loadItems(context, COLLECTION_CART, KEY_CART, callback);
    }

    public static void getWishlistItems(Context context, @NonNull ItemsCallback callback) {
        loadItems(context, COLLECTION_WISHLIST, KEY_WISHLIST, callback);
    }

    public static void addToCart(Context context, SavedProductItem item, MutationCallback callback) {
        changeCartQuantity(context, item, item.getQuantity(), true, callback);
    }

    public static void updateCartQuantity(Context context, String productId, int quantity, MutationCallback callback) {
        for (SavedProductItem item : getCartItems(context)) {
            if (same(item.getProductId(), productId)) {
                changeCartQuantity(context, item, quantity, false, callback);
                return;
            }
        }
        callback.onError("Product is no longer in your cart.");
    }

    private static void changeCartQuantity(Context context, SavedProductItem item, int requested,
                                            boolean add, MutationCallback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            callback.onError("Please sign in to add products to your cart.");
            return;
        }
        synchronized (pendingCartOwners) {
            if (!pendingCartOwners.add(uid)) {
                callback.onError("Please wait for your cart update to finish.");
                return;
            }
        }
        int localQuantity = 0;
        for (SavedProductItem saved : getCartItems(context)) {
            if (same(saved.getProductId(), item.getProductId())) localQuantity = saved.getQuantity();
        }
        ProductStockService.changeCartQuantity(uid, item, requested, add, localQuantity)
                .addOnSuccessListener(quantity -> {
                    synchronized (pendingCartOwners) { pendingCartOwners.remove(uid); }
                    if (!uid.equals(FirebaseAuth.getInstance().getUid())) return;
                    List<SavedProductItem> items = getCartItems(context);
                    boolean found = false;
                    for (SavedProductItem saved : items) {
                        if (same(saved.getProductId(), item.getProductId())) {
                            saved.setQuantity(quantity);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        item.setQuantity(quantity);
                        items.add(item);
                    }
                    saveCartItems(context, items);
                    FirebaseFirestore.getInstance().collection(COLLECTION_CART)
                            .document(uid + "_" + item.getProductId()).delete();
                    callback.onSaved();
                }).addOnFailureListener(error -> {
                    synchronized (pendingCartOwners) { pendingCartOwners.remove(uid); }
                    if (uid.equals(FirebaseAuth.getInstance().getUid())) callback.onError(ProductStockService.errorMessage(error));
                });
    }

    public static void clearCart(Context context) {
        saveCartItems(context, new ArrayList<>());
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(user.getUid()).collection(COLLECTION_CART)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        doc.getReference().delete();
                    }
                });
    }

    public static void removeCartItem(Context context, String productId) {
        List<SavedProductItem> items = getCartItems(context);
        for (int i = 0; i < items.size(); i++) {
            if (same(items.get(i).getProductId(), productId)) {
                items.remove(i);
                break;
            }
        }
        saveCartItems(context, items);
        deleteRemoteItem(COLLECTION_CART, productId);
    }

    public static boolean isInWishlist(Context context, String productId) {
        List<SavedProductItem> items = getWishlistItems(context);
        for (SavedProductItem item : items) {
            if (same(item.getProductId(), productId)) {
                return true;
            }
        }
        return false;
    }

    public static void toggleWishlist(Context context, SavedProductItem item) {
        List<SavedProductItem> items = getWishlistItems(context);
        for (int i = 0; i < items.size(); i++) {
            if (same(items.get(i).getProductId(), item.getProductId())) {
                items.remove(i);
                saveWishlistItems(context, items);
                deleteRemoteItem(COLLECTION_WISHLIST, item.getProductId());
                return;
            }
        }
        SavedProductItem savedItem = new SavedProductItem(item.getProductId(), item.getName(), item.getImageUrl(), item.getPrice(), 1, item.getRating(), item.getSoldCount());
        items.add(savedItem);
        saveWishlistItems(context, items);
        syncItem(context, COLLECTION_WISHLIST, savedItem);
    }

    public static void removeWishlistItem(Context context, String productId) {
        List<SavedProductItem> items = getWishlistItems(context);
        for (int i = 0; i < items.size(); i++) {
            if (same(items.get(i).getProductId(), productId)) {
                items.remove(i);
                break;
            }
        }
        saveWishlistItems(context, items);
        deleteRemoteItem(COLLECTION_WISHLIST, productId);
    }

    private static void loadItems(Context context, String remoteCollection, String localKey, @NonNull ItemsCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            deliverItems(context, localKey, readItems(prefs(context).getString(localKey, "[]")), remoteCollection, callback);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
                .document(user.getUid())
                .collection(remoteCollection)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!user.getUid().equals(FirebaseAuth.getInstance().getUid())) return;
                    List<SavedProductItem> items = mapFirestoreItems(queryDocumentSnapshots.getDocuments());
                    if (!items.isEmpty()) {
                        saveLocal(context, localKey, items);
                        deliverItems(context, localKey, items, remoteCollection, callback);
                    } else {
                        db.collection(remoteCollection)
                                .whereEqualTo("userId", user.getUid())
                                .get()
                                .addOnSuccessListener(topLevelSnapshots -> {
                                    if (!user.getUid().equals(FirebaseAuth.getInstance().getUid())) return;
                                    List<SavedProductItem> fallbackItems = mapFirestoreItems(topLevelSnapshots.getDocuments());
                                    saveLocal(context, localKey, fallbackItems);
                                    deliverItems(context, localKey, fallbackItems, remoteCollection, callback);
                                })
                                .addOnFailureListener(e -> {
                                    if (user.getUid().equals(FirebaseAuth.getInstance().getUid())) {
                                        deliverItems(context, localKey, readItems(prefs(context).getString(localKey, "[]")), remoteCollection, callback);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (user.getUid().equals(FirebaseAuth.getInstance().getUid())) {
                        deliverItems(context, localKey, readItems(prefs(context).getString(localKey, "[]")), remoteCollection, callback);
                    }
                });
    }

    private static void syncItem(Context context, String collection, SavedProductItem item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || item == null || TextUtils.isEmpty(item.getProductId())) {
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        JSONObject payload = new JSONObject();
        try {
            payload.put("productId", value(item.getProductId()));
            payload.put("id", value(item.getProductId()));
            payload.put("name", value(item.getName()));
            payload.put("productName", value(item.getName()));
            payload.put("imageUrl", value(item.getImageUrl()));
            payload.put("image", value(item.getImageUrl()));
            payload.put("price", item.getPrice());
            payload.put("quantity", Math.max(1, item.getQuantity()));
            payload.put("rating", item.getRating());
            payload.put("soldCount", item.getSoldCount());
            payload.put("userId", user.getUid());
        } catch (Exception ignored) {
        }

        db.collection("users")
                .document(user.getUid())
                .collection(collection)
                .document(item.getProductId())
                .set(jsonToMap(payload));

        db.collection(collection)
                .document(user.getUid() + "_" + item.getProductId())
                .delete();
    }

    private static void deleteRemoteItem(String collection, String productId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || TextUtils.isEmpty(productId)) {
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
                .document(user.getUid())
                .collection(collection)
                .document(productId)
                .delete();

        db.collection(collection)
                .document(user.getUid() + "_" + productId)
                .delete();
    }

    private static java.util.Map<String, Object> jsonToMap(JSONObject object) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        JSONArray names = object.names();
        if (names == null) {
            return map;
        }
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i);
            map.put(key, object.opt(key));
        }
        return map;
    }

    private static List<SavedProductItem> mapFirestoreItems(List<? extends com.google.firebase.firestore.DocumentSnapshot> documents) {
        List<SavedProductItem> items = new ArrayList<>();
        for (com.google.firebase.firestore.DocumentSnapshot document : documents) {
            if (document == null || !document.exists()) {
                continue;
            }
            String productId = firstNotEmpty(document.getString("productId"), document.getString("id"), document.getId());
            String name = firstNotEmpty(document.getString("name"), document.getString("productName"));
            String imageUrl = firstNotEmpty(document.getString("imageUrl"), document.getString("image"));
            long price = longValue(document.get("price"));
            int quantity = (int) Math.max(1L, longValue(document.get("quantity")));
            double rating = doubleValue(document.get("rating"));
            long soldCount = longValue(document.get("soldCount"));
            items.add(new SavedProductItem(productId, name, imageUrl, price, quantity, rating, soldCount));
        }
        return items;
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (Exception ignored) {
            }
        }
        return 0.0;
    }

    private static long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private static String firstNotEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static void deliverItems(Context context, String localKey, List<SavedProductItem> items,
                                     String remoteCollection, @NonNull ItemsCallback callback) {
        if (!COLLECTION_WISHLIST.equals(remoteCollection)) {
            callback.onLoaded(items);
            return;
        }
        enrichWishlistItems(context, localKey, items, callback);
    }

    private static void enrichWishlistItems(Context context, String localKey, List<SavedProductItem> items,
                                            @NonNull ItemsCallback callback) {
        if (items == null || items.isEmpty()) {
            callback.onLoaded(items);
            return;
        }

        boolean needsRefresh = false;
        for (SavedProductItem item : items) {
            if (item != null && needsProductMetaRefresh(item)) {
                needsRefresh = true;
                break;
            }
        }

        if (!needsRefresh) {
            callback.onLoaded(items);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<com.google.android.gms.tasks.Task<?>> tasks = new ArrayList<>();
        for (SavedProductItem item : items) {
            if (item == null || TextUtils.isEmpty(item.getProductId()) || !needsProductMetaRefresh(item)) {
                continue;
            }
            tasks.add(db.collection("products").document(item.getProductId()).get().continueWithTask(task -> {
                com.google.firebase.firestore.DocumentSnapshot document = task.isSuccessful() ? task.getResult() : null;
                if (document != null && document.exists()) {
                    applyProductMeta(item, document);
                    return Tasks.forResult(null);
                }
                return db.collection("products").whereEqualTo("id", item.getProductId()).limit(1).get().continueWith(fallbackTask -> {
                    if (fallbackTask.isSuccessful() && fallbackTask.getResult() != null && !fallbackTask.getResult().isEmpty()) {
                        applyProductMeta(item, fallbackTask.getResult().getDocuments().get(0));
                    }
                    return null;
                });
            }));
        }

        if (tasks.isEmpty()) {
            callback.onLoaded(items);
            return;
        }

        final String requestUid = FirebaseAuth.getInstance().getUid();
        Tasks.whenAllComplete(tasks)
                .addOnCompleteListener(task -> {
                    if (!java.util.Objects.equals(requestUid, FirebaseAuth.getInstance().getUid())) return;
                    saveLocal(context, localKey, items);
                    callback.onLoaded(items);
                });
    }

    private static boolean needsProductMetaRefresh(SavedProductItem item) {
        if (item == null) {
            return false;
        }
        return item.getRating() <= 0.0 || item.getSoldCount() <= 0L;
    }

    private static void applyProductMeta(SavedProductItem item, com.google.firebase.firestore.DocumentSnapshot document) {
        if (item == null || document == null || !document.exists()) {
            return;
        }
        item.setRating(doubleValue(document.get("rating")));
        item.setSoldCount(longValue(document.get("soldCount")));
    }

    private static synchronized void saveCartItems(Context context, List<SavedProductItem> items) {
        prefs(context);
        cachedCartItems = copyOf(items);
        prefs(context).edit().putString(KEY_CART, writeItems(items).toString()).apply();
    }

    private static synchronized void saveWishlistItems(Context context, List<SavedProductItem> items) {
        prefs(context);
        cachedWishlistItems = copyOf(items);
        prefs(context).edit().putString(KEY_WISHLIST, writeItems(items).toString()).apply();
    }

    private static synchronized void saveLocal(Context context, String key, List<SavedProductItem> items) {
        prefs(context);
        if (KEY_CART.equals(key)) {
            cachedCartItems = copyOf(items);
        } else if (KEY_WISHLIST.equals(key)) {
            cachedWishlistItems = copyOf(items);
        }
        prefs(context).edit().putString(key, writeItems(items).toString()).apply();
    }

    private static JSONArray writeItems(List<SavedProductItem> items) {
        JSONArray array = new JSONArray();
        for (SavedProductItem item : items) {
            try {
                JSONObject object = new JSONObject();
                object.put("productId", value(item.getProductId()));
                object.put("name", value(item.getName()));
                object.put("imageUrl", value(item.getImageUrl()));
                object.put("price", item.getPrice());
                object.put("quantity", Math.max(1, item.getQuantity()));
                object.put("rating", item.getRating());
                object.put("soldCount", item.getSoldCount());
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        return array;
    }

    private static List<SavedProductItem> readItems(String rawJson) {
        List<SavedProductItem> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(rawJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                items.add(new SavedProductItem(
                        object.optString("productId"),
                        object.optString("name"),
                        object.optString("imageUrl"),
                        object.optLong("price", 0L),
                        Math.max(1, object.optInt("quantity", 1)),
                        object.optDouble("rating", 0.0),
                        object.optLong("soldCount", 0L)
                ));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private static List<SavedProductItem> copyOf(List<SavedProductItem> source) {
        List<SavedProductItem> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (SavedProductItem item : source) {
            if (item == null) {
                continue;
            }
            copy.add(new SavedProductItem(
                    value(item.getProductId()),
                    value(item.getName()),
                    value(item.getImageUrl()),
                    item.getPrice(),
                    Math.max(1, item.getQuantity()),
                    item.getRating(),
                    item.getSoldCount()
            ));
        }
        return copy;
    }

    private static boolean same(String first, String second) {
        return value(first).equalsIgnoreCase(value(second));
    }

    private static String value(String text) {
        return text != null ? text : "";
    }
}
