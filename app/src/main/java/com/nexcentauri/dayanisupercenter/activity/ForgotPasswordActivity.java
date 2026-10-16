package com.nexcentauri.dayanisupercenter.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;

public class ForgotPasswordActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    private ImageView btnBack;
    private TextInputEditText edtEmail;
    private TextView txtEmailError;
    private Button btnSendReset;
    private TextView txtBackToLogin;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        SystemBarUtil.matchStatusBarToContent(this);

        auth = FirebaseAuth.getInstance();

        btnBack = findViewById(R.id.btnBack);
        edtEmail = findViewById(R.id.edtEmail);
        txtEmailError = findViewById(R.id.txtEmailError);
        btnSendReset = findViewById(R.id.btnSendReset);
        txtBackToLogin = findViewById(R.id.txtBackToLogin);
        progressBar = findViewById(R.id.progressBar);

        String prefillEmail = getIntent().getStringExtra("email");
        if (prefillEmail != null && !prefillEmail.trim().isEmpty()) {
            edtEmail.setText(prefillEmail.trim());
            edtEmail.setSelection(edtEmail.getText() != null ? edtEmail.getText().length() : 0);
        }

        btnBack.setOnClickListener(v -> finish());
        txtBackToLogin.setOnClickListener(v -> finish());
        btnSendReset.setOnClickListener(v -> sendResetLink());

        edtEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearEmailError(); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void sendResetLink() {
        if (!validateEmail()) {
            return;
        }

        hideKeyboard();

        String email = edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "";
        setLoading(true);

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Intent intent = new Intent(ForgotPasswordActivity.this, PasswordResetEmailSentActivity.class);
                        intent.putExtra("email", email);
                        startActivity(intent);
                    } else {
                        AppMessageUtil.show(
                                ForgotPasswordActivity.this,
                                getString(R.string.reset_link_failed_message),
                                AppMessageUtil.TYPE_ERROR
                        );
                    }
                });
    }

    private boolean validateEmail() {
        String email = edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "";

        if (email.isEmpty()) {
            showEmailError(getString(R.string.enter_email_error));
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showEmailError(getString(R.string.invalid_email_error));
            return false;
        }

        clearEmailError();
        return true;
    }

    private void showEmailError(String message) {
        txtEmailError.setText(message);
        txtEmailError.setVisibility(View.VISIBLE);
    }

    private void clearEmailError() {
        txtEmailError.setText("");
        txtEmailError.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSendReset.setEnabled(!loading);
        btnBack.setEnabled(!loading);
        txtBackToLogin.setEnabled(!loading);
        edtEmail.setEnabled(!loading);
        btnSendReset.setAlpha(loading ? 0.70f : 1f);
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view == null) {
            view = edtEmail;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
