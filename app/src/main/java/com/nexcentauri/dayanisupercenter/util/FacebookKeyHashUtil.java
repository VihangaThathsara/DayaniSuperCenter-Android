package com.nexcentauri.dayanisupercenter.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import androidx.appcompat.app.AlertDialog;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class FacebookKeyHashUtil {
    private FacebookKeyHashUtil() {}

    public static List<String> getInstalledKeyHashes(Context context) {
        List<String> hashes = new ArrayList<>();
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) return hashes;
            for (Signature signature : info.signingInfo.getApkContentsSigners()) {
                String hash = Base64.encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest(signature.toByteArray()), Base64.NO_WRAP);
                if (!hashes.contains(hash)) hashes.add(hash);
            }
        } catch (Exception ignored) { }
        return hashes;
    }

    public static void showSetupHelp(Context context) {
        String hashes = android.text.TextUtils.join("\n", getInstalledKeyHashes(context));
        new AlertDialog.Builder(context)
                .setTitle("Facebook sign-in needs setup")
                .setMessage("Register this installed app's key hash in Meta Developer Dashboard → Android → Key Hashes.\n\n"
                        + context.getPackageName() + "\n" + hashes)
                .setPositiveButton("Copy key hash", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Facebook key hash", hashes));
                })
                .setNegativeButton("Close", null)
                .show();
    }
}
