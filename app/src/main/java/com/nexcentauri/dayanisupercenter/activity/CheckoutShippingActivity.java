package com.nexcentauri.dayanisupercenter.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.checkout.CheckoutMath;
import com.nexcentauri.dayanisupercenter.checkout.CheckoutPrefs;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

import java.text.NumberFormat;
import java.util.Locale;

public class CheckoutShippingActivity extends AppCompatActivity {

    private String selectedType;
    private final NumberFormat currency = NumberFormat.getNumberInstance(Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout_shipping);
        SystemBarUtil.matchStatusBarToContent(this);

        selectedType = CheckoutPrefs.getSelectedShippingType(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.optionHomeDelivery).setOnClickListener(v -> {
            selectedType = CheckoutPrefs.SHIPPING_HOME_DELIVERY;
            bind();
        });
        findViewById(R.id.optionStorePickup).setOnClickListener(v -> {
            selectedType = CheckoutPrefs.SHIPPING_STORE_PICKUP;
            bind();
        });
        findViewById(R.id.btnApply).setOnClickListener(v -> {
            CheckoutPrefs.setSelectedShippingType(this, selectedType);
            finish();
        });
        ((ImageView) findViewById(R.id.optionHomeDelivery).findViewById(R.id.imgIcon)).setImageResource(R.drawable.ic_home_delivery_custom);
        ((ImageView) findViewById(R.id.optionStorePickup).findViewById(R.id.imgIcon)).setImageResource(R.drawable.ic_store_pickup_custom);
        bind();
    }

    private void bind() {
        long deliveryFee = CheckoutMath.calculateShippingFee(CheckoutPrefs.SHIPPING_HOME_DELIVERY,
                CheckoutPrefs.getSelectedAddressLat(this),
                CheckoutPrefs.getSelectedAddressLng(this));
        double km = CheckoutMath.calculateDistanceKm(
                CheckoutPrefs.getSelectedAddressLat(this),
                CheckoutPrefs.getSelectedAddressLng(this));

        bindOption(R.id.optionHomeDelivery,
                "Home Delivery",
                CheckoutPrefs.hasSelectedAddress(this)
                        ? "Distance based fee • ~ " + Math.max(1, Math.round(km)) + " km"
                        : "Select address first for distance based fee",
                "Rs. " + currency.format(deliveryFee),
                CheckoutPrefs.SHIPPING_HOME_DELIVERY.equals(selectedType));

        bindOption(R.id.optionStorePickup,
                "Store Pickup",
                "Collect your order from store",
                "Free",
                CheckoutPrefs.SHIPPING_STORE_PICKUP.equals(selectedType));
    }

    private void bindOption(int rootId, String title, String subtitle, String price, boolean selected) {
        View root = findViewById(rootId);
        ((TextView) root.findViewById(R.id.txtTitle)).setText(title);
        ((TextView) root.findViewById(R.id.txtSubtitle)).setText(subtitle);
        ((TextView) root.findViewById(R.id.txtPrice)).setText(price);
        ((ImageView) root.findViewById(R.id.imgRadio)).setImageResource(selected ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
    }
}
