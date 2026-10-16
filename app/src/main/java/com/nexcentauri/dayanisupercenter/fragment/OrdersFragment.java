package com.nexcentauri.dayanisupercenter.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.activity.TrackOrderActivity;
import com.nexcentauri.dayanisupercenter.adapter.OrdersAdapter;
import com.nexcentauri.dayanisupercenter.model.OrderItem;
import com.nexcentauri.dayanisupercenter.model.UserOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrdersFragment extends Fragment {

    private static final String ARG_OPEN_ORDER_ID = "open_order_id";
    private static final long REFRESH_THROTTLE_MS = 5_000L;

    public static OrdersFragment newInstance(String openOrderId) {
        OrdersFragment fragment = new OrdersFragment();
        Bundle args = new Bundle();
        args.putString(ARG_OPEN_ORDER_ID, openOrderId);
        fragment.setArguments(args);
        return fragment;
    }

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private RecyclerView recyclerOrdersTab;
    private View emptyOrdersTab;
    private TextView tabOngoing;
    private TextView tabCompleted;
    private View indicatorOngoing;
    private View indicatorCompleted;
    private OrdersAdapter ordersAdapter;
    private boolean showingCompletedOrders = false;
    private final List<UserOrder> allUserOrders = new ArrayList<>();
    private String pendingOpenOrderId;
    private long lastLoadedAtMs = 0L;
    private boolean firstLoadDone = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_orders_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        pendingOpenOrderId = getArguments() != null ? getArguments().getString(ARG_OPEN_ORDER_ID) : null;

        recyclerOrdersTab = view.findViewById(R.id.recyclerOrdersTab);
        emptyOrdersTab = view.findViewById(R.id.layoutOrdersEmpty);
        tabOngoing = view.findViewById(R.id.tabOngoing);
        tabCompleted = view.findViewById(R.id.tabCompleted);
        indicatorOngoing = view.findViewById(R.id.indicatorOngoing);
        indicatorCompleted = view.findViewById(R.id.indicatorCompleted);

        recyclerOrdersTab.setLayoutManager(new LinearLayoutManager(requireContext()));
        ordersAdapter = new OrdersAdapter(order -> {
            Intent intent = new Intent(requireContext(), TrackOrderActivity.class);
            intent.putExtra(TrackOrderActivity.EXTRA_ORDER_ID, order.getOrderId());
            startActivity(intent);
        });
        recyclerOrdersTab.setAdapter(ordersAdapter);
        recyclerOrdersTab.setVisibility(View.GONE);
        emptyOrdersTab.setVisibility(View.VISIBLE);

        tabOngoing.setOnClickListener(v -> {
            showingCompletedOrders = false;
            updateOrdersTabSelection();
            bindOrders();
        });
        tabCompleted.setOnClickListener(v -> {
            showingCompletedOrders = true;
            updateOrdersTabSelection();
            bindOrders();
        });
        updateOrdersTabSelection();
    }

    @Override
    public void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (!firstLoadDone || pendingOpenOrderId != null || now - lastLoadedAtMs >= REFRESH_THROTTLE_MS) {
            loadOrders();
        }
    }

    private void updateOrdersTabSelection() {
        if (!isAdded()) return;
        boolean ongoingSelected = !showingCompletedOrders;
        tabOngoing.setTextColor(requireContext().getColor(ongoingSelected ? android.R.color.black : android.R.color.darker_gray));
        tabCompleted.setTextColor(requireContext().getColor(ongoingSelected ? android.R.color.darker_gray : android.R.color.black));
        tabOngoing.setTypeface(tabOngoing.getTypeface(), ongoingSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tabCompleted.setTypeface(tabCompleted.getTypeface(), ongoingSelected ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        indicatorOngoing.setBackgroundColor(ongoingSelected ? 0xFF111111 : 0x00000000);
        indicatorCompleted.setBackgroundColor(ongoingSelected ? 0x00000000 : 0xFF111111);
    }

    private void loadOrders() {
        lastLoadedAtMs = System.currentTimeMillis();
        FirebaseUser currentUser = auth != null ? auth.getCurrentUser() : null;
        if (currentUser == null) {
            allUserOrders.clear();
            bindOrders();
            return;
        }

        db.collection("users")
                .document(currentUser.getUid())
                .collection("orders")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allUserOrders.clear();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots.getDocuments()) {
                        allUserOrders.add(parseUserOrder(snapshot));
                    }
                    allUserOrders.sort((a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()));
                    firstLoadDone = true;
                    bindOrders();
                    maybeOpenPendingOrder();
                })
                .addOnFailureListener(e -> {
                    allUserOrders.clear();
                    bindOrders();
                });
    }

    private void maybeOpenPendingOrder() {
        if (!isAdded() || pendingOpenOrderId == null || pendingOpenOrderId.trim().isEmpty()) {
            return;
        }
        String orderId = pendingOpenOrderId;
        pendingOpenOrderId = null;
        recyclerOrdersTab.postDelayed(() -> {
            if (!isAdded()) return;
            Intent trackIntent = new Intent(requireContext(), TrackOrderActivity.class);
            trackIntent.putExtra(TrackOrderActivity.EXTRA_ORDER_ID, orderId);
            startActivity(trackIntent);
        }, 250);
    }

    private void bindOrders() {
        List<UserOrder> filteredOrders = new ArrayList<>();
        for (UserOrder order : allUserOrders) {
            if (showingCompletedOrders ? order.isCompleted() : order.isOngoing()) {
                filteredOrders.add(order);
            }
        }

        ordersAdapter.submitList(filteredOrders);

        boolean isEmpty = filteredOrders.isEmpty();
        recyclerOrdersTab.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyOrdersTab.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        TextView title = emptyOrdersTab.findViewById(R.id.txtOrdersEmptyTitle);
        TextView subtitle = emptyOrdersTab.findViewById(R.id.txtOrdersEmptySubtitle);
        title.setText(showingCompletedOrders ? "No completed orders yet" : "You don't have an order yet");
        subtitle.setText(showingCompletedOrders
                ? "Delivered orders will appear here once your orders are completed"
                : "You don't have any ongoing orders at this time");
    }

    private UserOrder parseUserOrder(DocumentSnapshot snapshot) {
        String orderId = snapshot.getString("orderId");
        if (orderId == null || orderId.trim().isEmpty()) orderId = snapshot.getId();
        String status = UserOrder.normalizeStatus(snapshot.getString("status"));
        long total = longValue(snapshot.get("total"));
        long createdAtMillis = readOrderTime(snapshot);
        List<OrderItem> items = new ArrayList<>();
        Object rawItems = snapshot.get("items");
        if (rawItems instanceof List<?>) {
            for (Object object : (List<?>) rawItems) {
                if (object instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) object;
                    items.add(new OrderItem(
                            objectToString(map.get("productId")),
                            objectToString(map.get("name")),
                            objectToString(map.get("imageUrl")),
                            longValue(map.get("price")),
                            (int) longValue(map.get("quantity"))
                    ));
                }
            }
        }
        return new UserOrder(orderId, status, total, createdAtMillis, items);
    }

    private long readOrderTime(DocumentSnapshot snapshot) {
        Timestamp client = snapshot.getTimestamp("createdAtClient");
        if (client != null) return client.toDate().getTime();
        Timestamp server = snapshot.getTimestamp("createdAt");
        if (server != null) return server.toDate().getTime();
        return System.currentTimeMillis();
    }

    private String objectToString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
