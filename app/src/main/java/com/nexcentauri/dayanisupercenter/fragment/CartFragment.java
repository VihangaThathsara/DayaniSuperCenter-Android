package com.nexcentauri.dayanisupercenter.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.activity.CheckoutActivity;
import com.nexcentauri.dayanisupercenter.adapter.CartAdapter;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;
import com.nexcentauri.dayanisupercenter.util.CartWishlistStorage;
import com.nexcentauri.dayanisupercenter.util.ProductStockService;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;

import java.text.NumberFormat;
import java.util.Locale;

public class CartFragment extends Fragment {

    private static final long REFRESH_THROTTLE_MS = 1_500L;
    private final NumberFormat cartNumberFormat = NumberFormat.getNumberInstance(Locale.US);
    private RecyclerView recyclerCartTab;
    private View emptyCartTab;
    private View checkoutBarTab;
    private TextView txtTotalPriceTab;
    private CartAdapter cartTabAdapter;
    private boolean cartChangePending;
    private boolean checkingStock;
    private long lastBoundAtMs = 0L;
    private boolean firstLoadDone = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_cart_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerCartTab = view.findViewById(R.id.recyclerCartTab);
        emptyCartTab = view.findViewById(R.id.layoutEmptyCartTab);
        checkoutBarTab = view.findViewById(R.id.layoutCheckoutBarTab);
        txtTotalPriceTab = view.findViewById(R.id.txtTotalPriceTab);

        recyclerCartTab.setLayoutManager(new LinearLayoutManager(requireContext()));
        cartTabAdapter = new CartAdapter(new CartAdapter.Listener() {
            @Override
            public void onIncrease(SavedProductItem item) {
                changeQuantity(item, item.getQuantity() == Integer.MAX_VALUE ? Integer.MAX_VALUE : item.getQuantity() + 1);
            }

            @Override
            public void onDecrease(SavedProductItem item) {
                int nextQuantity = item.getQuantity() - 1;
                if (nextQuantity <= 0) {
                    showCartRemoveDialog(item);
                } else {
                    changeQuantity(item, nextQuantity);
                }
            }

            @Override
            public void onRemove(SavedProductItem item) {
                showCartRemoveDialog(item);
            }
        });
        recyclerCartTab.setAdapter(cartTabAdapter);

        View btnCheckout = view.findViewById(R.id.btnCheckoutTab);
        if (btnCheckout != null) {
            btnCheckout.setOnClickListener(this::openCheckout);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (!firstLoadDone || now - lastBoundAtMs >= REFRESH_THROTTLE_MS) {
            bindCartTabData();
        }
    }

    private void bindCartTabData() {
        lastBoundAtMs = System.currentTimeMillis();
        CartWishlistStorage.getCartItems(requireContext(), items -> {
            if (!isAdded() || cartTabAdapter == null) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                firstLoadDone = true;
                cartTabAdapter.submitList(items);
                boolean isEmpty = items.isEmpty();
                if (emptyCartTab != null) emptyCartTab.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                if (recyclerCartTab != null) recyclerCartTab.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                if (checkoutBarTab != null) checkoutBarTab.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

                long totalPrice = 0L;
                for (SavedProductItem item : items) {
                    totalPrice += item.getPrice() * Math.max(1, item.getQuantity());
                }
                if (txtTotalPriceTab != null) {
                    txtTotalPriceTab.setText("Rs. " + cartNumberFormat.format(totalPrice));
                }
            });
        });
    }

    private void changeQuantity(SavedProductItem item, int quantity) {
        if (cartChangePending || checkingStock) return;
        cartChangePending = true;
        CartWishlistStorage.updateCartQuantity(requireContext(), item.getProductId(), quantity, new CartWishlistStorage.MutationCallback() {
            @Override public void onSaved() {
                cartChangePending = false;
                if (!isAdded() || getView() == null) return;
                bindCartTabData();
            }
            @Override public void onError(String message) {
                cartChangePending = false;
                if (!isAdded() || getView() == null) return;
                AppMessageUtil.show(requireActivity(), message, AppMessageUtil.TYPE_ERROR);
            }
        });
    }

    private void openCheckout(View button) {
        if (cartChangePending || checkingStock) return;
        checkingStock = true;
        button.setEnabled(false);
        ProductStockService.validateCart(CartWishlistStorage.getCartItems(requireContext()))
                .addOnSuccessListener(unused -> {
                    checkingStock = false;
                    if (!isAdded() || getView() == null) return;
                    button.setEnabled(true);
                    startActivity(new Intent(requireContext(), CheckoutActivity.class));
                }).addOnFailureListener(error -> {
                    checkingStock = false;
                    if (!isAdded() || getView() == null) return;
                    button.setEnabled(true);
                    AppMessageUtil.show(requireActivity(), ProductStockService.errorMessage(error), AppMessageUtil.TYPE_ERROR);
                });
    }

    private void showCartRemoveDialog(SavedProductItem item) {
        if (cartChangePending || checkingStock) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_remove_product, null);
        dialog.setContentView(view);

        android.widget.ImageView imgProduct = view.findViewById(R.id.imgProduct);
        TextView txtName = view.findViewById(R.id.txtProductName);
        TextView txtPrice = view.findViewById(R.id.txtProductPrice);
        com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader.loadInto(imgProduct, item.getImageUrl(), R.drawable.logo_clean);
        txtName.setText(item.getName());
        txtPrice.setText("Rs. " + cartNumberFormat.format(item.getPrice()));

        view.findViewById(R.id.btnCancelRemove).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirmRemove).setOnClickListener(v -> {
            if (cartChangePending || checkingStock) return;
            CartWishlistStorage.removeCartItem(requireContext(), item.getProductId());
            dialog.dismiss();
            bindCartTabData();
        });
        dialog.show();
    }
}
