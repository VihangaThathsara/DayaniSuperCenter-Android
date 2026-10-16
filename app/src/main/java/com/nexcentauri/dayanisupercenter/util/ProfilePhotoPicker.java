package com.nexcentauri.dayanisupercenter.util;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public final class ProfilePhotoPicker {
    private final AppCompatActivity activity;
    private final Consumer<Uri> selected;
    private final ActivityResultLauncher<Uri> camera;
    private final ActivityResultLauncher<String[]> gallery;
    private Uri pendingUri;
    private File pendingFile;
    private File selectedFile;

    public ProfilePhotoPicker(AppCompatActivity activity, Bundle state, Consumer<Uri> selected) {
        this.activity = activity; this.selected = selected;
        if (state != null) {
            String uri = state.getString("camera_uri"), pending = state.getString("camera_file"), saved = state.getString("selected_camera_file");
            if (uri != null) pendingUri = Uri.parse(uri);
            if (pending != null) pendingFile = ownedFile(pending);
            if (saved != null) selectedFile = ownedFile(saved);
        }
        camera = activity.registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (Boolean.TRUE.equals(success) && pendingUri != null && pendingFile != null && pendingFile.length() > 0) {
                clearSelected();
                selectedFile = pendingFile;
                selected.accept(pendingUri);
            } else if (pendingFile != null) pendingFile.delete();
            pendingUri = null; pendingFile = null;
        });
        gallery = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try { activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            clearSelected();
            selected.accept(uri);
        });
    }
    public void show() {
        new AlertDialog.Builder(activity).setTitle("Choose profile photo")
                .setItems(new String[]{"Take photo", "Choose from gallery"}, (dialog, choice) -> {
                    if (choice == 0) takePhoto();
                    else {
                        try { gallery.launch(new String[]{"image/*"}); }
                        catch (RuntimeException error) {
                            AppMessageUtil.show(activity, "Couldn't open your gallery. Please try taking a photo.", AppMessageUtil.TYPE_ERROR);
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
    }
    private void takePhoto() {
        try {
            File directory = new File(activity.getFilesDir(), "profile_photos");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Photo folder unavailable");
            pendingFile = File.createTempFile("profile_", ".jpg", directory);
            pendingUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".profilephotos", pendingFile);
            camera.launch(pendingUri);
        } catch (IOException | RuntimeException error) {
            if (pendingFile != null) pendingFile.delete();
            pendingFile = null; pendingUri = null;
            AppMessageUtil.show(activity, "Couldn't open the camera. Please choose a photo from your gallery.", AppMessageUtil.TYPE_ERROR);
        }
    }
    public void saveState(Bundle state) {
        if (pendingUri != null) state.putString("camera_uri", pendingUri.toString());
        if (pendingFile != null) state.putString("camera_file", pendingFile.getAbsolutePath());
        if (selectedFile != null) state.putString("selected_camera_file", selectedFile.getAbsolutePath());
    }
    public void clearSelected() { if (selectedFile != null) selectedFile.delete(); selectedFile = null; }
    private File ownedFile(String path) {
        File candidate = new File(path);
        try {
            File directory = new File(activity.getFilesDir(), "profile_photos").getCanonicalFile();
            return directory.equals(candidate.getCanonicalFile().getParentFile()) ? candidate : null;
        } catch (IOException error) { return null; }
    }
}
