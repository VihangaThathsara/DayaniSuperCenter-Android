package com.nexcentauri.dayanisupercenter.model;

public class HomeProduct {
    private final String id;
    private final String name;
    private final double rating;
    private final int soldCount;
    private final int price;
    private final String imageUrl;
    private final String categoryId;
    private final String categoryName;
    private final boolean mostPopular;
    private final boolean active;

    public HomeProduct(String id, String name, double rating, int soldCount, int price,
                       String imageUrl, String categoryId, String categoryName, boolean mostPopular, boolean active) {
        this.id = id;
        this.name = name;
        this.rating = rating;
        this.soldCount = soldCount;
        this.price = price;
        this.imageUrl = imageUrl;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.mostPopular = mostPopular;
        this.active = active;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getRating() { return rating; }
    public int getSoldCount() { return soldCount; }
    public int getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public String getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public boolean isMostPopular() { return mostPopular; }
    public boolean isActive() { return active; }
}
