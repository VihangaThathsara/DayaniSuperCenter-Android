package com.nexcentauri.dayanisupercenter.util;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stock decisions use fresh server data; cached product previews are display-only. */
public final class ProductStockService {
    private ProductStockService() { }

    public static long availableStock(DocumentSnapshot product) {
        if (product == null || !product.exists() || Boolean.FALSE.equals(product.getBoolean("active"))) return 0L;
        Object stock = product.get("stock");
        if (!(stock instanceof Number)) stock = product.get("stockCount");
        return stock instanceof Number ? Math.max(0L, ((Number) stock).longValue()) : 0L;
    }

    private static void requireQuantity(DocumentSnapshot product, long quantity) {
        long available = availableStock(product);
        String name = product == null ? "Product" : product.getString("name");
        if (name == null || name.trim().isEmpty()) name = "Product";
        if (available <= 0) throw new IllegalStateException(name + " is out of stock.");
        if (quantity <= 0 || quantity > Integer.MAX_VALUE) throw new IllegalStateException("Please select a valid quantity.");
        if (quantity > available) throw new IllegalStateException("Only " + available + " of " + name + " are in stock. Reduce your cart quantity.");
    }

    private static Task<DocumentSnapshot> resolveProduct(String productId) {
        if (productId == null || productId.trim().isEmpty()) return Tasks.forException(new IllegalStateException("Product not found."));
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return db.collection("products").document(productId).get(Source.SERVER).continueWithTask(task -> {
            if (!task.isSuccessful()) return Tasks.forException(task.getException());
            if (task.getResult().exists()) return Tasks.forResult(task.getResult());
            return db.collection("products").whereEqualTo("id", productId).limit(1).get(Source.SERVER)
                    .continueWithTask(query -> {
                        if (!query.isSuccessful()) return Tasks.forException(query.getException());
                        if (!query.getResult().isEmpty()) return Tasks.forResult(query.getResult().getDocuments().get(0));
                        // Older carts used the product name as their identifier.
                        return db.collection("products").whereEqualTo("name", productId).limit(1).get(Source.SERVER)
                                .continueWith(names -> {
                                    if (!names.isSuccessful()) throw names.getException();
                                    if (names.getResult().isEmpty()) throw new IllegalStateException("Product is no longer available. Remove it from your cart.");
                                    return names.getResult().getDocuments().get(0);
                                });
                    });
        });
    }

