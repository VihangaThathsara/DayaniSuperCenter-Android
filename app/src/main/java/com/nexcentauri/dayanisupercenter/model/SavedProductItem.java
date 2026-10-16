package com.nexcentauri.dayanisupercenter.model;

public class SavedProductItem {
    private String productId;
    private String name;
    private String imageUrl;
    private long price;
    private int quantity;
    private double rating;
    private long soldCount;

    public SavedProductItem() {
    }

    public SavedProductItem(String productId, String name, String imageUrl, long price, int quantity) {
        this(productId, name, imageUrl, price, quantity, 0.0, 0L);
    }

    public SavedProductItem(String productId, String name, String imageUrl, long price, int quantity,
                            double rating, long soldCount) {
        this.productId = productId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.price = price;
        this.quantity = quantity;
        this.rating = rating;
        this.soldCount = soldCount;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public long getSoldCount() {
        return soldCount;
    }

    public void setSoldCount(long soldCount) {
        this.soldCount = soldCount;
    }
}
