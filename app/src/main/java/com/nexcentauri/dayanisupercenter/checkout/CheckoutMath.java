package com.nexcentauri.dayanisupercenter.checkout;

import android.location.Location;

public final class CheckoutMath {

    // Default store location. Update to your actual store coordinates when ready.
    public static final double STORE_LAT = 7.506952;
    public static final double STORE_LNG = 80.343035;

    private CheckoutMath() {}

    public static double calculateDistanceKm(double userLat, double userLng) {
        if (userLat == 0d && userLng == 0d) return 0d;
        float[] results = new float[1];
        Location.distanceBetween(STORE_LAT, STORE_LNG, userLat, userLng, results);
        return results[0] / 1000d;
    }

    public static long calculateShippingFee(String shippingType, double userLat, double userLng) {
        if (CheckoutPrefs.SHIPPING_STORE_PICKUP.equals(shippingType)) {
            return 0L;
        }
        double km = calculateDistanceKm(userLat, userLng);
        if (km <= 5d) return 50L;
        if (km <= 10d) return 100L;
        if (km <= 20d) return 200L;
        if (km <= 30d) return 300L;
        if (km <= 40d) return 400L;
        return 500L;
    }
}

