package com.nexcentauri.dayanisupercenter.activity;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.util.ProductSnapshotCache;
import com.nexcentauri.dayanisupercenter.util.ProductStockService;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.CartWishlistStorage;
import com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader;

import java.text.NumberFormat;
import java.util.Locale;

public class SingleProductActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "extra_product_id";

    private ImageView imgProduct;
    private TextView txtProductName;
    private TextView txtProductPrice;
    private TextView txtProductRating;
    private TextView txtProductSold;
    private TextView txtProductDescription;
    private TextView txtQuantity;
    private TextView txtProductStock;
    private long availableStock;
    private boolean addingToCart;
    private ImageView btnWishlist;
    private TextView btnAddToCart;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
    private long unitPrice = 0L;
    private int quantity = 1;
    private String currentProductId = "";
    private String currentProductName = "";
    private String currentImageUrl = "";
    private double currentRating = 0.0;
    private long currentSoldCount = 0L;
    private boolean productBound;
    private boolean serverProductReceived;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_single_product);

        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.applyWindowInsets(this);

        ImageView btnBack = findViewById(R.id.btnBack);
        imgProduct = findViewById(R.id.imgProduct);
        txtProductName = findViewById(R.id.txtProductName);
        txtProductPrice = findViewById(R.id.txtProductPrice);
        txtProductRating = findViewById(R.id.txtProductRating);
        txtProductSold = findViewById(R.id.txtProductSold);
        txtProductDescription = findViewById(R.id.txtProductDescription);
        txtQuantity = findViewById(R.id.txtQuantity);
        txtProductStock = findViewById(R.id.txtProductStock);
        btnWishlist = findViewById(R.id.btnWishlist);
        btnAddToCart = findViewById(R.id.btnAddToCart);
        TextView btnMinus = findViewById(R.id.btnMinus);
        TextView btnPlus = findViewById(R.id.btnPlus);

        btnBack.setOnClickListener(v -> finish());
        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                updatePriceAndQuantity();
            }
        });
        btnPlus.setOnClickListener(v -> {
            if (quantity < availableStock && quantity < Integer.MAX_VALUE) {
                quantity++;
                updatePriceAndQuantity();
            } else {
                AppMessageUtil.show(this, availableStock <= 0 ? "This product is out of stock."
                        : "Only " + availableStock + " items are in stock.", AppMessageUtil.TYPE_ERROR);
            }
        });

        String productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        currentProductId = productId != null ? productId : "";
        if (productId == null || productId.trim().isEmpty()) {
            Toast.makeText(this, "Product not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnWishlist.setOnClickListener(v -> toggleWishlist());
        btnAddToCart.setOnClickListener(v -> addCurrentProductToCart());

        btnAddToCart.setEnabled(false);
        btnWishlist.setEnabled(false);
        loadProduct(productId);
    }

    private void loadProduct(String productId) {
        DocumentSnapshot cached = ProductSnapshotCache.get(productId);
        if (cached != null) bindProductSnapshot(cached);
        com.google.firebase.firestore.DocumentReference product = FirebaseFirestore.getInstance().collection("products").document(productId);
        product.get(Source.CACHE).addOnSuccessListener(snapshot -> {
            if (!serverProductReceived && snapshot.exists() && !isFinishing() && !isDestroyed()) bindProductSnapshot(snapshot);
        });
        product.get().addOnSuccessListener(snapshot -> {
            if (isFinishing() || isDestroyed()) return;
            serverProductReceived = true;
            if (snapshot.exists()) bindProductSnapshot(snapshot);
            else loadProductByFieldId(productId);
        }).addOnFailureListener(error -> {
            if (!productBound && !isFinishing() && !isDestroyed()) loadProductByFieldId(productId);
        });
    }

    private void bindProductSnapshot(DocumentSnapshot snapshot) {
        ProductSnapshotCache.put(snapshot);
        availableStock = ProductStockService.availableStock(snapshot);
        quantity = availableStock <= 0 ? 1 : (int) Math.min(Math.max(1, quantity), availableStock);
        bindProduct(value(snapshot.getString("name")), value(snapshot.getString("imageUrl")),
                value(snapshot.getString("description")), getDouble(snapshot.getDouble("rating")),
                getLong(snapshot.getLong("soldCount")), getLong(snapshot.getLong("price")));
    }

    private void loadProductByFieldId(String productId) {
        FirebaseFirestore.getInstance().collection("products")
                .whereEqualTo("id", productId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "Product not found.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    com.google.firebase.firestore.DocumentSnapshot documentSnapshot = queryDocumentSnapshots.getDocuments().get(0);
                    if (!isFinishing() && !isDestroyed()) bindProductSnapshot(documentSnapshot);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load product.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bindProduct(String name, String imageUrl, String description, double rating, long soldCount, long price) {
        productBound = true;
        btnAddToCart.setEnabled(availableStock > 0 && !addingToCart);
        btnAddToCart.setAlpha(availableStock > 0 ? 1f : 0.45f);
        btnAddToCart.setText(availableStock > 0 ? "Add to Cart" : "Out of stock");
        txtProductStock.setText(availableStock > 0 ? numberFormat.format(availableStock) + " in stock" : "Out of stock");
        btnWishlist.setEnabled(true);
        currentProductName = name.isEmpty() ? "Product" : name;
        currentImageUrl = imageUrl;
        txtProductName.setText(currentProductName);
        txtProductRating.setText(rating > 0 ? String.format(Locale.US, "%.1f", rating) : "Not rated");
        txtProductSold.setText(numberFormat.format(soldCount) + " sold");
        txtProductDescription.setText(description.isEmpty() ? "No description available for this product yet." : description);
        currentRating = rating;
        currentSoldCount = soldCount;
        unitPrice = price;
        updatePriceAndQuantity();
        FirebaseImageLoader.loadInto(imgProduct, imageUrl, R.drawable.logo_clean);
        updateWishlistIcon();
    }

    private void updatePriceAndQuantity() {
        txtQuantity.setText(String.valueOf(quantity));
        long totalPrice = unitPrice * quantity;
        txtProductPrice.setText("Rs. " + numberFormat.format(totalPrice));
    }

    private void addCurrentProductToCart() {
        if (currentProductId.trim().isEmpty()) {
            return;
        }
        if (addingToCart || availableStock <= 0) return;
        addingToCart = true;
        btnAddToCart.setEnabled(false);
        CartWishlistStorage.addToCart(this, buildSavedItem(), new CartWishlistStorage.MutationCallback() {
            private void restoreButton() {
                addingToCart = false;
                btnAddToCart.setEnabled(availableStock > 0);
            }
            @Override public void onSaved() {
                if (isFinishing() || isDestroyed()) return;
                restoreButton();
                AppMessageUtil.show(SingleProductActivity.this, "Product added to cart successfully.", AppMessageUtil.TYPE_SUCCESS);
            }
            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                restoreButton();
                AppMessageUtil.show(SingleProductActivity.this, message, AppMessageUtil.TYPE_ERROR);
                loadProduct(currentProductId);
            }
        });
    }

    private void toggleWishlist() {
        if (currentProductId.trim().isEmpty()) {
            return;
        }
        boolean wasSaved = CartWishlistStorage.isInWishlist(this, currentProductId);
        CartWishlistStorage.toggleWishlist(this, buildSavedItem());
        updateWishlistIcon();
        if (!wasSaved) AppMessageUtil.show(this, "Product added to wishlist successfully.", AppMessageUtil.TYPE_SUCCESS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWishlistIcon();
    }

    private void updateWishlistIcon() {
        if (btnWishlist == null) {
            return;
        }
        boolean inWishlist = CartWishlistStorage.isInWishlist(this, currentProductId);
        btnWishlist.setImageResource(inWishlist ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        btnWishlist.setBackgroundResource(inWishlist ? R.drawable.bg_product_wish_circle_selected : R.drawable.bg_product_wish_circle_unselected);
        btnWishlist.setColorFilter(getColor(android.R.color.white));
        btnWishlist.setAlpha(1f);
    }

    @NonNull
    private SavedProductItem buildSavedItem() {
        return new SavedProductItem(currentProductId, currentProductName, currentImageUrl, unitPrice, quantity, currentRating, currentSoldCount);
    }

    private String value(String text) {
        return text != null ? text : "";
    }

    private long getLong(Long value) {
        return value != null ? value : 0L;
    }

    private double getDouble(Double value) {
        return value != null ? value : 0.0;
    }
}
