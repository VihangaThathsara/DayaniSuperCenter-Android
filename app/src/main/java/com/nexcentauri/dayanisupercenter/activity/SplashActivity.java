package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.nexcentauri.dayanisupercenter.R;


public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_APP_FLOW = "app_flow_prefs";
    private static final String KEY_ONBOARDING_SEEN = "onboarding_seen";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.matchStatusBarToContent(this);
        routeNext();
    }

    private void routeNext() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_APP_FLOW, MODE_PRIVATE);
            Class<?> next = prefs.getBoolean(KEY_ONBOARDING_SEEN, false)
                    ? LoginActivity.class
                    : OnboardingActivity.class;
            startActivity(new Intent(SplashActivity.this, next));
            finish();
            return;
        }

        if (hasPasswordProvider(user) && !user.isEmailVerified()) {
            Intent intent = new Intent(SplashActivity.this, VerifyEmailActivity.class);
            intent.putExtra("email", user.getEmail());
            startActivity(intent);
            finish();
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                .addOnSuccessListener(snapshot -> routeAuthenticatedUser(snapshot, user))
                .addOnFailureListener(e -> {
                    startActivity(new Intent(SplashActivity.this, FillProfileActivity.class));
                    finish();
                });
    }

    private void routeAuthenticatedUser(@NonNull DocumentSnapshot snapshot, @NonNull FirebaseUser user) {
        boolean profileCompleted = snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("profileCompleted"));
        boolean pinCreated = snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("pinCreated"));

        if (!profileCompleted) {
            startActivity(new Intent(SplashActivity.this, FillProfileActivity.class));
            finish();
            return;
        }

        if (!pinCreated) {
            startActivity(new Intent(SplashActivity.this, CreatePinActivity.class));
            finish();
            return;
        }

        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();
    }

    private boolean hasPasswordProvider(FirebaseUser user) {
        for (UserInfo info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) {
                return true;
            }
        }
        return false;
    }
}