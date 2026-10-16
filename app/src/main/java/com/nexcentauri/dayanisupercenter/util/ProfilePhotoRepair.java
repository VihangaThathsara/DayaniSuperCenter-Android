package com.nexcentauri.dayanisupercenter.util;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Recover older profiles that have a Storage path or device URI without a web URL. */
public final class ProfilePhotoRepair {
    private static final Set<String> attempted = new HashSet<>();
    private ProfilePhotoRepair() {}

    public static void repair(Context context, String uid, DocumentSnapshot profile) {
        if (isWebUrl(profile.getString("photoUrl"))) return;
        String path = value(profile.getString("photoStoragePath"));
        String local = value(profile.getString("localPhotoUri"));
        if (local.isEmpty()) local = value(profile.getString("photoUrl"));
        String source = path.isEmpty() ? local : path;
        if (source.isEmpty() || !attempted.add(uid + ":" + source)) return;
        final String expectedSource = source;
        final Context appContext = context.getApplicationContext();
        if (!path.isEmpty()) {
            FirebaseImageLoader.resolveToDownloadUrl(path, new FirebaseImageLoader.UrlCallback() {
                @Override public void onResolved(@NonNull String url) {
                    persist(appContext, uid, expectedSource, url, path, false);
                }
                @Override public void onFailed() { }
            });
        } else if ("content".equals(Uri.parse(local).getScheme()) || "file".equals(Uri.parse(local).getScheme())) {
            ProfilePhotoStorageUtil.uploadProfilePhoto(uid, Uri.parse(local), "", new ProfilePhotoStorageUtil.UploadCallback() {
                @Override public void onSuccess(@NonNull String url, @NonNull String uploadedPath) {
                    persist(appContext, uid, expectedSource, url, uploadedPath, true);
                }
                @Override public void onFailure() { }
            });
        }
    }

    private static void persist(Context context, String uid, String expected, String url, String path, boolean uploaded) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentReference userRef = db.collection("users").document(uid);
            DocumentSnapshot current = transaction.get(userRef);
            if (!current.exists() || isWebUrl(current.getString("photoUrl"))) return false;
            String currentSource = value(current.getString("photoStoragePath"));
            if (currentSource.isEmpty()) currentSource = value(current.getString("localPhotoUri"));
            if (currentSource.isEmpty()) currentSource = value(current.getString("photoUrl"));
            if (!expected.equals(currentSource)) return false;
            Map<String, Object> fields = new HashMap<>();
            fields.put("photoUrl", url);
            fields.put("photoStoragePath", path);
            fields.put("localPhotoUri", FieldValue.delete());
            transaction.update(userRef, fields);
            return true;
        }).addOnSuccessListener(saved -> {
            if (!saved) {
                if (uploaded) ProfilePhotoStorageUtil.deleteByPath(path);
                return;
            }
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && uid.equals(user.getUid())) {
                UserProfileCache.updatePhoto(context, url);
                user.updateProfile(new UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(url)).build());
            }
        });
    }

    private static boolean isWebUrl(String value) {
        if (TextUtils.isEmpty(value)) return false;
        Uri uri = Uri.parse(value.trim());
        return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && !TextUtils.isEmpty(uri.getHost());
    }

    private static String value(String text) { return text == null ? "" : text.trim(); }
}
