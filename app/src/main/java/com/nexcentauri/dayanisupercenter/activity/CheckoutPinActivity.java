package com.nexcentauri.dayanisupercenter.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

public class CheckoutPinActivity extends AppCompatActivity {

    private EditText etHiddenPin;
    private TextView[] pinViews;
    private MaterialButton btnContinue;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout_pin);
        SystemBarUtil.matchStatusBarToContent(this);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        bindViews();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnContinue.setOnClickListener(v -> validatePinAndContinue());
        findViewById(R.id.pinBoxes).setOnClickListener(v -> focusPinInput());

        etHiddenPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        etHiddenPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etHiddenPin.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        etHiddenPin.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refreshPinUi(s.toString()); }
        });
        refreshPinUi("");
        etHiddenPin.post(this::focusPinInput);
    }

    private void bindViews() {
        etHiddenPin = findViewById(R.id.etHiddenPin);
        pinViews = new TextView[]{findViewById(R.id.txtPin1), findViewById(R.id.txtPin2), findViewById(R.id.txtPin3), findViewById(R.id.txtPin4)};
        btnContinue = findViewById(R.id.btnContinue);
    }

    private void focusPinInput() {
        etHiddenPin.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etHiddenPin, InputMethodManager.SHOW_IMPLICIT);
    }

    private void refreshPinUi(String pin) {
        for (int i = 0; i < pinViews.length; i++) {
            if (i < pin.length()) {
                pinViews[i].setText("●");
                pinViews[i].setBackgroundResource(R.drawable.bg_pin_box_filled);
            } else if (i == pin.length() && pin.length() < 4) {
                pinViews[i].setText("");
                pinViews[i].setBackgroundResource(R.drawable.bg_pin_box_active);
            } else {
                pinViews[i].setText("");
                pinViews[i].setBackgroundResource(R.drawable.bg_pin_box);
            }
        }
        boolean enabled = pin.length() == 4;
        btnContinue.setEnabled(enabled);
        btnContinue.setAlpha(enabled ? 1f : 0.45f);
    }

    private void validatePinAndContinue() {
        String enteredPin = etHiddenPin.getText() == null ? "" : etHiddenPin.getText().toString().trim();
        if (enteredPin.length() != 4) {
            AppMessageUtil.show(this, "Enter your 4-digit PIN.", AppMessageUtil.TYPE_ERROR);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    String savedPin = snapshot.getString("pin");
                    if (enteredPin.equals(savedPin)) {
                        hideKeyboard();
                        btnContinue.setEnabled(false);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(this, PayHereGatewayActivity.class);
                            startActivity(intent);
                            finish();
                        }, 150);
                    } else {
                        AppMessageUtil.show(this, "Incorrect PIN.", AppMessageUtil.TYPE_ERROR);
                    }
                })
                .addOnFailureListener(e -> AppMessageUtil.show(this, "Could not verify PIN.", AppMessageUtil.TYPE_ERROR));
    }
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etHiddenPin.getWindowToken(), 0);
        }
        etHiddenPin.clearFocus();
    }

}
