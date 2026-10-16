package com.nexcentauri.dayanisupercenter.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);
        SystemBarUtil.matchStatusBarToContent(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
