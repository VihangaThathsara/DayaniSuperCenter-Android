package com.nexcentauri.dayanisupercenter.model;

public class CountryItem {
    private final String name;
    private final String isoCode;
    private final String phoneCode;
    private final String flagEmoji;

    public CountryItem(String name, String isoCode, String phoneCode, String flagEmoji) {
        this.name = name;
        this.isoCode = isoCode;
        this.phoneCode = phoneCode;
        this.flagEmoji = flagEmoji;
    }

    public String getName() {
        return name;
    }

    public String getIsoCode() {
        return isoCode;
    }

    public String getPhoneCode() {
        return phoneCode;
    }

    public String getFlagEmoji() {
        return flagEmoji;
    }

    @Override
    public String toString() {
        return flagEmoji + "  " + name + " (" + phoneCode + ")";
    }
}
