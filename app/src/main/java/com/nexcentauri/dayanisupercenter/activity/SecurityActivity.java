package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

public class SecurityActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);
        SystemBarUtil.matchStatusBarToContent(this);

        MaterialButton btnChangePin = findViewById(R.id.btnChangePin);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnChangePin.setOnClickListener(v -> startActivity(new Intent(this, ChangePinActivity.class)));
    }
}
