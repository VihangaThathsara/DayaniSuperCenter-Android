package com.nexcentauri.dayanisupercenter;

import com.nexcentauri.dayanisupercenter.util.LocationFixPolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationFixPolicyTest {
    @Test public void nearbyOrStaleLocationMustNotBeUsedAsImmediateAccurateFix() {
        assertFalse(LocationFixPolicy.isAccurate(150f, 1000L));
        assertFalse(LocationFixPolicy.isAccurate(10f, 600000L));
        assertFalse(LocationFixPolicy.isAccurate(Float.NaN, 100L));
        assertFalse(LocationFixPolicy.isAccurate(-1f, 100L));
        assertFalse(LocationFixPolicy.isAccurate(25f, 5000L));
        assertTrue(LocationFixPolicy.isAccurate(10f, 5000L));
    }

    @Test public void freshApproximateFixCanBeIdentifiedForTimeoutFallback() {
        assertTrue(LocationFixPolicy.isFresh(150f, 1000L));
        assertFalse(LocationFixPolicy.isAccurate(150f, 1000L));
        assertTrue(LocationFixPolicy.isFresh(150f, 45000L));
        assertFalse(LocationFixPolicy.isFresh(150f, 60001L));
    }

    @Test public void cachedPreviewDoesNotReplaceAFreshAccurateFix() {
        assertTrue(LocationFixPolicy.canShowImmediately(15f, 5000L));
        assertFalse(LocationFixPolicy.canShowImmediately(80f, 1000L));
        assertFalse(LocationFixPolicy.canShowImmediately(5f, 30000L));
        assertTrue(LocationFixPolicy.isBetter(8f, 1000L, 20f, 1000L));
        assertFalse(LocationFixPolicy.isBetter(20f, 1000L, 8f, 1000L));
        assertFalse(LocationFixPolicy.isBetter(Float.NaN, 1000L, 8f, 1000L));
    }

    @Test public void approximatePreviewCanRecenterWithoutBecomingADeliveryPoint() {
        assertTrue(LocationFixPolicy.canPreviewOnMap(300f, 1000L));
        assertTrue(LocationFixPolicy.canPreviewOnMap(3000f, 1000L));
        assertFalse(LocationFixPolicy.canUseForAddress(300f, 1000L));
        assertFalse(LocationFixPolicy.canPreviewOnMap(10f, 30001L));
        assertFalse(LocationFixPolicy.canPreviewOnMap(5001f, 1000L));
        assertFalse(LocationFixPolicy.canPreviewOnMap(Float.NaN, 1000L));
        assertFalse(LocationFixPolicy.canPreviewOnMap(-1f, 1000L));
        assertFalse(LocationFixPolicy.canPreviewOnMap(10f, -1L));
    }
}
