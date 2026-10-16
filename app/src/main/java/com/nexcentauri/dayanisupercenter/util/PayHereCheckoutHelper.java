package com.nexcentauri.dayanisupercenter.util;

import android.app.Activity;
import android.content.Intent;

import lk.payhere.androidsdk.PHConfigs;
import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.Item;

public final class PayHereCheckoutHelper {

    public static final int PAYHERE_REQUEST = 11001;

    private PayHereCheckoutHelper() {
    }

    public static void startSandboxCheckout(Activity activity,
                                            String orderId,
                                            double amount,
                                            String itemsDescription,
                                            String firstName,
                                            String lastName,
                                            String email,
                                            String phone,
                                            String address,
                                            String city,
                                            String country) {

        InitRequest req = new InitRequest();
        req.setMerchantId(PayHereConfig.MERCHANT_ID);
        req.setCurrency(PayHereConfig.CURRENCY);
        req.setAmount(amount);
        req.setOrderId(orderId);
        req.setItemsDescription(itemsDescription);
        req.getCustomer().setFirstName(firstName);
        req.getCustomer().setLastName(lastName);
        req.getCustomer().setEmail(email);
        req.getCustomer().setPhone(phone);
        req.getCustomer().getAddress().setAddress(address);
        req.getCustomer().getAddress().setCity(city);
        req.getCustomer().getAddress().setCountry(country);
        if (PayHereConfig.NOTIFY_URL != null && !PayHereConfig.NOTIFY_URL.trim().isEmpty()) {
            req.setNotifyUrl(PayHereConfig.NOTIFY_URL);
        }
        req.getItems().add(new Item(null, itemsDescription, 1, amount));

        PHConfigs.setBaseUrl(PayHereConfig.USE_SANDBOX ? PHConfigs.SANDBOX_URL : PHConfigs.LIVE_URL);

        Intent intent = new Intent(activity, com.nexcentauri.dayanisupercenter.activity.TopAlignedPayHereActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);
        activity.startActivityForResult(intent, PAYHERE_REQUEST);
    }
}
