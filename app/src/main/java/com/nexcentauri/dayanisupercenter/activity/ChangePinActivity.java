package com.nexcentauri.dayanisupercenter.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.method.HideReturnsTransformationMethod;
import android.view.WindowManager;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

import java.util.HashMap;
import java.util.Map;

public class ChangePinActivity extends AppCompatActivity {

    private TextInputLayout layoutPin;
    private TextInputLayout layoutConfirmPin;
    private TextInputEditText edtPin;
    private TextInputEditText edtConfirmPin;
    private boolean isPinVisible = false;
    private boolean isConfirmPinVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_pin);
        SystemBarUtil.matchStatusBarToContent(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        layoutPin = findViewById(R.id.layoutPin);
        layoutConfirmPin = findViewById(R.id.layoutConfirmPin);
        edtPin = findViewById(R.id.edtPin);
        edtConfirmPin = findViewById(R.id.edtConfirmPin);
        MaterialButton btnSave = findViewById(R.id.btnSavePin);

        edtPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        edtConfirmPin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        applyHiddenState(edtPin, false);
        applyHiddenState(edtConfirmPin, false);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        layoutPin.setEndIconOnClickListener(v -> {
            isPinVisible = !isPinVisible;
            applyHiddenState(edtPin, isPinVisible);
            layoutPin.setEndIconDrawable(isPinVisible ? R.drawable.ic_eye_open : R.drawable.ic_eye_closed);
        });

        layoutConfirmPin.setEndIconOnClickListener(v -> {
            isConfirmPinVisible = !isConfirmPinVisible;
            applyHiddenState(edtConfirmPin, isConfirmPinVisible);
            layoutConfirmPin.setEndIconDrawable(isConfirmPinVisible ? R.drawable.ic_eye_open : R.drawable.ic_eye_closed);
        });

        edtPin.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validatePinField(false);
            }
        });

        edtConfirmPin.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && !getValue(edtConfirmPin).isEmpty()) {
                validateConfirmPinField(false);
            }
        });

        edtPin.addTextChangedListener(simpleWatcher(() -> {
            layoutPin.setError(null);
            if (getValue(edtPin).length() == 4) {
                edtConfirmPin.requestFocus();
            }
        }));

        edtConfirmPin.addTextChangedListener(simpleWatcher(() -> {
            layoutConfirmPin.setError(null);
        }));

        edtConfirmPin.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnSave.performClick();
                return true;
            }
            return false;
        });

        btnSave.setOnClickListener(v -> {
            if (!validatePinField(true)) {
                return;
            }
            if (!validateConfirmPinField(true)) {
                return;
            }

            String uid = FirebaseAuth.getInstance().getUid();
            if (uid == null) {
                AppMessageUtil.show(this, "User not found.", AppMessageUtil.TYPE_ERROR);
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("pin", getValue(edtPin));
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        AppMessageUtil.show(this, "PIN updated successfully.", AppMessageUtil.TYPE_SUCCESS);
                        finish();
                    })
                    .addOnFailureListener(e -> AppMessageUtil.show(this, "Could not update PIN.", AppMessageUtil.TYPE_ERROR));
        });
    }

    private void applyHiddenState(EditText editText, boolean showText) {
        int cursorPosition = editText.getSelectionStart() >= 0 ? editText.getSelectionStart() : editText.length();
        editText.setTransformationMethod(showText ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
        editText.setSelection(Math.max(cursorPosition, 0));
    }

    private boolean validatePinField(boolean focusOnError) {
        String pin = getValue(edtPin);
        if (pin.isEmpty()) {
            layoutPin.setError("Enter a 4-digit PIN.");
            if (focusOnError) edtPin.requestFocus();
            return false;
        }
        if (pin.length() != 4) {
            layoutPin.setError("PIN must be exactly 4 digits.");
            if (focusOnError) edtPin.requestFocus();
            return false;
        }
        layoutPin.setError(null);
        return true;
    }

    private boolean validateConfirmPinField(boolean focusOnError) {
        String pin = getValue(edtPin);
        String confirmPin = getValue(edtConfirmPin);

        if (confirmPin.isEmpty()) {
            layoutConfirmPin.setError("Confirm your 4-digit PIN.");
            if (focusOnError) edtConfirmPin.requestFocus();
            return false;
        }
        if (confirmPin.length() != 4) {
            layoutConfirmPin.setError("Confirm PIN must be exactly 4 digits.");
            if (focusOnError) edtConfirmPin.requestFocus();
            return false;
        }
        if (!confirmPin.equals(pin)) {
            layoutConfirmPin.setError("PIN confirmation does not match.");
            if (focusOnError) edtConfirmPin.requestFocus();
            return false;
        }
        layoutConfirmPin.setError(null);
        return true;
    }

    private String getValue(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private TextWatcher simpleWatcher(Runnable afterTextChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                afterTextChanged.run();
            }
        };
    }
}
