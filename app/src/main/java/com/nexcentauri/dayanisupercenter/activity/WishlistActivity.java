package com.nexcentauri.dayanisupercenter.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.adapter.WishlistAdapter;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;
import com.nexcentauri.dayanisupercenter.util.CartWishlistStorage;


public class WishlistActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View emptyState;
    private WishlistAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_wishlist);

        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.applyWindowInsets(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        recyclerView = findViewById(R.id.recyclerWishlist);
        emptyState = findViewById(R.id.layoutEmptyWishlist);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        if (recyclerView.getItemDecorationCount() == 0) {
            recyclerView.addItemDecoration(new SpaceItemDecoration(14, 18));
        }
        adapter = new WishlistAdapter(this::removeWishlistItem, this::openSingleProduct);
        recyclerView.setAdapter(adapter);
        bindLocalData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindData();
    }

    private void bindLocalData() {
        java.util.List<SavedProductItem> items = CartWishlistStorage.getWishlistItems(this);
        adapter.submitList(items);
        boolean isEmpty = items.isEmpty();
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void bindData() {
        CartWishlistStorage.getWishlistItems(this, items -> runOnUiThread(() -> {
            adapter.submitList(items);
            boolean isEmpty = items.isEmpty();
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }));
    }


    private void openSingleProduct(SavedProductItem item) {
        android.content.Intent intent = new android.content.Intent(this, SingleProductActivity.class);
        intent.putExtra(SingleProductActivity.EXTRA_PRODUCT_ID, item.getProductId());
        startActivity(intent);
    }
    private void removeWishlistItem(SavedProductItem item) {
        if (item == null) {
            return;
        }
        CartWishlistStorage.removeWishlistItem(WishlistActivity.this, item.getProductId());
        bindLocalData();
    }


    private static class SpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int horizontal;
        private final int vertical;

        SpaceItemDecoration(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            outRect.bottom = vertical;
            if (position % 2 == 0) {
                outRect.right = horizontal / 2;
            } else {
                outRect.left = horizontal / 2;
            }
        }
    }
}
