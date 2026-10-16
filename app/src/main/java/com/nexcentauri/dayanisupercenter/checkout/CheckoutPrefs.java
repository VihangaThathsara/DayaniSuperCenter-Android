package com.nexcentauri.dayanisupercenter.checkout;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

public final class CheckoutPrefs {

    public static final String SHIPPING_HOME_DELIVERY = "home_delivery";
    public static final String SHIPPING_STORE_PICKUP = "store_pickup";
    private static final String PREFS = "checkout_prefs";
    private static final String KEY_ADDRESS_ID = "address_id";
    private static final String KEY_ADDRESS_LABEL = "address_label";
    private static final String KEY_ADDRESS_DETAILS = "address_details";
    private static final String KEY_ADDRESS_LAT = "address_lat";
    private static final String KEY_ADDRESS_LNG = "address_lng";
    private static final String KEY_SHIPPING_TYPE = "shipping_type";
    private static final String KEY_PAYMENT_METHOD = "payment_method";

    private CheckoutPrefs() {}

    private static SharedPreferences prefs(Context context) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        return context.getSharedPreferences(PREFS + "_" + (uid == null ? "guest" : uid), Context.MODE_PRIVATE);
    }

    public static void saveSelectedAddress(Context context, String id, String label, String details, double lat, double lng) {
        prefs(context).edit()
                .putString(KEY_ADDRESS_ID, id)
                .putString(KEY_ADDRESS_LABEL, label)
                .putString(KEY_ADDRESS_DETAILS, details)
                .putFloat(KEY_ADDRESS_LAT, (float) lat)
                .putFloat(KEY_ADDRESS_LNG, (float) lng)
                .apply();
    }

    public static boolean hasSelectedAddress(Context context) {
        return !TextUtils.isEmpty(prefs(context).getString(KEY_ADDRESS_ID, null));
    }

    public static String getSelectedAddressId(Context context) {
        return prefs(context).getString(KEY_ADDRESS_ID, null);
    }

    public static String getSelectedAddressLabel(Context context) {
        return prefs(context).getString(KEY_ADDRESS_LABEL, "Address");
    }

    public static String getSelectedAddressDetails(Context context) {
        return prefs(context).getString(KEY_ADDRESS_DETAILS, "Select your shipping address");
    }

    public static double getSelectedAddressLat(Context context) {
        return prefs(context).getFloat(KEY_ADDRESS_LAT, 0f);
    }

    public static double getSelectedAddressLng(Context context) {
        return prefs(context).getFloat(KEY_ADDRESS_LNG, 0f);
    }

    public static void setSelectedShippingType(Context context, String shippingType) {
        prefs(context).edit().putString(KEY_SHIPPING_TYPE, shippingType).apply();
    }

    @NonNull
    public static String getSelectedShippingType(Context context) {
        return prefs(context).getString(KEY_SHIPPING_TYPE, SHIPPING_HOME_DELIVERY);
    }

    public static void setSelectedPaymentMethod(Context context, String method) {
        prefs(context).edit().putString(KEY_PAYMENT_METHOD, method).apply();
    }

    public static String getSelectedPaymentMethod(Context context) {
        return prefs(context).getString(KEY_PAYMENT_METHOD, "");
    }

    public static void clearSelectedAddress(Context context) {
        prefs(context).edit()
                .remove(KEY_ADDRESS_ID)
                .remove(KEY_ADDRESS_LABEL)
                .remove(KEY_ADDRESS_DETAILS)
                .remove(KEY_ADDRESS_LAT)
                .remove(KEY_ADDRESS_LNG)
                .apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}
