package com.nexcentauri.dayanisupercenter.checkout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the existing order-success event; never accepts broadcasts from other apps. */
public final class OrderPlacedReceiver extends BroadcastReceiver {
    static final String ACTION_ORDER_PLACED = "com.nexcentauri.dayanisupercenter.ORDER_PLACED";
    static final String EXTRA_ORDER_ID = "order_id";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_ORDER_PLACED.equals(intent.getAction())) return;
        String orderId = intent.getStringExtra(EXTRA_ORDER_ID);
        if (orderId == null || orderId.trim().isEmpty()) return;
        LocalNotificationHelper.postOrderPlaced(context, orderId);
    }
}
