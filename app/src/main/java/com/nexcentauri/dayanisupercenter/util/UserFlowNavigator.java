package com.nexcentauri.dayanisupercenter.util;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;
import com.nexcentauri.dayanisupercenter.activity.CreatePinActivity;
import com.nexcentauri.dayanisupercenter.activity.FillProfileActivity;
import com.nexcentauri.dayanisupercenter.activity.MainActivity;

import java.util.HashMap;
import java.util.Date;
import java.util.Map;

public final class UserFlowNavigator {

    private UserFlowNavigator() {}

    public static void routeAfterAuth(@NonNull Activity activity, @NonNull FirebaseUser user, boolean clearTask) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    boolean profileCompleted = snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("profileCompleted"));
                    boolean pinCreated = snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("pinCreated"));

                    Class<?> next;
                    if (!profileCompleted) {
                        next = FillProfileActivity.class;
                    } else if (!pinCreated) {
                        next = CreatePinActivity.class;
                    } else {
                        next = MainActivity.class;
                    }
                    launch(activity, next, clearTask);
                })
                .addOnFailureListener(e -> {
                    long authCreatedMillis = user.getMetadata() != null
                            ? user.getMetadata().getCreationTimestamp()
                            : System.currentTimeMillis();
                    Timestamp joinedAt = new Timestamp(new Date(authCreatedMillis));

                    Map<String, Object> seed = new HashMap<>();
                    seed.put("uid", uid);
                    if (user.getEmail() != null) {
                        seed.put("email", user.getEmail());
                    }
                    seed.put("createdAt", FieldValue.serverTimestamp());
                    seed.put("createdAtClient", joinedAt);
                    db.collection("users").document(uid).set(seed, SetOptions.merge());
                    launch(activity, FillProfileActivity.class, clearTask);
                });
    }

    public static void launch(@NonNull Activity activity, @NonNull Class<?> target, boolean clearTask) {
        Intent intent = new Intent(activity, target);
        if (clearTask) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        activity.startActivity(intent);
        activity.finish();
    }
}
