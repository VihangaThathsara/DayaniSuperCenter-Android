package com.nexcentauri.dayanisupercenter.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UserOrder {
    private final String orderId;
    private final String status;
    private final long total;
    private final long createdAtMillis;
    private final List<OrderItem> items;

    public UserOrder(String orderId, String status, long total, long createdAtMillis, List<OrderItem> items) {
        this.orderId = orderId == null ? "" : orderId;
        this.status = normalizeStatus(status);
        this.total = total;
        this.createdAtMillis = createdAtMillis;
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public long getTotal() { return total; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public int getItemCount() {
        int totalQty = 0;
        for (OrderItem item : items) totalQty += Math.max(1, item.getQuantity());
        return totalQty <= 0 ? items.size() : totalQty;
    }

    public int getProductTypeCount() {
        return Math.max(1, items.size());
    }
    public OrderItem getPreviewItem() {
        return items.isEmpty() ? new OrderItem("", "Order item", "", 0L, 1) : items.get(0);
    }
    public boolean isCompleted() { return "delivered".equals(status); }
    public boolean isOngoing() { return !isCompleted(); }

    public static String normalizeStatus(String raw) {
        String value = raw == null ? "pending" : raw.trim().toLowerCase(Locale.US);
        switch (value) {
            case "paid":
            case "placed":
            case "order_placed":
                return "pending";
            case "processing":
            case "shipped":
            case "delivered":
            case "pending":
                return value;
            default:
                return "pending";
        }
    }

    public static String getDisplayStatus(String raw) {
        String status = normalizeStatus(raw);
        switch (status) {
            case "processing":
                return "Processing";
            case "shipped":
                return "In Delivery";
            case "delivered":
                return "Delivered";
            default:
                return "Order Placed";
        }
    }

    public static int getStatusStep(String raw) {
        switch (normalizeStatus(raw)) {
            case "processing": return 1;
            case "shipped": return 2;
            case "delivered": return 3;
            default: return 0;
        }
    }
}
