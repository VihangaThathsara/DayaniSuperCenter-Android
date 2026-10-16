package com.nexcentauri.dayanisupercenter.model;

public class OfferBanner {
    private final String percent;
    private final String title;
    private final String subtitle;
    private final int imageResId;

    public OfferBanner(String percent, String title, String subtitle, int imageResId) {
        this.percent = percent;
        this.title = title;
        this.subtitle = subtitle;
        this.imageResId = imageResId;
    }

    public String getPercent() { return percent; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public int getImageResId() { return imageResId; }
}
