package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.adapter.OnboardingAdapter;
import com.nexcentauri.dayanisupercenter.model.OnboardingItem;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_APP_FLOW = "app_flow_prefs";
    private static final String KEY_ONBOARDING_SEEN = "onboarding_seen";

    private ViewPager2 viewPager;
    private LinearLayout dotsLayout;
    private MaterialButton btnNext;

    private final List<OnboardingItem> pages = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.matchStatusBarToContent(this);

        // Make status bar blend with white background
        getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
        }

        TextView btnSkip = findViewById(R.id.btnSkip);

        btnSkip.setOnClickListener(v -> {
            // Skip should jump to the LAST onboarding page
            if (viewPager != null && viewPager.getAdapter() != null) {
                int last = viewPager.getAdapter().getItemCount() - 1;
                if (last >= 0) viewPager.setCurrentItem(last, true);
            }
        });

        viewPager = findViewById(R.id.viewPager);
        dotsLayout = findViewById(R.id.dotsLayout);
        btnNext = findViewById(R.id.btnNext);

        // Start directly from the "Shop Everything You Need" page
        pages.add(new OnboardingItem(
                R.drawable.ob_1,
                "Shop Everything You Need",
                "Electronics, homeware, school items & daily essentials in one app.",
                false
        ));

        pages.add(new OnboardingItem(
                R.drawable.ob_2,
                "Easy, Fast & Reliable",
                "Search, add to cart, and order in seconds with a smooth experience.",
                false
        ));

        pages.add(new OnboardingItem(
                R.drawable.ob_3,
                "Delivery & Great Deals",
                "Track your orders, enjoy offers, and pay safely.",
                false
        ));

        viewPager.setAdapter(new OnboardingAdapter(pages));

        setupDots(pages.size());
        updateDotsAndButton(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDotsAndButton(position);
                updateSkip(position);
            }
        });

        btnNext.setOnClickListener(v -> {
            int pos = viewPager.getCurrentItem();
            if (pos < pages.size() - 1) {
                viewPager.setCurrentItem(pos + 1, true);
            } else {
                markOnboardingSeen();
                startActivity(new Intent(OnboardingActivity.this, LoginActivity.class));
                finish();
            }
        });

        // No auto-slide on the first onboarding page.
    }

    private void setupDots(int count) {
        dotsLayout.removeAllViews();
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(8, 0, 8, 0);
            dot.setLayoutParams(lp);
            dot.setImageResource(R.drawable.dot_inactive);
            dotsLayout.addView(dot);
        }
    }

    private void updateDotsAndButton(int position) {
        int count = dotsLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            ImageView dot = (ImageView) dotsLayout.getChildAt(i);
            dot.setImageResource(i == position ? R.drawable.dot_active : R.drawable.dot_inactive);
        }

        if (position == pages.size() - 1) {
            btnNext.setText("Get Started");
        } else {
            btnNext.setText("Next");
        }
    }

    private void goToLogin() {
        markOnboardingSeen();
        startActivity(new Intent(OnboardingActivity.this, LoginActivity.class));
        finish();
    }

    private void markOnboardingSeen() {
        SharedPreferences prefs = getSharedPreferences(PREFS_APP_FLOW, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply();
    }

    private void updateSkip(int position) {
        TextView btnSkip = findViewById(R.id.btnSkip);
        btnSkip.setVisibility(position == pages.size() - 1 ? View.GONE : View.VISIBLE);
    }
}
