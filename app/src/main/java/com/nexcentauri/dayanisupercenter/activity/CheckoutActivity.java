package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.checkout.CheckoutMath;
import com.nexcentauri.dayanisupercenter.checkout.CheckoutPrefs;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;
import com.nexcentauri.dayanisupercenter.util.CartWishlistStorage;
import com.nexcentauri.dayanisupercenter.util.ProductStockService;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CheckoutActivity extends AppCompatActivity {

    private LinearLayout itemsContainer;
    private View addressCard;
    private TextView txtAddressLabel;
    private TextView txtAddressDetails;
    private TextView txtShippingName;
    private TextView txtShippingSubtitle;
    private TextView txtAmount;
    private TextView txtShippingFee;
    private TextView txtTotal;
    private ImageView imgAddressAction;
    private ImageView imgShippingIcon;
    private final NumberFormat currency = NumberFormat.getNumberInstance(Locale.US);
    private List<SavedProductItem> currentItems = new ArrayList<>();
    private boolean checkingStock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);
        SystemBarUtil.matchStatusBarToContent(this);

        bindViews();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        addressCard.setOnClickListener(v -> startActivity(new Intent(this, CheckoutAddressSelectionActivity.class)));
        findViewById(R.id.cardChooseShipping).setOnClickListener(v -> startActivity(new Intent(this, CheckoutShippingActivity.class)));
        findViewById(R.id.btnContinueToPayment).setOnClickListener(v -> continueToPayment());
    }

    @Override
    protected void onResume() {
        super.onResume();
        resolveSelectedAddress();
        bindCheckoutData();
    }

    private void bindViews() {
        itemsContainer = findViewById(R.id.itemsContainer);
        addressCard = findViewById(R.id.cardAddress);
        txtAddressLabel = findViewById(R.id.txtAddressLabel);
        txtAddressDetails = findViewById(R.id.txtAddressDetails);
        txtShippingName = findViewById(R.id.txtShippingName);
        txtShippingSubtitle = findViewById(R.id.txtShippingSubtitle);
        txtAmount = findViewById(R.id.txtAmount);
        txtShippingFee = findViewById(R.id.txtShippingFee);
        txtTotal = findViewById(R.id.txtTotal);
        imgAddressAction = findViewById(R.id.imgAddressAction);
        imgShippingIcon = findViewById(R.id.imgShippingIcon);
    }

    private void resolveSelectedAddress() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .collection("addresses")
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) docs.add(doc);
                    if (docs.isEmpty()) {
                        CheckoutPrefs.clearSelectedAddress(this);
                        renderAddress();
                        renderShipping();
                        return;
                    }

                    QueryDocumentSnapshot selectedDoc = null;
                    String selectedId = CheckoutPrefs.getSelectedAddressId(this);
                    for (QueryDocumentSnapshot doc : docs) {
                        if (!TextUtils.isEmpty(selectedId) && TextUtils.equals(selectedId, doc.getId())) {
                            selectedDoc = doc;
                            break;
                        }
                    }
                    if (selectedDoc == null) {
                        for (QueryDocumentSnapshot doc : docs) {
                            if (Boolean.TRUE.equals(doc.getBoolean("isDefault"))) {
                                selectedDoc = doc;
                                break;
                            }
                        }
                    }
                    if (selectedDoc == null && docs.size() == 1) {
                        selectedDoc = docs.get(0);
                    }
                    if (selectedDoc == null && !docs.isEmpty()) {
                        selectedDoc = docs.get(0);
                    }
                    if (selectedDoc == null) {
                        CheckoutPrefs.clearSelectedAddress(this);
                    } else {
                        CheckoutPrefs.saveSelectedAddress(
                                this,
                                selectedDoc.getId(),
                                safe(selectedDoc.getString("label"), "Address"),
                                safe(selectedDoc.getString("details"), "Select your address"),
                                value(selectedDoc.getDouble("lat")),
                                value(selectedDoc.getDouble("lng"))
                        );
                    }
                    bindCheckoutData();
                });
    }

    private void bindCheckoutData() {
        renderAddress();
        renderShipping();
        CartWishlistStorage.getCartItems(this, items -> runOnUiThread(() -> {
            currentItems = items;
            renderItems(items);
            renderTotals(items);
        }));
    }

    private void renderAddress() {
        boolean hasAddress = CheckoutPrefs.hasSelectedAddress(this);
        txtAddressLabel.setText(hasAddress ? CheckoutPrefs.getSelectedAddressLabel(this) : "No Address Added");
        txtAddressDetails.setText(hasAddress ? CheckoutPrefs.getSelectedAddressDetails(this) : "Add your shipping address to continue.");
        imgAddressAction.setImageResource(hasAddress ? R.drawable.ic_address_edit_custom_small : R.drawable.ic_chevron_right);
    }

    private void renderShipping() {
        String shippingType = CheckoutPrefs.getSelectedShippingType(this);
        if (CheckoutPrefs.SHIPPING_STORE_PICKUP.equals(shippingType)) {
            imgShippingIcon.setImageResource(R.drawable.ic_store_pickup_custom);
            txtShippingName.setText("Store Pickup");
            txtShippingSubtitle.setText("Pickup from Dayani Super Center");
        } else {
            long fee = CheckoutMath.calculateShippingFee(
                    shippingType,
                    CheckoutPrefs.getSelectedAddressLat(this),
                    CheckoutPrefs.getSelectedAddressLng(this));
            double km = CheckoutMath.calculateDistanceKm(
                    CheckoutPrefs.getSelectedAddressLat(this),
                    CheckoutPrefs.getSelectedAddressLng(this));
            imgShippingIcon.setImageResource(R.drawable.ic_home_delivery_custom);
            txtShippingName.setText("Home Delivery");
            txtShippingSubtitle.setText(CheckoutPrefs.hasSelectedAddress(this)
                    ? "~ " + Math.max(1, Math.round(km)) + " km • Rs. " + currency.format(fee)
                    : "Select an address to calculate delivery fee");
        }
    }

    private void renderItems(List<SavedProductItem> items) {
        itemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (SavedProductItem item : items) {
            View row = inflater.inflate(R.layout.item_checkout_product, itemsContainer, false);
            ImageView imgProduct = row.findViewById(R.id.imgProduct);
            TextView txtName = row.findViewById(R.id.txtName);
            TextView txtPrice = row.findViewById(R.id.txtPrice);
            TextView txtQty = row.findViewById(R.id.txtQty);
            FirebaseImageLoader.loadInto(imgProduct, item.getImageUrl(), R.drawable.logo_clean);
            txtName.setText(item.getName());
            txtPrice.setText("Rs. " + currency.format(item.getPrice()));
            txtQty.setText(String.valueOf(Math.max(1, item.getQuantity())));
            itemsContainer.addView(row);
        }
    }

    private void renderTotals(List<SavedProductItem> items) {
        long subtotal = 0L;
        for (SavedProductItem item : items) {
            subtotal += item.getPrice() * Math.max(1, item.getQuantity());
        }
        long shippingFee = CheckoutMath.calculateShippingFee(
                CheckoutPrefs.getSelectedShippingType(this),
                CheckoutPrefs.getSelectedAddressLat(this),
                CheckoutPrefs.getSelectedAddressLng(this));
        if (!CheckoutPrefs.hasSelectedAddress(this) && CheckoutPrefs.SHIPPING_HOME_DELIVERY.equals(CheckoutPrefs.getSelectedShippingType(this))) {
            shippingFee = 0L;
        }
        long total = subtotal + shippingFee;
        txtAmount.setText("Rs. " + currency.format(subtotal));
        txtShippingFee.setText(shippingFee <= 0 ? "-" : "Rs. " + currency.format(shippingFee));
        txtTotal.setText("Rs. " + currency.format(total));
        findViewById(R.id.btnContinueToPayment).setEnabled(!checkingStock && !items.isEmpty() && hasMinimumData());
        findViewById(R.id.btnContinueToPayment).setAlpha((!checkingStock && !items.isEmpty() && hasMinimumData()) ? 1f : 0.45f);
    }

    private void continueToPayment() {
        if (checkingStock || currentItems.isEmpty() || !hasMinimumData()) return;
        checkingStock = true;
        renderTotals(currentItems);
        ProductStockService.validateCart(currentItems)
                .addOnSuccessListener(unused -> {
                    checkingStock = false;
                    if (isFinishing() || isDestroyed()) return;
                    renderTotals(currentItems);
                    startActivity(new Intent(this, CheckoutPinActivity.class));
                }).addOnFailureListener(error -> {
                    checkingStock = false;
                    if (isFinishing() || isDestroyed()) return;
                    renderTotals(currentItems);
                    AppMessageUtil.show(this, ProductStockService.errorMessage(error), AppMessageUtil.TYPE_ERROR);
                });
    }

    private boolean hasMinimumData() {
        if (CheckoutPrefs.SHIPPING_STORE_PICKUP.equals(CheckoutPrefs.getSelectedShippingType(this))) {
            return true;
        }
        return CheckoutPrefs.hasSelectedAddress(this);
    }

    private static String safe(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static double value(Double number) {
        return number == null ? 0d : number;
    }
}
