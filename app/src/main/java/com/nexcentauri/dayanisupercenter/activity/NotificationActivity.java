package com.nexcentauri.dayanisupercenter.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.model.AppNotificationItem;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NotificationActivity extends AppCompatActivity {
    private LinearLayout listContainer;
    private View empty;
    private LayoutInflater inflater;
    private ListenerRegistration notificationsListener;
    private int listenSequence;
    private final Set<String> pendingReads = new HashSet<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notification);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.applyWindowInsets(this);
        inflater = LayoutInflater.from(this);
        listContainer = findViewById(R.id.layoutNotificationList);
        empty = findViewById(R.id.layoutEmptyNotification);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override protected void onStart() {
        super.onStart();
        loadNotifications();
    }

    @Override protected void onStop() {
        listenSequence++;
        if (notificationsListener != null) notificationsListener.remove();
        notificationsListener = null;
        super.onStop();
    }

    private List<AppNotificationItem> accountNotification(FirebaseUser user) {
        List<AppNotificationItem> items = new ArrayList<>();
        long created = user.getMetadata() == null ? 0L : user.getMetadata().getCreationTimestamp();
        if (created > 0L) items.add(new AppNotificationItem("account_created", "Account Setup Successful!",
                "Your account has been created!", created, "account", true));
        return items;
    }

    private void loadNotifications() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { showEmpty(); return; }
        String uid = user.getUid();
        int sequence = ++listenSequence;
        if (notificationsListener != null) notificationsListener.remove();
        notificationsListener = FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("notifications").addSnapshotListener((snapshot, error) -> {
                    if (sequence != listenSequence || isFinishing() || isDestroyed()
                            || !uid.equals(FirebaseAuth.getInstance().getUid())) return;
                    if (error != null) {
                        if (listContainer.getChildCount() == 0) renderNotifications(accountNotification(user), uid);
                        AppMessageUtil.show(this, "Could not refresh notifications. Check your connection.", AppMessageUtil.TYPE_ERROR);
                        return;
                    }
                    if (snapshot == null) return;
                    List<AppNotificationItem> items = accountNotification(user);
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Object time = document.get("createdAtClient");
                        if (time == null) time = document.get("createdAt");
                        items.add(new AppNotificationItem(document.getId(), value(document.getString("title"), "Notification"),
                                value(document.getString("message"), "You have a new update."), readTimestamp(time),
                                value(document.getString("type"), "account"), Boolean.TRUE.equals(document.getBoolean("read"))));
                    }
                    items.sort((a, b) -> Long.compare(b.getTimeMillis(), a.getTimeMillis()));
                    renderNotifications(items, uid);
                });
    }

    private void renderNotifications(List<AppNotificationItem> items, String uid) {
        listContainer.removeAllViews();
        if (items.isEmpty()) { showEmpty(); return; }
        empty.setVisibility(View.GONE);
        SimpleDateFormat headerFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
        String lastDate = null;
        for (AppNotificationItem item : items) {
            String currentDate = headerFormat.format(new Date(item.getTimeMillis()));
            if (!currentDate.equals(lastDate)) {
                View header = inflater.inflate(R.layout.item_notification_date_header, listContainer, false);
                ((TextView) header.findViewById(R.id.txtDateHeader)).setText(currentDate);
                listContainer.addView(header);
                lastDate = currentDate;
            }
            View row = inflater.inflate(R.layout.item_notification_entry, listContainer, false);
            TextView title = row.findViewById(R.id.txtNotificationTitle);
            TextView message = row.findViewById(R.id.txtMessage);
            ImageView icon = row.findViewById(R.id.imgNotificationIcon);
            title.setText(item.getTitle());
            message.setText(item.getMessage());
            icon.setImageResource("order".equalsIgnoreCase(item.getType()) ? R.drawable.ic_shopping_bag : R.drawable.ic_user);
            applyReadStyle(row, item.isRead());
            row.setOnClickListener(v -> markRead(uid, item));
            listContainer.addView(row);
        }
    }

    private void applyReadStyle(View row, boolean read) {
        row.setBackgroundResource(read ? R.drawable.bg_notification_read : R.drawable.bg_settings_card);
        ((TextView) row.findViewById(R.id.txtNotificationTitle)).setTextColor(android.graphics.Color.parseColor(read ? "#777777" : "#171717"));
        ((TextView) row.findViewById(R.id.txtMessage)).setTextColor(android.graphics.Color.parseColor(read ? "#999999" : "#171717"));
        ((ImageView) row.findViewById(R.id.imgNotificationIcon)).setColorFilter(android.graphics.Color.parseColor(read ? "#777777" : "#FFFFFF"));
        row.findViewById(R.id.layoutNotificationIcon).setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor(read ? "#E4E4E4" : "#353535")));
    }

    private void markRead(String uid, AppNotificationItem item) {
        if (item.isRead() || "account_created".equals(item.getId()) || !uid.equals(FirebaseAuth.getInstance().getUid())
                || !pendingReads.add(item.getId())) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference reference = db.collection("users").document(uid).collection("notifications").document(item.getId());
        db.runTransaction(transaction -> {
            DocumentSnapshot notification = transaction.get(reference);
            if (notification.exists() && !Boolean.TRUE.equals(notification.getBoolean("read")))
                transaction.update(reference, "read", true);
            return null;
        }).addOnCompleteListener(task -> {
            pendingReads.remove(item.getId());
            if (!task.isSuccessful() && !isFinishing() && !isDestroyed() && uid.equals(FirebaseAuth.getInstance().getUid()))
                AppMessageUtil.show(this, "Could not mark this notification as read. Check your connection.", AppMessageUtil.TYPE_ERROR);
        });
        // The Home badge listens to unread documents; opening this screen never clears unread items.
    }

    private void showEmpty() { listContainer.removeAllViews(); empty.setVisibility(View.VISIBLE); }
    private String value(String text, String fallback) { return text == null || text.trim().isEmpty() ? fallback : text; }
    private long readTimestamp(Object time) {
        if (time instanceof Timestamp) return ((Timestamp) time).toDate().getTime();
        if (time instanceof Date) return ((Date) time).getTime();
        return time instanceof Number ? ((Number) time).longValue() : 0L;
    }
}
