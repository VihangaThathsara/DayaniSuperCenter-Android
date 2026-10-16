package com.nexcentauri.dayanisupercenter.activity;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.UserFlowNavigator;

public class VerifyEmailActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    private ImageView btnBack;
    private TextView txtEmail;
    private Button btnOpenEmail, btnResend, btnIVerified;
    private ProgressBar progressBar;
    private CountDownTimer timer;
    private boolean isNavigating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);
        SystemBarUtil.matchStatusBarToContent(this);

        auth = FirebaseAuth.getInstance();

        btnBack = findViewById(R.id.btnBack);
        txtEmail = findViewById(R.id.txtEmail);
        btnOpenEmail = findViewById(R.id.btnOpenEmail);
        btnResend = findViewById(R.id.btnResend);
        btnIVerified = findViewById(R.id.btnIVerified);
        progressBar = findViewById(R.id.progressBar);

        btnBack.setOnClickListener(v -> finish());

        String email = getIntent().getStringExtra("email");
        if (email != null) {
            txtEmail.setText(email);
        }

        btnOpenEmail.setOnClickListener(v -> openEmailApp());
        btnResend.setOnClickListener(v -> resend());
        btnIVerified.setOnClickListener(v -> checkVerifiedAndContinue());

        startResendCooldown(60);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkVerifiedSilent();
    }

    private void openEmailApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_EMAIL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"));
            startActivity(fallback);
        }
    }

    private void resend() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            AppMessageUtil.showVerify(this, "Session expired. Please sign in again.", AppMessageUtil.TYPE_WARNING);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setLoading(true);
        user.sendEmailVerification().addOnCompleteListener(task -> {
            setLoading(false);
            if (task.isSuccessful()) {
                AppMessageUtil.showVerify(this, "Verification email sent again. Please check your inbox or spam folder.", AppMessageUtil.TYPE_SUCCESS);
                startResendCooldown(60);
            } else {
                AppMessageUtil.showVerify(this, "Couldn't resend email right now. Please try again.", AppMessageUtil.TYPE_ERROR);
            }
        });
    }

    private void checkVerifiedAndContinue() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            AppMessageUtil.showVerify(this, "Session expired. Please sign in again.", AppMessageUtil.TYPE_WARNING);
            return;
        }

        setLoading(true);
        user.reload().addOnCompleteListener(task -> {
            setLoading(false);
            FirebaseUser refreshed = auth.getCurrentUser();
            if (refreshed != null && refreshed.isEmailVerified()) {
                showSuccessThenHome();
            } else {
                AppMessageUtil.showVerify(this, "Your email is not verified yet.", AppMessageUtil.TYPE_INFO);
            }
        });
    }

    private void checkVerifiedSilent() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return;
        }

        user.reload().addOnCompleteListener(task -> {
            FirebaseUser refreshed = auth.getCurrentUser();
            if (refreshed != null && refreshed.isEmailVerified()) {
                showSuccessThenHome();
            }
        });
    }

    private void showSuccessThenHome() {
        if (isNavigating) {
            return;
        }
        isNavigating = true;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_success);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        TextView txtCongrats = dialog.findViewById(R.id.txtCongrats);
        TextView txtMsg = dialog.findViewById(R.id.txtMsg);
        txtCongrats.setText("Verified Successfully!");
        txtMsg.setText("Your email has been verified. You will be redirected to complete your profile.");
        dialog.show();
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            int screenWidth = getWindow().getDecorView().getWidth();
            if (screenWidth <= 0) screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxWidth = Math.round(360f * getResources().getDisplayMetrics().density);
            dialogWindow.setLayout(Math.min(maxWidth, Math.round(screenWidth * 0.88f)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            dialog.dismiss();
            goNext();
        }, 2200);
    }

    private void goNext() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            UserFlowNavigator.launch(this, LoginActivity.class, true);
            return;
        }
        UserFlowNavigator.routeAfterAuth(this, user, true);
    }

    private void startResendCooldown(int seconds) {
        btnResend.setEnabled(false);
        btnResend.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E9E9E9")));
        btnResend.setTextColor(Color.parseColor("#8A8A8A"));
        if (timer != null) {
            timer.cancel();
        }

        timer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long s = millisUntilFinished / 1000L;
                btnResend.setText("Resend (" + s + "s)");
            }

            @Override
            public void onFinish() {
                btnResend.setText("Resend Verification Email");
                btnResend.setEnabled(true);
                btnResend.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#16A34A")));
                btnResend.setTextColor(Color.WHITE);
            }
        }.start();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnOpenEmail.setEnabled(!loading);
        btnIVerified.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
