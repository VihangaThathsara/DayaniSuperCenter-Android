package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.adapter.CartAdapter;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;
import com.nexcentauri.dayanisupercenter.util.CartWishlistStorage;
import com.nexcentauri.dayanisupercenter.util.ProductStockService;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CartActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View emptyState;
    private View checkoutBar;
    private TextView txtTotalPrice;
    private CartAdapter adapter;
    private boolean cartChangePending;
    private boolean checkingStock;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cart);

        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.applyWindowInsets(this);

        recyclerView = findViewById(R.id.recyclerCart);
        emptyState = findViewById(R.id.layoutEmptyCart);
        checkoutBar = findViewById(R.id.layoutCheckoutBar);
        txtTotalPrice = findViewById(R.id.txtTotalPrice);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartAdapter(new CartAdapter.Listener() {
            @Override
            public void onIncrease(SavedProductItem item) {
                changeQuantity(item, item.getQuantity() == Integer.MAX_VALUE ? Integer.MAX_VALUE : item.getQuantity() + 1);
            }

            @Override
            public void onDecrease(SavedProductItem item) {
                int nextQuantity = item.getQuantity() - 1;
                if (nextQuantity <= 0) {
                    showRemoveDialog(item);
                } else {
                    changeQuantity(item, nextQuantity);
                }
            }

            @Override
            public void onRemove(SavedProductItem item) {
                showRemoveDialog(item);
            }
        });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnCheckout).setOnClickListener(this::openCheckout);

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindData();
    }

    private void bindData() {
        CartWishlistStorage.getCartItems(this, items -> runOnUiThread(() -> {
            adapter.submitList(items);
            boolean isEmpty = items.isEmpty();
            emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            checkoutBar.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

            long totalPrice = 0L;
            for (SavedProductItem item : items) {
                totalPrice += item.getPrice() * Math.max(1, item.getQuantity());
            }
            txtTotalPrice.setText("Rs. " + numberFormat.format(totalPrice));
        }));
    }

    private void changeQuantity(SavedProductItem item, int quantity) {
        if (cartChangePending || checkingStock) return;
        cartChangePending = true;
        CartWishlistStorage.updateCartQuantity(this, item.getProductId(), quantity, new CartWishlistStorage.MutationCallback() {
            @Override public void onSaved() {
                cartChangePending = false;
                if (isFinishing() || isDestroyed()) return;
                bindData();
            }
            @Override public void onError(String message) {
                cartChangePending = false;
                if (isFinishing() || isDestroyed()) return;
                AppMessageUtil.show(CartActivity.this, message, AppMessageUtil.TYPE_ERROR);
            }
        });
    }

    private void openCheckout(View button) {
        if (cartChangePending || checkingStock) return;
        checkingStock = true;
        button.setEnabled(false);
        ProductStockService.validateCart(CartWishlistStorage.getCartItems(this))
                .addOnSuccessListener(unused -> {
                    checkingStock = false;
                    if (isFinishing() || isDestroyed()) return;
                    button.setEnabled(true);
                    startActivity(new Intent(this, CheckoutActivity.class));
                }).addOnFailureListener(error -> {
                    checkingStock = false;
                    if (isFinishing() || isDestroyed()) return;
                    button.setEnabled(true);
                    AppMessageUtil.show(CartActivity.this, ProductStockService.errorMessage(error), AppMessageUtil.TYPE_ERROR);
                });
    }

    private void setupBottomNav() {
        findViewById(R.id.navHomeItem).setOnClickListener(v -> finish());
        findViewById(R.id.navCartItem).setOnClickListener(v -> {
        });
        findViewById(R.id.navOrdersItem).setOnClickListener(v -> Toast.makeText(this, "Orders screen coming next.", Toast.LENGTH_SHORT).show());
        findViewById(R.id.navProfileItem).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("open_profile_tab", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void showRemoveDialog(SavedProductItem item) {
        if (cartChangePending || checkingStock) return;
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_remove_product, null);
        dialog.setContentView(view);

        android.widget.ImageView imgProduct = view.findViewById(R.id.imgProduct);
        TextView txtName = view.findViewById(R.id.txtProductName);
        TextView txtPrice = view.findViewById(R.id.txtProductPrice);
        com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader.loadInto(imgProduct, item.getImageUrl(), R.drawable.logo_clean);
        txtName.setText(item.getName());
        txtPrice.setText("Rs. " + numberFormat.format(item.getPrice()));

        view.findViewById(R.id.btnCancelRemove).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirmRemove).setOnClickListener(v -> {
            if (cartChangePending || checkingStock) return;
            CartWishlistStorage.removeCartItem(CartActivity.this, item.getProductId());
            dialog.dismiss();
            bindData();
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
