package com.nexcentauri.dayanisupercenter.activity;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.checkout.CheckoutMath;
import com.nexcentauri.dayanisupercenter.checkout.CheckoutPrefs;
import com.nexcentauri.dayanisupercenter.checkout.LocalNotificationHelper;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.CartWishlistStorage;
import com.nexcentauri.dayanisupercenter.util.PayHereCheckoutHelper;
import com.nexcentauri.dayanisupercenter.util.ProductStockService;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import lk.payhere.androidsdk.PHConstants;

public class PayHereGatewayActivity extends AppCompatActivity {

    private FirebaseUser currentUser;
    private List<SavedProductItem> cartItems = new ArrayList<>();
    private long pendingTotal;
    private String pendingOrderId;
    private boolean checkoutStarted = false;
    private Dialog successDialog;
    private boolean orderSaving;
    private boolean orderSaved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payhere_gateway);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        SystemBarUtil.matchStatusBarToContent(this);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        CartWishlistStorage.getCartItems(this, items -> runOnUiThread(() -> {
            cartItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
            if (!checkoutStarted) {
                startPayHereCheckout();
            }
        }));
    }

    private void startPayHereCheckout() {
        if (checkoutStarted) return;
        checkoutStarted = true;

        if (cartItems == null || cartItems.isEmpty()) {
            AppMessageUtil.show(this, "Your cart is empty.", AppMessageUtil.TYPE_ERROR);
            finish();
            return;
        }

        setGatewayLoading(true, "Checking stock...");
        ProductStockService.validateCart(cartItems).addOnSuccessListener(unused -> {
            if (isFinishing() || isDestroyed()) return;
            if (!currentUser.getUid().equals(FirebaseAuth.getInstance().getUid())) { finish(); return; }
            setGatewayLoading(true, "Opening secure payment...");
            openPayHere();
        }).addOnFailureListener(error -> {
            if (isFinishing() || isDestroyed()) return;
            setGatewayLoading(false, "");
            AppMessageUtil.show(this, ProductStockService.errorMessage(error), AppMessageUtil.TYPE_ERROR);
            finish();
        });
    }

    private void openPayHere() {
        long subtotal = 0L;
        for (SavedProductItem item : cartItems) subtotal += item.getPrice() * Math.max(1, item.getQuantity());
        long shipping = CheckoutMath.calculateShippingFee(
                CheckoutPrefs.getSelectedShippingType(this),
                CheckoutPrefs.getSelectedAddressLat(this),
                CheckoutPrefs.getSelectedAddressLng(this));
        pendingTotal = subtotal + shipping;
        pendingOrderId = "DSC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);

        String displayName = currentUser.getDisplayName() == null ? "Dayani User" : currentUser.getDisplayName();
        String[] parts = displayName.trim().split(" ", 2);
        String first = parts.length > 0 ? parts[0] : "Dayani";
        String last = parts.length > 1 ? parts[1] : "Customer";
        PayHereCheckoutHelper.startSandboxCheckout(
                this,
                pendingOrderId,
                pendingTotal,
                "Dayani Super Center Order",
                first,
                last,
                currentUser.getEmail() == null ? "customer@example.com" : currentUser.getEmail(),
                "0770000000",
                CheckoutPrefs.getSelectedAddressDetails(this),
                CheckoutPrefs.SHIPPING_STORE_PICKUP.equals(CheckoutPrefs.getSelectedShippingType(this)) ? "Store Pickup" : "Colombo",
                "Sri Lanka"
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PayHereCheckoutHelper.PAYHERE_REQUEST) {
            return;
        }

        if (data == null || !data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
            finish();
            return;
        }

        Object response = data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);
        boolean success = false;
        if (resultCode == RESULT_OK && response != null) {
            try {
                success = (Boolean) response.getClass().getMethod("isSuccess").invoke(response);
            } catch (Exception ignored) {
                success = false;
            }
        }

        if (success) {
            saveOrderAndShowSuccess();
        } else {
            AppMessageUtil.show(this, "Payment was not completed.", AppMessageUtil.TYPE_ERROR);
            finish();
        }
    }

    private void saveOrderAndShowSuccess() {
        if (orderSaving || orderSaved) return;
        orderSaving = true;
        setGatewayLoading(true, "Saving your order...");
        long subtotal = 0L;
        List<Map<String, Object>> itemsPayload = new ArrayList<>();
        for (SavedProductItem item : cartItems) {
            subtotal += item.getPrice() * Math.max(1, item.getQuantity());
            Map<String, Object> map = new HashMap<>();
            map.put("productId", item.getProductId());
            map.put("name", item.getName());
            map.put("imageUrl", item.getImageUrl());
            map.put("price", item.getPrice());
            map.put("quantity", Math.max(1, item.getQuantity()));
            itemsPayload.add(map);
        }
        long shipping = CheckoutMath.calculateShippingFee(
                CheckoutPrefs.getSelectedShippingType(this),
                CheckoutPrefs.getSelectedAddressLat(this),
                CheckoutPrefs.getSelectedAddressLng(this));

        Map<String, Object> order = new HashMap<>();
        order.put("orderId", pendingOrderId);
        order.put("userId", currentUser.getUid());
        order.put("status", "pending");
        order.put("createdAt", FieldValue.serverTimestamp());
        order.put("createdAtClient", Timestamp.now());
        order.put("subtotal", subtotal);
        order.put("shippingFee", shipping);
        order.put("total", pendingTotal);
        order.put("shippingType", CheckoutPrefs.getSelectedShippingType(this));
        order.put("paymentMethod", "PayHere");
        order.put("addressLabel", CheckoutPrefs.getSelectedAddressLabel(this));
        order.put("addressDetails", CheckoutPrefs.getSelectedAddressDetails(this));
        order.put("items", itemsPayload);

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", "Order placed successfully!");
        notification.put("message", "Your order " + pendingOrderId + " has been placed successfully.");
        notification.put("type", "order");
        notification.put("orderId", pendingOrderId);
        notification.put("read", false);
        notification.put("createdAt", FieldValue.serverTimestamp());
        notification.put("createdAtClient", Timestamp.now());

        Map<String, Object> latestNotification = new HashMap<>();
        latestNotification.put("latestNotificationTitle", notification.get("title"));
        latestNotification.put("latestNotificationMessage", notification.get("message"));
        latestNotification.put("latestNotificationType", notification.get("type"));
        latestNotification.put("latestNotificationOrderId", pendingOrderId);
        latestNotification.put("latestNotificationAtClient", Timestamp.now());
        latestNotification.put("hasUnreadNotifications", true);

        ProductStockService.saveOrder(currentUser.getUid(), pendingOrderId, cartItems,
                order, notification, latestNotification)
                .addOnSuccessListener(unused -> {
                    orderSaving = false;
                    orderSaved = true;
                    if (!currentUser.getUid().equals(FirebaseAuth.getInstance().getUid())) { finish(); return; }
                    CartWishlistStorage.clearCart(this);
                    CheckoutPrefs.clear(this);
                    LocalNotificationHelper.showOrderPlaced(this, pendingOrderId);
                    if (!isFinishing() && !isDestroyed()) showSuccessDialog();
                }).addOnFailureListener(error -> {
                    orderSaving = false;
                    if (isFinishing() || isDestroyed()) return;
                    setGatewayLoading(false, "Payment completed. Your order could not be saved.\nOrder ID: " + pendingOrderId);
                    AppMessageUtil.show(this, valueOrDefault(error.getMessage(), "Order saving failed."), AppMessageUtil.TYPE_ERROR);
                });
    }

    private void setGatewayLoading(boolean loading, String message) {
        findViewById(R.id.progressPayment).setVisibility(loading ? View.VISIBLE : View.GONE);
        TextView status = findViewById(R.id.txtPaymentStatus);
        status.setText(message);
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onPause() {
        if (checkoutStarted && !orderSaving) setGatewayLoading(false, "");
        super.onPause();
    }

    private void showSuccessDialog() {
        setGatewayLoading(false, "");
        successDialog = new Dialog(this);
        successDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        successDialog.setContentView(R.layout.dialog_order_success);
        successDialog.setCancelable(false);
        if (successDialog.getWindow() != null) {
            successDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        TextView txtOrderId = successDialog.findViewById(R.id.txtOrderId);
        txtOrderId.setText("Order ID: " + pendingOrderId);
        successDialog.findViewById(R.id.btnViewOrder).setOnClickListener(v -> {
            successDialog.dismiss();
            openOrdersFromSuccess();
        });
        successDialog.findViewById(R.id.btnCloseSuccess).setOnClickListener(v -> {
            successDialog.dismiss();
            navigateHome();
        });
        successDialog.show();
    }

    @Override
    public void onBackPressed() {
        if (successDialog != null && successDialog.isShowing()) {
            navigateHome();
            return;
        }
        super.onBackPressed();
    }

    private void openOrdersFromSuccess() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("open_orders", true);
        if (pendingOrderId != null && !pendingOrderId.trim().isEmpty()) {
            intent.putExtra("open_order_id", pendingOrderId);
        }
        startActivity(intent);
        finish();
    }

    private void navigateHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
