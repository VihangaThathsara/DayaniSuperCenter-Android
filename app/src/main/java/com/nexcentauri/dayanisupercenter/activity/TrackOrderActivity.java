package com.nexcentauri.dayanisupercenter.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.adapter.TrackOrderPagerAdapter;
import com.nexcentauri.dayanisupercenter.model.OrderItem;
import com.nexcentauri.dayanisupercenter.model.UserOrder;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrackOrderActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "order_id";

    private final Handler autoScrollHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewPager == null || pagerAdapter == null || pagerAdapter.getItemCount() <= 1) return;
            int next = (viewPager.getCurrentItem() + 1) % pagerAdapter.getItemCount();
            viewPager.setCurrentItem(next, true);
            autoScrollHandler.postDelayed(this, 2600L);
        }
    };

    private final SimpleDateFormat timelineFormat = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

    private ViewPager2 viewPager;
    private LinearLayout dotsLayout;
    private TrackOrderPagerAdapter pagerAdapter;
    private TextView txtCurrentStatus;
    private View dividerTrackTop;

    private View stepPlacedBg, stepProcessingBg, stepShippedBg, stepDeliveredBg;
    private View checkPlacedBg, checkProcessingBg, checkShippedBg, checkDeliveredBg;
    private View lineCheckPlacedProcessing, lineCheckProcessingShipped, lineCheckShippedDelivered;
    private ImageView imgStepPlaced, imgStepProcessing, imgStepShipped, imgStepDelivered;

    private View rowPlacedDot, rowProcessingDot, rowShippedDot, rowDeliveredDot;
    private View rowPlacedOuter, rowProcessingOuter, rowShippedOuter, rowDeliveredOuter;
    private View linePlacedVertical, lineProcessingVertical, lineShippedVertical;
    private LinearLayout rowPlaced, rowProcessing, rowShipped, rowDelivered;
    private TextView txtPlacedTitle, txtProcessingTitle, txtShippedTitle, txtDeliveredTitle;
    private TextView txtPlacedMessage, txtProcessingMessage, txtShippedMessage, txtDeliveredMessage;
    private TextView txtPlacedTime, txtProcessingTime, txtShippedTime, txtDeliveredTime;

    private ListenerRegistration orderListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_track_order);

        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.applyWindowInsets(this);

        bindViews();
        findViewById(R.id.btnBackTrack).setOnClickListener(v -> finish());

        pagerAdapter = new TrackOrderPagerAdapter();
        viewPager.setAdapter(pagerAdapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
            }
        });

        listenOrder();
    }

    private void bindViews() {
        viewPager = findViewById(R.id.viewPagerTrackProducts);
        dotsLayout = findViewById(R.id.layoutTrackDots);
        txtCurrentStatus = findViewById(R.id.txtCurrentTrackStatus);
        dividerTrackTop = findViewById(R.id.dividerTrackTop);

        stepPlacedBg = findViewById(R.id.stepPlacedBg);
        stepProcessingBg = findViewById(R.id.stepProcessingBg);
        stepShippedBg = findViewById(R.id.stepShippedBg);
        stepDeliveredBg = findViewById(R.id.stepDeliveredBg);

        checkPlacedBg = findViewById(R.id.checkPlacedBg);
        checkProcessingBg = findViewById(R.id.checkProcessingBg);
        checkShippedBg = findViewById(R.id.checkShippedBg);
        checkDeliveredBg = findViewById(R.id.checkDeliveredBg);

        lineCheckPlacedProcessing = findViewById(R.id.lineCheckPlacedProcessing);
        lineCheckProcessingShipped = findViewById(R.id.lineCheckProcessingShipped);
        lineCheckShippedDelivered = findViewById(R.id.lineCheckShippedDelivered);

        imgStepPlaced = findViewById(R.id.imgStepPlaced);
        imgStepProcessing = findViewById(R.id.imgStepProcessing);
        imgStepShipped = findViewById(R.id.imgStepShipped);
        imgStepDelivered = findViewById(R.id.imgStepDelivered);

        rowPlaced = findViewById(R.id.rowPlaced);
        rowProcessing = findViewById(R.id.rowProcessing);
        rowShipped = findViewById(R.id.rowShipped);
        rowDelivered = findViewById(R.id.rowDelivered);

        rowPlacedOuter = findViewById(R.id.rowPlacedOuter);
        rowProcessingOuter = findViewById(R.id.rowProcessingOuter);
        rowShippedOuter = findViewById(R.id.rowShippedOuter);
        rowDeliveredOuter = findViewById(R.id.rowDeliveredOuter);

        rowPlacedDot = findViewById(R.id.rowPlacedDot);
        rowProcessingDot = findViewById(R.id.rowProcessingDot);
        rowShippedDot = findViewById(R.id.rowShippedDot);
        rowDeliveredDot = findViewById(R.id.rowDeliveredDot);

        linePlacedVertical = findViewById(R.id.linePlacedVertical);
        lineProcessingVertical = findViewById(R.id.lineProcessingVertical);
        lineShippedVertical = findViewById(R.id.lineShippedVertical);

        txtPlacedTitle = findViewById(R.id.txtPlacedTitle);
        txtProcessingTitle = findViewById(R.id.txtProcessingTitle);
        txtShippedTitle = findViewById(R.id.txtShippedTitle);
        txtDeliveredTitle = findViewById(R.id.txtDeliveredTitle);

        txtPlacedMessage = findViewById(R.id.txtPlacedMessage);
        txtProcessingMessage = findViewById(R.id.txtProcessingMessage);
        txtShippedMessage = findViewById(R.id.txtShippedMessage);
        txtDeliveredMessage = findViewById(R.id.txtDeliveredMessage);

        txtPlacedTime = findViewById(R.id.txtPlacedTime);
        txtProcessingTime = findViewById(R.id.txtProcessingTime);
        txtShippedTime = findViewById(R.id.txtShippedTime);
        txtDeliveredTime = findViewById(R.id.txtDeliveredTime);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startAutoScroll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoScroll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoScroll();
        if (orderListener != null) {
            orderListener.remove();
            orderListener = null;
        }
    }

    private void listenOrder() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (currentUser == null || orderId == null || orderId.trim().isEmpty()) {
            AppMessageUtil.show(this, "Order details unavailable.", AppMessageUtil.TYPE_ERROR);
            finish();
            return;
        }

        orderListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .collection("orders")
                .document(orderId)
                .addSnapshotListener(this, (snapshot, e) -> {
                    if (e != null) {
                        AppMessageUtil.show(this, "Failed to load order.", AppMessageUtil.TYPE_ERROR);
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        AppMessageUtil.show(this, "Order not found.", AppMessageUtil.TYPE_ERROR);
                        finish();
                        return;
                    }
                    bindOrder(parseOrder(snapshot), snapshot);
                });
    }

    private void bindOrder(@NonNull UserOrder order, @NonNull DocumentSnapshot snapshot) {
        List<OrderItem> safeItems = order.getItems() == null ? new ArrayList<>() : new ArrayList<>(order.getItems());
        if (safeItems.isEmpty()) {
            safeItems.add(new OrderItem("", "Order item", "", 0L, 1));
        }
        pagerAdapter.submitList(safeItems, order.getItemCount());

        int itemSize = safeItems.size();
        boolean showCarousel = itemSize > 1;
        setupDots(itemSize);
        viewPager.setUserInputEnabled(showCarousel);
        viewPager.setCurrentItem(0, false);

        txtCurrentStatus.setText("Order Status Details");
        dividerTrackTop.setVisibility(View.VISIBLE);

        int step = UserOrder.getStatusStep(order.getStatus());
        updateStepUi(step);
        bindStatusMessages(step, snapshot, order);

        if (showCarousel) startAutoScroll(); else stopAutoScroll();
    }

    private void setupDots(int count) {
        dotsLayout.removeAllViews();
        boolean showDots = count > 1;
        dotsLayout.setVisibility(showDots ? View.VISIBLE : View.GONE);
        ViewGroup.LayoutParams params = viewPager.getLayoutParams();
        params.height = dp(showDots ? 140 : 136);
        viewPager.setLayoutParams(params);
        if (!showDots) return;
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
            if (i > 0) dotParams.leftMargin = dp(6);
            dot.setLayoutParams(dotParams);
            dot.setBackgroundResource(i == 0 ? R.drawable.dot_track_active : R.drawable.dot_track_inactive);
            dotsLayout.addView(dot);
        }
    }

    private void updateDots(int activePosition) {
        for (int i = 0; i < dotsLayout.getChildCount(); i++) {
            dotsLayout.getChildAt(i).setBackgroundResource(i == activePosition
                    ? R.drawable.dot_track_active
                    : R.drawable.dot_track_inactive);
        }
    }

    private void updateStepUi(int step) {
        updateStep(stepPlacedBg, imgStepPlaced, step >= 0);
        updateStep(stepProcessingBg, imgStepProcessing, step >= 1);
        updateStep(stepShippedBg, imgStepShipped, step >= 2);
        updateStep(stepDeliveredBg, imgStepDelivered, step >= 3);

        updateCheck(checkPlacedBg, step >= 0);
        updateCheck(checkProcessingBg, step >= 1);
        updateCheck(checkShippedBg, step >= 2);
        updateCheck(checkDeliveredBg, step >= 3);

        updateHorizontalLine(lineCheckPlacedProcessing, step >= 1);
        updateHorizontalLine(lineCheckProcessingShipped, step >= 2);
        updateHorizontalLine(lineCheckShippedDelivered, step >= 3);
    }

    private void updateStep(View bg, ImageView icon, boolean active) {
        bg.setBackgroundResource(R.drawable.bg_track_step_neutral);
        icon.setAlpha(active ? 1f : 0.28f);
        icon.setColorFilter(ContextCompat.getColor(this,
                active ? android.R.color.black : android.R.color.darker_gray));
    }

    private void updateCheck(View bg, boolean active) {
        bg.setBackgroundResource(active
                ? R.drawable.bg_track_check_circle_active
                : R.drawable.bg_track_check_circle_inactive);
        bg.setAlpha(active ? 1f : 0.78f);
    }

    private void updateHorizontalLine(View line, boolean active) {
        if (line == null) return;
        line.setBackgroundResource(active ? R.drawable.bg_track_line_active : R.drawable.bg_track_line_inactive);
        line.setAlpha(active ? 1f : 0.40f);
    }

    private void bindStatusMessages(int step, @NonNull DocumentSnapshot snapshot, @NonNull UserOrder order) {
        long placedAt = order.getCreatedAtMillis();
        long statusUpdatedAt = extractTime(snapshot.get("statusUpdatedAt"));
        if (statusUpdatedAt <= 0L) statusUpdatedAt = placedAt;

        long processingAt = readStatusHistoryTime(snapshot, "processing");
        long shippedAt = readStatusHistoryTime(snapshot, "shipped");
        long deliveredAt = readStatusHistoryTime(snapshot, "delivered");

        if (processingAt <= 0L && step >= 1) processingAt = statusUpdatedAt;
        if (shippedAt <= 0L && step >= 2) shippedAt = statusUpdatedAt;
        if (deliveredAt <= 0L && step >= 3) deliveredAt = statusUpdatedAt;

        ArrayList<RowData> rows = new ArrayList<>();
        if (step >= 3) rows.add(new RowData("Order Delivered", deliveredAt, "Your order has been delivered", true));
        if (step >= 2) rows.add(new RowData("Packet In Delivery", shippedAt, "Your order is out for delivery", true));
        if (step >= 1) rows.add(new RowData("Order Processing", processingAt, "Your order is being prepared", true));
        rows.add(new RowData("Order Placed", placedAt, "Order placed successfully", true));

        bindTimelineRow(0, rows.size() > 0 ? rows.get(0) : null, rows.size() > 1);
        bindTimelineRow(1, rows.size() > 1 ? rows.get(1) : null, rows.size() > 2);
        bindTimelineRow(2, rows.size() > 2 ? rows.get(2) : null, rows.size() > 3);
        bindTimelineRow(3, rows.size() > 3 ? rows.get(3) : null, false);
    }

    private void bindTimelineRow(int index, @Nullable RowData data, boolean showConnector) {
        LinearLayout row = getRow(index);
        View outer = getRowOuter(index);
        View dot = getRowDot(index);
        View line = getRowLine(index);
        TextView title = getRowTitle(index);
        TextView message = getRowMessage(index);
        TextView time = getRowTime(index);

        if (data == null) {
            row.setVisibility(View.GONE);
            if (line != null) line.setVisibility(View.GONE);
            return;
        }

        row.setVisibility(View.VISIBLE);
        title.setText(data.title);
        message.setText(data.subtitle);
        time.setText(formatTime(data.timeMillis));

        outer.setBackgroundResource(data.active
                ? R.drawable.bg_track_timeline_circle_active
                : R.drawable.bg_track_timeline_circle_inactive);
        dot.setBackgroundResource(data.active
                ? R.drawable.bg_track_status_dot_active
                : R.drawable.bg_track_status_dot_inactive);

        float alpha = data.active ? 1f : 0.60f;
        title.setAlpha(alpha);
        message.setAlpha(alpha);
        time.setAlpha(alpha);

        if (line != null) {
            line.setVisibility(showConnector ? View.VISIBLE : View.GONE);
            line.setBackgroundResource(data.active
                    ? R.drawable.bg_track_vertical_dotted_active
                    : R.drawable.bg_track_vertical_dotted);
            line.setAlpha(data.active ? 1f : 0.45f);
        }
    }

    private LinearLayout getRow(int index) {
        switch (index) {
            case 1: return rowProcessing;
            case 2: return rowShipped;
            case 3: return rowDelivered;
            default: return rowPlaced;
        }
    }

    private View getRowOuter(int index) {
        switch (index) {
            case 1: return rowProcessingOuter;
            case 2: return rowShippedOuter;
            case 3: return rowDeliveredOuter;
            default: return rowPlacedOuter;
        }
    }

    private View getRowDot(int index) {
        switch (index) {
            case 1: return rowProcessingDot;
            case 2: return rowShippedDot;
            case 3: return rowDeliveredDot;
            default: return rowPlacedDot;
        }
    }

    private View getRowLine(int index) {
        switch (index) {
            case 0: return linePlacedVertical;
            case 1: return lineProcessingVertical;
            case 2: return lineShippedVertical;
            default: return null;
        }
    }

    private TextView getRowTitle(int index) {
        switch (index) {
            case 1: return txtProcessingTitle;
            case 2: return txtShippedTitle;
            case 3: return txtDeliveredTitle;
            default: return txtPlacedTitle;
        }
    }

    private TextView getRowMessage(int index) {
        switch (index) {
            case 1: return txtProcessingMessage;
            case 2: return txtShippedMessage;
            case 3: return txtDeliveredMessage;
            default: return txtPlacedMessage;
        }
    }

    private TextView getRowTime(int index) {
        switch (index) {
            case 1: return txtProcessingTime;
            case 2: return txtShippedTime;
            case 3: return txtDeliveredTime;
            default: return txtPlacedTime;
        }
    }

    private UserOrder parseOrder(DocumentSnapshot snapshot) {
        String orderId = value(snapshot.getString("orderId"), snapshot.getId());
        String status = UserOrder.normalizeStatus(snapshot.getString("status"));
        long total = longValue(snapshot.get("total"));
        long createdAt = readTime(snapshot);
        List<OrderItem> items = new ArrayList<>();
        Object rawItems = snapshot.get("items");
        if (rawItems instanceof List<?>) {
            for (Object object : (List<?>) rawItems) {
                if (object instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) object;
                    items.add(new OrderItem(
                            stringValue(map.get("productId")),
                            stringValue(map.get("name")),
                            stringValue(map.get("imageUrl")),
                            longValue(map.get("price")),
                            Math.max(1, (int) longValue(map.get("quantity")))
                    ));
                }
            }
        }
        return new UserOrder(orderId, status, total, createdAt, items);
    }

    private long readTime(DocumentSnapshot snapshot) {
        Timestamp client = snapshot.getTimestamp("createdAtClient");
        if (client != null) return client.toDate().getTime();
        Timestamp server = snapshot.getTimestamp("createdAt");
        if (server != null) return server.toDate().getTime();
        return System.currentTimeMillis();
    }

    private long readStatusHistoryTime(DocumentSnapshot snapshot, String key) {
        Object object = snapshot.get("statusHistory");
        if (object instanceof Map<?, ?>) {
            return extractTime(((Map<?, ?>) object).get(key));
        }
        return 0L;
    }

    private long extractTime(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof Date) return ((Date) value).getTime();
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object seconds = map.get("seconds");
            if (seconds instanceof Number) {
                return ((Number) seconds).longValue() * 1000L;
            }
        }
        return 0L;
    }

    private String formatTime(long timeMillis) {
        if (timeMillis <= 0L) return "--";
        return timelineFormat.format(new Date(timeMillis));
    }

    private void startAutoScroll() {
        stopAutoScroll();
        if (pagerAdapter != null && pagerAdapter.getItemCount() > 1) {
            autoScrollHandler.postDelayed(autoScrollRunnable, 2600L);
        }
    }

    private void stopAutoScroll() {
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
    }

    private String value(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private static class RowData {
        final String title;
        final long timeMillis;
        final String subtitle;
        final boolean active;

        RowData(String title, long timeMillis, String subtitle, boolean active) {
            this.title = title;
            this.timeMillis = timeMillis;
            this.subtitle = subtitle;
            this.active = active;
        }
    }
}
