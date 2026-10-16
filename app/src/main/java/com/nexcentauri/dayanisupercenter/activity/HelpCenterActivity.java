package com.nexcentauri.dayanisupercenter.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

public class HelpCenterActivity extends AppCompatActivity {

    private void setupRow(int viewId, int iconRes, String title) {
        com.google.android.material.card.MaterialCardView cardView = findViewById(viewId);
        ((android.widget.ImageView) cardView.findViewById(R.id.iconStart)).setImageResource(iconRes);
        ((android.widget.TextView) cardView.findViewById(R.id.txtTitle)).setText(title);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_center);
        SystemBarUtil.matchStatusBarToContent(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setupRow(R.id.cardCall, R.drawable.ic_help_call_custom, "Customer Service");
        setupRow(R.id.cardWhatsapp, R.drawable.ic_help_whatsapp_custom, "WhatsApp");
        setupRow(R.id.cardWebsite, R.drawable.ic_help_website_custom, "Website");
        setupRow(R.id.cardEmail, R.drawable.ic_help_email_custom, "Email Support");
        findViewById(R.id.cardCall).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:0743504500"))));
        findViewById(R.id.cardWhatsapp).setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/94743504500"))));
        findViewById(R.id.cardWebsite).setOnClickListener(v -> openWebsite());
        findViewById(R.id.cardEmail).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@donline.lk"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Dayani Super Center Support Request");
            startActivity(intent);
        });
    }

    private void openWebsite() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.donline.lk/"));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        Intent browserSelector = new Intent(Intent.ACTION_MAIN);
        browserSelector.addCategory(Intent.CATEGORY_APP_BROWSER);
        intent.setSelector(browserSelector);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            AppMessageUtil.show(this, "Please install a browser to open the website.", AppMessageUtil.TYPE_ERROR);
        }
    }
}
