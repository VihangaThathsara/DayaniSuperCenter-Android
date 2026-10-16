package com.nexcentauri.dayanisupercenter.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;

public final class UserProfileCache {

    private static final String PREFS = "dayani_user_profile_cache";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_PHOTO = "photo";
    private static final String KEY_PHOTO_PREVIEW_PATH = "photo_preview_path";
    private static final String KEY_PHOTO_PREVIEW_URL = "photo_preview_url";
    private static final String KEY_DOB = "dob";
    private static final String KEY_GENDER = "gender";

    private UserProfileCache() {
    }

    private static SharedPreferences prefs(Context context) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        return context.getSharedPreferences(PREFS + "_" + (uid == null ? "guest" : uid), Context.MODE_PRIVATE);
    }

    public static void save(Context context, String name, String email, String phone, String photo) {
        save(context, name, email, phone, photo, "", "");
    }

    public static void save(Context context, String name, String email, String phone, String photo, String dob, String gender) {
        if (context == null) return;
        prefs(context).edit()
                .putString(KEY_NAME, safe(name))
                .putString(KEY_EMAIL, safe(email))
                .putString(KEY_PHONE, safe(phone))
                .putString(KEY_PHOTO, safe(photo))
                .putString(KEY_DOB, safe(dob))
                .putString(KEY_GENDER, safe(gender))
                .apply();
    }

    public static String getName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    public static String getPhone(Context context) {
        return prefs(context).getString(KEY_PHONE, "");
    }

    public static String getPhoto(Context context) {
        return prefs(context).getString(KEY_PHOTO, "");
    }

    // Keep the uploaded URL in the profile; use its matching private copy for display.
    public static String getPhotoForDisplay(Context context, String photoUrl) {
        SharedPreferences cache = prefs(context);
        if (!TextUtils.isEmpty(photoUrl)
                && photoUrl.equals(cache.getString(KEY_PHOTO_PREVIEW_URL, ""))) {
            File preview = ownedPreview(context, cache.getString(KEY_PHOTO_PREVIEW_PATH, ""));
            if (preview != null && preview.isFile() && preview.length() > 0) {
                return Uri.fromFile(preview).toString();
            }
        }
        return safe(photoUrl);
    }

    public static void savePhotoPreview(Context context, String photoUrl, File photoFile) {
        if (context == null || TextUtils.isEmpty(photoUrl) || photoFile == null) return;
        File preview = ownedPreview(context, photoFile.getAbsolutePath());
        if (preview == null || !preview.isFile() || preview.length() == 0) return;
        SharedPreferences cache = prefs(context);
        File previous = ownedPreview(context, cache.getString(KEY_PHOTO_PREVIEW_PATH, ""));
        cache.edit().putString(KEY_PHOTO_PREVIEW_URL, photoUrl)
                .putString(KEY_PHOTO_PREVIEW_PATH, preview.getAbsolutePath()).apply();
        if (previous != null && !previous.equals(preview)) previous.delete();
    }

    private static File ownedPreview(Context context, String path) {
        if (TextUtils.isEmpty(path)) return null;
        try {
            File file = new File(path).getCanonicalFile();
            File directory = new File(context.getFilesDir(), "profile_photos").getCanonicalFile();
            return directory.equals(file.getParentFile()) && file.getName().startsWith("preview_")
                    ? file : null;
        } catch (IOException error) {
            return null;
        }
    }

    public static void updatePhoto(Context context, String photo) {
        if (context != null) prefs(context).edit().putString(KEY_PHOTO, safe(photo)).apply();
    }

    public static String getDob(Context context) {
        return prefs(context).getString(KEY_DOB, "");
    }

    public static String getGender(Context context) {
        return prefs(context).getString(KEY_GENDER, "");
    }

    public static void clear(Context context) {
        if (context == null) return;
        prefs(context).edit().clear().apply();
    }

    private static String safe(String value) {
        return TextUtils.isEmpty(value) ? "" : value.trim();
    }
}
