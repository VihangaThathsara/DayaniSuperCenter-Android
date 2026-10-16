package com.nexcentauri.dayanisupercenter.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProfilePhotoStorageUtil {

    private static final int MAX_PHOTO_SIZE = 768;
    private static final ExecutorService PHOTO_EXECUTOR = Executors.newSingleThreadExecutor();

    public interface PreparationCallback {
        void onSuccess(@NonNull File photoFile);
        void onFailure();
    }

    public interface UploadCallback {
        void onSuccess(@NonNull String downloadUrl, @NonNull String storagePath);
        void onFailure();
    }

    private ProfilePhotoStorageUtil() {
    }

    // Decode at profile size off the UI thread, keeping camera EXIF orientation.
    public static void prepareProfilePhoto(@NonNull Context context,
                                           @NonNull Uri localUri,
                                           @NonNull PreparationCallback callback) {
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        PHOTO_EXECUTOR.execute(() -> {
            File photoFile = null;
            Bitmap bitmap = null;
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(appContext.getContentResolver(), localUri);
                bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, ignored) -> {
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    float scale = Math.min(1f, (float) MAX_PHOTO_SIZE / Math.max(width, height));
                    decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
                File directory = new File(appContext.getFilesDir(), "profile_photos");
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new IOException("Photo folder unavailable");
                }
                photoFile = File.createTempFile("preview_", ".jpg", directory);
                try (FileOutputStream output = new FileOutputStream(photoFile)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                        throw new IOException("Could not prepare photo");
                    }
                }
                File preparedFile = photoFile;
                mainHandler.post(() -> callback.onSuccess(preparedFile));
            } catch (IOException | RuntimeException error) {
                if (photoFile != null) photoFile.delete();
                mainHandler.post(callback::onFailure);
            } finally {
                if (bitmap != null) bitmap.recycle();
            }
        });
    }

    public static void uploadProfilePhoto(@NonNull String uid,
                                          @NonNull Uri localUri,
                                          String oldStoragePath,
                                          @NonNull UploadCallback callback) {
        uploadNew(uid, localUri, callback);
    }

    private static void uploadNew(@NonNull String uid,
                                  @NonNull Uri localUri,
                                  @NonNull UploadCallback callback) {
        String storagePath = "users/" + uid + "/profile/profile_" + System.currentTimeMillis() + ".jpg";
        StorageReference reference = FirebaseStorage.getInstance().getReference().child(storagePath);

        reference.putFile(localUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null ? task.getException() : new IllegalStateException("Upload failed");
                    }
                    return reference.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> callback.onSuccess(uri.toString(), storagePath))
                .addOnFailureListener(e -> callback.onFailure());
    }

    public static void deleteByPath(String storagePath) {
        if (TextUtils.isEmpty(storagePath)) {
            return;
        }
        FirebaseStorage.getInstance().getReference().child(normalize(storagePath)).delete();
    }

    private static String normalize(@NonNull String path) {
        String normalized = path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
