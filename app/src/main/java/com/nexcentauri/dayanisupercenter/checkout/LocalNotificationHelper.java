package com.nexcentauri.dayanisupercenter.checkout;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.activity.NotificationActivity;

public final class LocalNotificationHelper {

    private static final String CHANNEL_ID = "orders_channel";

    private LocalNotificationHelper() {}

    public static void showOrderPlaced(Context context, String orderId) {
        Intent event = new Intent(context, OrderPlacedReceiver.class)
                .setAction(OrderPlacedReceiver.ACTION_ORDER_PLACED)
                .setPackage(context.getPackageName())
                .putExtra(OrderPlacedReceiver.EXTRA_ORDER_ID, orderId);
        context.sendBroadcast(event);
    }

    static void postOrderPlaced(Context context, String orderId) {
        createChannel(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, NotificationActivity.class);
        intent.putExtra("open_order_id", orderId);
        int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                : android.app.PendingIntent.FLAG_UPDATE_CURRENT;

        android.app.PendingIntent pendingIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(orderId.hashCode(), flags);

        Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher_round);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_status_success)
                .setLargeIcon(largeIcon)
                .setContentTitle("Order placed successfully")
                .setContentText("Your order " + orderId + " was placed successfully.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Your order " + orderId + " was placed successfully."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Orders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Order updates");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
