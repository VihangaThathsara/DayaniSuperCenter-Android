package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;

public class PasswordResetEmailSentActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    private ImageView btnBack;
    private TextView txtEmail;
    private Button btnOpenEmail;
    private Button btnResend;
    private TextView txtBackToLogin;
    private ProgressBar progressBar;
    private CountDownTimer timer;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_reset_email_sent);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.matchStatusBarToContent(this);

        auth = FirebaseAuth.getInstance();

        btnBack = findViewById(R.id.btnBack);
        txtEmail = findViewById(R.id.txtEmail);
        btnOpenEmail = findViewById(R.id.btnOpenEmail);
        btnResend = findViewById(R.id.btnResend);
        txtBackToLogin = findViewById(R.id.txtBackToLogin);
        progressBar = findViewById(R.id.progressBar);

        email = getIntent().getStringExtra("email");
        if (email != null && !email.trim().isEmpty()) {
            txtEmail.setText(email.trim());
        }

        btnBack.setOnClickListener(v -> goToLogin());
        txtBackToLogin.setOnClickListener(v -> goToLogin());
        btnOpenEmail.setOnClickListener(v -> openEmailApp());
        btnResend.setOnClickListener(v -> resendResetLink());

        startResendCooldown(45);
    }

    private void resendResetLink() {
        if (email == null || email.trim().isEmpty()) {
            AppMessageUtil.show(this, getString(R.string.reset_link_failed_message), AppMessageUtil.TYPE_ERROR);
            return;
        }

        setLoading(true);
        auth.sendPasswordResetEmail(email.trim())
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        AppMessageUtil.show(
                                this,
                                getString(R.string.reset_link_resent_message),
                                AppMessageUtil.TYPE_SUCCESS
                        );
                        startResendCooldown(45);
                    } else {
                        AppMessageUtil.show(
                                this,
                                getString(R.string.reset_link_failed_message),
                                AppMessageUtil.TYPE_ERROR
                        );
                    }
                });
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

    private void startResendCooldown(int seconds) {
        btnResend.setEnabled(false);
        btnResend.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E9F8ED")));
        btnResend.setTextColor(Color.parseColor("#63A36E"));

        if (timer != null) {
            timer.cancel();
        }

        timer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long remaining = millisUntilFinished / 1000L;
                btnResend.setText(getString(R.string.resend_reset_link_countdown, remaining));
            }

            @Override
            public void onFinish() {
                btnResend.setText(R.string.resend_reset_link);
                btnResend.setEnabled(true);
                btnResend.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10AD32")));
                btnResend.setTextColor(Color.WHITE);
            }
        }.start();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnOpenEmail.setEnabled(!loading);
        btnBack.setEnabled(!loading);
        txtBackToLogin.setEnabled(!loading);
        btnResend.setEnabled(!loading && btnResend.isEnabled());
        btnOpenEmail.setAlpha(loading ? 0.70f : 1f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
