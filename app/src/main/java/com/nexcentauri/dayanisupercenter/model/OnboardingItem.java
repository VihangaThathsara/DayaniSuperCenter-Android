package com.nexcentauri.dayanisupercenter.model;

public class OnboardingItem {
    public final int imageRes;
    public final String title;
    public final String desc;
    public final boolean isFirstPage;

    public OnboardingItem(int imageRes, String title, String desc, boolean isFirstPage) {
        this.imageRes = imageRes;
        this.title = title;
        this.desc = desc;
        this.isFirstPage = isFirstPage;
    }
}
