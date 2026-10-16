package com.nexcentauri.dayanisupercenter.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SettingsPrefs {
    private static final String PREFS = "dayani_settings";
    private static final String KEY_PREFERRED_PAYMENT = "preferred_payment";
    private static final String KEY_PAYMENT_ADDED = "payment_added";

    private SettingsPrefs() {}

    public static final class CardData {
        public final String type;
        public final String cardHolder;
        public final String maskedNumber;
        public final String last4;
        public final String expiry;

        public CardData(String type, String cardHolder, String maskedNumber, String last4, String expiry) {
            this.type = type;
            this.cardHolder = cardHolder;
            this.maskedNumber = maskedNumber;
            this.last4 = last4;
            this.expiry = expiry;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalize(String value) {
        if (value == null) return "Visa";
        String upper = value.trim().toUpperCase();
        switch (upper) {
            case "MASTERCARD":
            case "MASTER CARD":
                return "MasterCard";
            case "AMEX":
            case "AMERICAN EXPRESS":
                return "AMEX";
            case "VISA":
            default:
                return "Visa";
        }
    }

    private static String key(String prefix, String method) {
        return prefix + "_" + normalize(method).toLowerCase();
    }

    public static List<String> getSupportedCardTypes() {
        return Arrays.asList("Visa", "MasterCard", "AMEX");
    }

    public static String getPreferredPayment(Context context) {
        return normalize(prefs(context).getString(KEY_PREFERRED_PAYMENT, "Visa"));
    }

    public static void setPreferredPayment(Context context, String value) {
        prefs(context).edit().putString(KEY_PREFERRED_PAYMENT, normalize(value)).apply();
    }

    public static void savePaymentMethod(Context context,
                                         String method,
                                         String cardHolder,
                                         String maskedNumber,
                                         String last4,
                                         String expiry) {
        String normalized = normalize(method);
        prefs(context).edit()
                .putBoolean(KEY_PAYMENT_ADDED, true)
                .putString(KEY_PREFERRED_PAYMENT, normalized)
                .putBoolean(key("payment_added", normalized), true)
                .putString(key("card_holder", normalized), cardHolder)
                .putString(key("card_masked", normalized), maskedNumber)
                .putString(key("card_last4", normalized), last4)
                .putString(key("card_expiry", normalized), expiry)
                .apply();
    }

    public static void clearPaymentMethod(Context context) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String type : getSupportedCardTypes()) {
            editor.remove(key("payment_added", type));
            editor.remove(key("card_holder", type));
            editor.remove(key("card_masked", type));
            editor.remove(key("card_last4", type));
            editor.remove(key("card_expiry", type));
        }
        editor.remove(KEY_PAYMENT_ADDED);
        editor.remove(KEY_PREFERRED_PAYMENT);
        editor.apply();
    }

    public static void clearPaymentMethod(Context context, String method) {
        String normalized = normalize(method);
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.remove(key("payment_added", normalized));
        editor.remove(key("card_holder", normalized));
        editor.remove(key("card_masked", normalized));
        editor.remove(key("card_last4", normalized));
        editor.remove(key("card_expiry", normalized));

        List<CardData> cards = getSavedCards(context);
        String preferred = cards.isEmpty() ? null : cards.get(0).type;
        editor.putBoolean(KEY_PAYMENT_ADDED, !cards.isEmpty());
        if (preferred == null) {
            editor.remove(KEY_PREFERRED_PAYMENT);
        } else {
            editor.putString(KEY_PREFERRED_PAYMENT, preferred);
        }
        editor.apply();
    }

    public static boolean hasSavedPaymentMethod(Context context) {
        return !getSavedCards(context).isEmpty();
    }

    public static boolean hasSavedPaymentMethod(Context context, String method) {
        return prefs(context).getBoolean(key("payment_added", method), false);
    }

    public static List<CardData> getSavedCards(Context context) {
        List<CardData> cards = new ArrayList<>();
        for (String type : getSupportedCardTypes()) {
            CardData card = getSavedCard(context, type);
            if (card != null) cards.add(card);
        }
        return cards;
    }

    public static CardData getSavedCard(Context context, String method) {
        String normalized = normalize(method);
        if (!hasSavedPaymentMethod(context, normalized)) return null;
        return new CardData(
                normalized,
                prefs(context).getString(key("card_holder", normalized), "Andrew Ainsley"),
                prefs(context).getString(key("card_masked", normalized), "•••• •••• •••• 4679"),
                prefs(context).getString(key("card_last4", normalized), "4679"),
                prefs(context).getString(key("card_expiry", normalized), "09/26")
        );
    }

    public static String getSavedCardHolder(Context context) {
        CardData card = getSavedCard(context, getPreferredPayment(context));
        return card != null ? card.cardHolder : "Andrew Ainsley";
    }

    public static String getSavedCardHolder(Context context, String method) {
        CardData card = getSavedCard(context, method);
        return card != null ? card.cardHolder : "Andrew Ainsley";
    }

    public static String getSavedCardMasked(Context context) {
        CardData card = getSavedCard(context, getPreferredPayment(context));
        return card != null ? card.maskedNumber : "•••• •••• •••• 4679";
    }

    public static String getSavedCardMasked(Context context, String method) {
        CardData card = getSavedCard(context, method);
        return card != null ? card.maskedNumber : "•••• •••• •••• 4679";
    }

    public static String getSavedCardLast4(Context context) {
        CardData card = getSavedCard(context, getPreferredPayment(context));
        return card != null ? card.last4 : "4679";
    }

    public static String getSavedCardLast4(Context context, String method) {
        CardData card = getSavedCard(context, method);
        return card != null ? card.last4 : "4679";
    }

    public static String getSavedCardExpiry(Context context) {
        CardData card = getSavedCard(context, getPreferredPayment(context));
        return card != null ? card.expiry : "09/26";
    }

    public static String getSavedCardExpiry(Context context, String method) {
        CardData card = getSavedCard(context, method);
        return card != null ? card.expiry : "09/26";
    }
}
