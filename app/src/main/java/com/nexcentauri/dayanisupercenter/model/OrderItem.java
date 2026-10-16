package com.nexcentauri.dayanisupercenter.model;

public class OrderItem {
    private final String productId;
    private final String name;
    private final String imageUrl;
    private final long price;
    private final int quantity;

    public OrderItem(String productId, String name, String imageUrl, long price, int quantity) {
        this.productId = productId == null ? "" : productId;
        this.name = name == null ? "Product" : name;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.price = price;
        this.quantity = Math.max(1, quantity);
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
    public long getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public long getSubtotal() { return price * Math.max(1, quantity); }
}
