package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

public class OrdersActivity extends AppCompatActivity {

    private boolean goHomeOnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders_placeholder);
        SystemBarUtil.matchStatusBarToContent(this);
        goHomeOnBack = getIntent().getBooleanExtra("go_home_on_back", false);
        findViewById(R.id.btnBack).setOnClickListener(v -> handleBackNavigation());
        String orderId = getIntent().getStringExtra("order_id");
        ((TextView) findViewById(R.id.txtSubtitle)).setText(orderId == null ? "Orders screen will be built next." : "Latest order: " + orderId + "\nOrders screen will be built next.");
        findViewById(R.id.btnGoHome).setOnClickListener(v -> openHome());
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (goHomeOnBack) {
            openHome();
            return;
        }
        finish();
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
