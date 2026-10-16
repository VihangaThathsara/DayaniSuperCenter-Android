package com.nexcentauri.dayanisupercenter.util;

/** Copy to the util package as PayHereConfig.java and configure locally. */
public final class PayHereConfig {
    private PayHereConfig() {
    }

    public static final String MERCHANT_ID = "YOUR_SANDBOX_MERCHANT_ID";
    public static final String PACKAGE_NAME = "com.nexcentauri.dayanisupercenter";
    public static final String CURRENCY = "LKR";
    public static final boolean USE_SANDBOX = true;

    // Configure a trusted server endpoint before accepting live payments.
    // Keep merchant secrets and payment verification on that server.
    public static final String NOTIFY_URL = "";
}
