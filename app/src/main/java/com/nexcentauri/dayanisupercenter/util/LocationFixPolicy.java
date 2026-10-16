package com.nexcentauri.dayanisupercenter.util;

public final class LocationFixPolicy {
    public static final long MAX_FIX_AGE_MS = 60000L;
    public static final float TARGET_ACCURACY_METERS = 10f;

    private LocationFixPolicy() {}

    public static boolean hasValidCoordinates(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90d && latitude <= 90d && longitude >= -180d && longitude <= 180d;
    }

    public static boolean canUseForAddress(float accuracy, long ageMs) {
        return isFresh(accuracy, ageMs) && ageMs <= 15000L && accuracy <= 50f;
    }

    public static boolean isFresh(float accuracy, long ageMs) {
        return Float.isFinite(accuracy) && accuracy >= 0f
                && ageMs >= 0L && ageMs <= MAX_FIX_AGE_MS;
    }

    public static boolean isAccurate(float accuracy, long ageMs) {
        return isFresh(accuracy, ageMs) && ageMs <= 10000L && accuracy <= TARGET_ACCURACY_METERS;
    }

    public static boolean canShowImmediately(float accuracy, long ageMs) {
        return isFresh(accuracy, ageMs) && ageMs <= 15000L && accuracy <= 25f;
    }

    // A map preview may be approximate; selecting a delivery point stays stricter.
    public static boolean canPreviewOnMap(float accuracy, long ageMs) {
        return isFresh(accuracy, ageMs) && ageMs <= 30000L && accuracy <= 5000f;
    }

    public static boolean isBetter(float accuracy, long ageMs, float previousAccuracy, long previousAgeMs) {
        return isFresh(accuracy, ageMs) && (!isFresh(previousAccuracy, previousAgeMs)
                || accuracy + ageMs / 2000f < previousAccuracy + previousAgeMs / 2000f);
    }
}
