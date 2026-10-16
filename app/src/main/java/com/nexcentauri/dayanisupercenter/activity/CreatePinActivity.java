package com.nexcentauri.dayanisupercenter.activity;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.UserFlowNavigator;

import java.util.HashMap;
import java.util.Map;

public class CreatePinActivity extends AppCompatActivity {

    private ConstraintLayout rootLayout;
    private EditText etHiddenPin;
    private TextView[] pinViews;
    private MaterialButton btnContinue;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_pin);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.matchStatusBarToContent(this);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            UserFlowNavigator.launch(this, LoginActivity.class, true);
            return;
        }

        rootLayout = findViewById(R.id.rootLayout);
        ImageView btnBack = findViewById(R.id.btnBack);
        btnContinue = findViewById(R.id.btnContinue);
        etHiddenPin = findViewById(R.id.etHiddenPin);

        View pinBoxes = findViewById(R.id.pinBoxes);
        TextView txtPin1 = findViewById(R.id.txtPin1);
        TextView txtPin2 = findViewById(R.id.txtPin2);
        TextView txtPin3 = findViewById(R.id.txtPin3);
        TextView txtPin4 = findViewById(R.id.txtPin4);

        pinViews = new TextView[]{txtPin1, txtPin2, txtPin3, txtPin4};

        etHiddenPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        etHiddenPin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etHiddenPin.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        etHiddenPin.setShowSoftInputOnFocus(true);

        btnBack.setOnClickListener(v -> {
            startActivity(new android.content.Intent(CreatePinActivity.this, FillProfileActivity.class));
            finish();
        });

        pinBoxes.setOnClickListener(v -> focusPinInput());
        txtPin1.setOnClickListener(v -> focusPinInput());
        txtPin2.setOnClickListener(v -> focusPinInput());
        txtPin3.setOnClickListener(v -> focusPinInput());
        txtPin4.setOnClickListener(v -> focusPinInput());

        btnContinue.setOnClickListener(v -> savePin());

        rootLayout.setOnClickListener(v -> hideKeyboardAndClearFocus());

        etHiddenPin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPinUi(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        refreshPinUi("");

        rootLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!isTouchInsideView(pinBoxes, event)
                        && !isTouchInsideView(btnContinue, event)
                        && !isTouchInsideView(btnBack, event)) {
                    hideKeyboardAndClearFocus();
                }
            }
            return false;
        });
    }

    private boolean isTouchInsideView(View view, MotionEvent event) {
        if (view == null) return false;

        int[] location = new int[2];
        view.getLocationOnScreen(location);

        float rawX = event.getRawX();
        float rawY = event.getRawY();

        return rawX >= location[0]
                && rawX <= location[0] + view.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + view.getHeight();
    }

    private void focusPinInput() {
        etHiddenPin.setFocusable(true);
        etHiddenPin.setFocusableInTouchMode(true);
        etHiddenPin.requestFocus();
        etHiddenPin.setSelection(etHiddenPin.getText().length());

        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etHiddenPin, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboardAndClearFocus() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etHiddenPin.getWindowToken(), 0);
        }

        etHiddenPin.clearFocus();
        rootLayout.requestFocus();
    }

    private void refreshPinUi(String pin) {
        for (int i = 0; i < pinViews.length; i++) {
            TextView view = pinViews[i];

            if (i < pin.length()) {
                view.setText("●");
                view.setBackgroundResource(R.drawable.bg_pin_box_filled);
            } else if (i == pin.length() && pin.length() < 4) {
                view.setText("");
                view.setBackgroundResource(R.drawable.bg_pin_box_active);
            } else {
                view.setText("");
                view.setBackgroundResource(R.drawable.bg_pin_box);
            }
        }

        boolean isComplete = pin.length() == 4;
        btnContinue.setEnabled(isComplete);
        btnContinue.setAlpha(isComplete ? 1f : 0.45f);
    }

    private void savePin() {
        String pin = etHiddenPin.getText().toString().trim();

        if (pin.length() != 4) {
            AppMessageUtil.show(this, "Please enter your 4-digit PIN.", AppMessageUtil.TYPE_ERROR);
            return;
        }

        btnContinue.setEnabled(false);

        Map<String, Object> data = new HashMap<>();
        data.put("pin", pin);
        data.put("pinCreated", true);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> showSuccessThenHome())
                .addOnFailureListener(e -> {
                    btnContinue.setEnabled(true);
                    AppMessageUtil.show(this, "Could not save PIN. Please try again.", AppMessageUtil.TYPE_ERROR);
                });
    }

    private void showSuccessThenHome() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_success);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView txtCongrats = dialog.findViewById(R.id.txtCongrats);
        TextView txtMsg = dialog.findViewById(R.id.txtMsg);

        txtCongrats.setText("Congratulations!");
        txtMsg.setText("Your account is ready to use. You will be redirected to the Home page in a few seconds.");

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
            UserFlowNavigator.launch(this, MainActivity.class, true);
        }, 2200);
    }
}