    private static Task<List<DocumentSnapshot>> resolveProducts(List<SavedProductItem> items) {
        if (items == null || items.isEmpty()) return Tasks.forException(new IllegalStateException("Your cart is empty."));
        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (SavedProductItem item : items) reads.add(resolveProduct(item.getProductId()));
        return Tasks.whenAllSuccess(reads).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            List<DocumentSnapshot> products = new ArrayList<>();
            for (Object result : task.getResult()) products.add((DocumentSnapshot) result);
            return products;
        });
    }

    public static Task<Void> validateCart(List<SavedProductItem> items) {
        List<SavedProductItem> selected = items == null ? new ArrayList<>() : new ArrayList<>(items);
        return resolveProducts(selected).continueWith(task -> {
            if (!task.isSuccessful()) throw task.getException();
            Map<String, Long> quantities = new HashMap<>();
            for (int i = 0; i < selected.size(); i++) {
                int quantity = selected.get(i).getQuantity();
                if (quantity <= 0) throw new IllegalStateException("Please select a valid quantity.");
                DocumentSnapshot product = task.getResult().get(i);
                String path = product.getReference().getPath();
                long total = quantities.containsKey(path) ? quantities.get(path) + quantity : quantity;
                quantities.put(path, total);
                requireQuantity(product, total);
            }
            return null;
        });
    }

    public static Task<Integer> changeCartQuantity(String uid, SavedProductItem item,
                                                   int requested, boolean add, int localQuantity) {
        if (requested <= 0) return Tasks.forException(new IllegalStateException("Please select a valid quantity."));
        return resolveProduct(item.getProductId()).continueWithTask(lookup -> {
            if (!lookup.isSuccessful()) return Tasks.forException(lookup.getException());
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentReference productRef = lookup.getResult().getReference();
            DocumentReference cartRef = db.collection("users").document(uid).collection("cart").document(item.getProductId());
            return db.runTransaction(transaction -> {
                DocumentSnapshot product = transaction.get(productRef);
                DocumentSnapshot cart = transaction.get(cartRef);
                Object storedQuantity = cart.get("quantity");
                long existing = cart.exists() && storedQuantity instanceof Number
                        ? Math.max(0L, ((Number) storedQuantity).longValue()) : Math.max(0, localQuantity);
                long quantity = add ? existing + requested : requested;
                requireQuantity(product, quantity);
                Map<String, Object> payload = new HashMap<>();
                payload.put("productId", item.getProductId());
                payload.put("id", item.getProductId());
                payload.put("name", item.getName());
                payload.put("productName", item.getName());
                payload.put("imageUrl", item.getImageUrl());
                payload.put("image", item.getImageUrl());
                payload.put("price", item.getPrice());
                payload.put("quantity", quantity);
                payload.put("rating", item.getRating());
                payload.put("soldCount", item.getSoldCount());
                payload.put("userId", uid);
                transaction.set(cartRef, payload);
                return (int) quantity;
            });
        });
    }

    public static Task<Void> saveOrder(String uid, String orderId, List<SavedProductItem> items,
                                       Map<String, Object> order, Map<String, Object> notification,
                                       Map<String, Object> latestNotification) {
        List<SavedProductItem> selected = new ArrayList<>(items);
        return resolveProducts(selected).continueWithTask(lookup -> {
            if (!lookup.isSuccessful()) return Tasks.forException(lookup.getException());
            Map<DocumentReference, Long> quantities = new LinkedHashMap<>();
            for (int i = 0; i < selected.size(); i++) {
                int quantity = selected.get(i).getQuantity();
                if (quantity <= 0) return Tasks.forException(new IllegalStateException("Please select a valid quantity."));
                DocumentReference ref = lookup.getResult().get(i).getReference();
                quantities.put(ref, (quantities.containsKey(ref) ? quantities.get(ref) : 0L) + quantity);
            }
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentReference userRef = db.collection("users").document(uid);
            DocumentReference orderRef = userRef.collection("orders").document(orderId);
            return db.runTransaction(transaction -> {
                // A repeated payment callback must not deduct stock a second time.
                if (transaction.get(orderRef).exists()) return null;
                Map<DocumentReference, Long> remaining = new LinkedHashMap<>();
                for (Map.Entry<DocumentReference, Long> entry : quantities.entrySet()) {
                    DocumentSnapshot product = transaction.get(entry.getKey());
                    requireQuantity(product, entry.getValue());
                    remaining.put(entry.getKey(), availableStock(product) - entry.getValue());
                }
                // Complete every read before writing so a stock change retries the entire order.
                transaction.set(orderRef, order, SetOptions.merge());
                transaction.set(userRef.collection("notifications").document(orderId), notification, SetOptions.merge());
                transaction.set(userRef, latestNotification, SetOptions.merge());
                for (Map.Entry<DocumentReference, Long> entry : remaining.entrySet()) {
                    Map<String, Object> stockUpdates = new HashMap<>();
                    stockUpdates.put("soldCount", FieldValue.increment(quantities.get(entry.getKey())));
                    stockUpdates.put("stock", entry.getValue());
                    stockUpdates.put("stockCount", entry.getValue());
                    transaction.update(entry.getKey(), stockUpdates);
                }
                return null;
            });
        });
    }

    public static String errorMessage(Exception error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof IllegalStateException && cause.getMessage() != null) return cause.getMessage();
            cause = cause.getCause();
        }
        return "Could not verify stock. Check your connection and try again.";
    }
}
