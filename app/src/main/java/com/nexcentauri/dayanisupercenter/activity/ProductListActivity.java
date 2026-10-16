package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.QuerySnapshot;
import com.nexcentauri.dayanisupercenter.util.HomeDataCache;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.ProductSnapshotCache;
import com.nexcentauri.dayanisupercenter.adapter.HomeProductAdapter;
import com.nexcentauri.dayanisupercenter.model.HomeProduct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductListActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY_ID = "extra_category_id";
    public static final String EXTRA_CATEGORY_NAME = "extra_category_name";

    private RecyclerView recyclerProducts;
    private HomeProductAdapter productAdapter;
    private final List<HomeProduct> productList = new ArrayList<>();
    private FirebaseFirestore db;
    private String categoryId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_list);

        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.applyWindowInsets(this);

        db = FirebaseFirestore.getInstance();
        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        String categoryName = getIntent().getStringExtra(EXTRA_CATEGORY_NAME);

        ImageView btnBack = findViewById(R.id.btnBack);
        TextView txtTitle = findViewById(R.id.txtToolbarTitle);
        recyclerProducts = findViewById(R.id.recyclerCategoryProducts);

        txtTitle.setText(categoryName != null && !categoryName.trim().isEmpty() ? categoryName : "Products");
        btnBack.setOnClickListener(v -> finish());

        recyclerProducts.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerProducts.setHasFixedSize(true);
        recyclerProducts.setItemAnimator(null);
        if (recyclerProducts.getItemDecorationCount() == 0) {
            recyclerProducts.addItemDecoration(new SpaceItemDecoration(14, 18));
        }
        productAdapter = new HomeProductAdapter(productList, product -> {
            Intent intent = new Intent(ProductListActivity.this, SingleProductActivity.class);
            intent.putExtra(SingleProductActivity.EXTRA_PRODUCT_ID, product.getId());
            startActivity(intent);
        });
        recyclerProducts.setAdapter(productAdapter);

        loadProducts();
    }

    private void loadProducts() {
        HomeDataCache.initialize(this);
        for (HomeProduct product : HomeDataCache.getProducts()) {
            if (product.isActive() && matchesCategory(product.getCategoryId())) productList.add(product);
        }
        productAdapter.updateProducts(productList);
        final boolean[] freshReceived = {false};
        if (productList.isEmpty()) {
            db.collection("products").get(Source.CACHE).addOnSuccessListener(snapshot -> {
                if (!isFinishing() && !isDestroyed() && !freshReceived[0] && !snapshot.isEmpty()) renderProducts(snapshot);
            });
        }
        db.collection("products").get().addOnSuccessListener(snapshot -> {
            if (isFinishing() || isDestroyed()) return;
            freshReceived[0] = true;
            renderProducts(snapshot);
        }).addOnFailureListener(e -> {
            if (!isFinishing() && !isDestroyed() && productList.isEmpty())
                Toast.makeText(this, "Failed to load products.", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean matchesCategory(String productCategory) {
        return "all".equalsIgnoreCase(value(categoryId)) || value(productCategory).equalsIgnoreCase(value(categoryId));
    }

    private void renderProducts(QuerySnapshot snapshot) {
        ProductSnapshotCache.replace(snapshot);
        List<HomeProduct> freshProducts = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            if (Boolean.FALSE.equals(document.getBoolean("active"))) continue;
            String docCategoryId = value(document.getString("categoryId"));
            if (!matchesCategory(docCategoryId)) continue;
            freshProducts.add(new HomeProduct(
                    document.getString("id") != null ? document.getString("id") : document.getId(),
                    value(document.getString("name")), getDouble(document, "rating"),
                    (int) getLong(document, "soldCount"), (int) getLong(document, "price"),
                    value(document.getString("imageUrl")), docCategoryId,
                    value(document.getString("categoryName")),
                    Boolean.TRUE.equals(document.getBoolean("mostPopular")), true));
        }
        retainDisplayedOrder(freshProducts);
        productList.clear();
        productList.addAll(freshProducts);
        productAdapter.updateProducts(productList);
    }

    private void retainDisplayedOrder(List<HomeProduct> freshProducts) {
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < productList.size(); i++) {
            positions.put(productList.get(i).getId(), i);
        }
        freshProducts.sort((first, second) -> Integer.compare(
                positions.getOrDefault(first.getId(), Integer.MAX_VALUE),
                positions.getOrDefault(second.getId(), Integer.MAX_VALUE)));
    }

    private long getLong(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value != null ? value : 0L;
    }

    private double getDouble(DocumentSnapshot document, String field) {
        Double value = document.getDouble(field);
        return value != null ? value : 0.0;
    }

    private String value(String text) {
        return text != null ? text : "";
    }

    private static class SpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int horizontal;
        private final int vertical;

        SpaceItemDecoration(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            outRect.bottom = vertical;
            if (position % 2 == 0) {
                outRect.right = horizontal / 2;
            } else {
                outRect.left = horizontal / 2;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (productAdapter != null) {
            productAdapter.refreshWishlist(this);
        }
    }
}
