package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.fragment.CartFragment;
import com.nexcentauri.dayanisupercenter.fragment.HomeFragment;
import com.nexcentauri.dayanisupercenter.fragment.OrdersFragment;
import com.nexcentauri.dayanisupercenter.fragment.ProfileFragment;

public class MainActivity extends AppCompatActivity {

    public static final String TAB_HOME = "home";
    public static final String TAB_CART = "cart";
    public static final String TAB_ORDERS = "orders";
    public static final String TAB_PROFILE = "profile";

    private LinearLayout bottomNavContainer;
    private String currentTab = TAB_HOME;
    private View homeLoadingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bottomNavContainer = findViewById(R.id.bottomNavContainer);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.applyWindowInsets(this);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        setupBottomNav();

        String firstTab = savedInstanceState == null ? resolveInitialTab(getIntent())
                : savedInstanceState.getString("current_tab", TAB_HOME);
        if (TAB_HOME.equals(firstTab)) {
            ViewGroup content = findViewById(android.R.id.content);
            homeLoadingView = getLayoutInflater().inflate(R.layout.activity_splash, content, false);
            homeLoadingView.setClickable(true);
            content.addView(homeLoadingView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (savedInstanceState == null) {
            String initialTab = resolveInitialTab(getIntent());
            switchToTab(initialTab, false);
        } else {
            currentTab = savedInstanceState.getString("current_tab", TAB_HOME);
            selectBottomNav(navIdForTab(currentTab));
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!TAB_HOME.equals(currentTab)) {
                    switchToTab(TAB_HOME, false);
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String tab = resolveInitialTab(intent);
        if (!tab.equals(currentTab)) {
            switchToTab(tab, false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("current_tab", currentTab);
    }

    public void openTab(String tab) {
        switchToTab(tab, false);
    }

    public boolean isHomeLoading() { return homeLoadingView != null; }

    public void completeHomeLoading() {
        if (homeLoadingView == null) return;
        ((ViewGroup) homeLoadingView.getParent()).removeView(homeLoadingView);
        homeLoadingView = null;
    }

    private String resolveInitialTab(Intent intent) {
        if (intent == null) {
            return TAB_HOME;
        }
        boolean openOrders = intent.getBooleanExtra("open_orders", false)
                || (intent.getStringExtra("open_order_id") != null && !intent.getStringExtra("open_order_id").trim().isEmpty());
        return openOrders ? TAB_ORDERS : TAB_HOME;
    }

    private void setupBottomNav() {
        LinearLayout navHomeItem = findViewById(R.id.navHomeItem);
        LinearLayout navCartItem = findViewById(R.id.navCartItem);
        LinearLayout navOrdersItem = findViewById(R.id.navOrdersItem);
        LinearLayout navProfileItem = findViewById(R.id.navProfileItem);

        navHomeItem.setOnClickListener(v -> switchToTab(TAB_HOME, false));
        navCartItem.setOnClickListener(v -> switchToTab(TAB_CART, false));
        navOrdersItem.setOnClickListener(v -> switchToTab(TAB_ORDERS, false));
        navProfileItem.setOnClickListener(v -> switchToTab(TAB_PROFILE, false));
    }

    private void switchToTab(String tab, boolean forceRecreate) {
        if (!TAB_HOME.equals(tab)) completeHomeLoading();
        Fragment target = fragmentForTab(tab, forceRecreate);
        if (target == null) {
            return;
        }

        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag(currentTab);

        if (currentFragment != null && currentFragment != target) {
            transaction.hide(currentFragment);
        }

        if (target.isAdded()) {
            transaction.show(target);
        } else {
            transaction.add(R.id.mainFragmentContainer, target, tab);
        }

        currentTab = tab;
        transaction.commit();
        selectBottomNav(navIdForTab(tab));
    }

    private Fragment fragmentForTab(String tab, boolean forceRecreate) {
        if (!forceRecreate) {
            Fragment existing = getSupportFragmentManager().findFragmentByTag(tab);
            if (existing != null) {
                return existing;
            }
        }
        switch (tab) {
            case TAB_CART:
                return new CartFragment();
            case TAB_ORDERS:
                return OrdersFragment.newInstance(getIntent() != null ? getIntent().getStringExtra("open_order_id") : null);
            case TAB_PROFILE:
                return new ProfileFragment();
            case TAB_HOME:
            default:
                return new HomeFragment();
        }
    }

    private int navIdForTab(String tab) {
        switch (tab) {
            case TAB_CART:
                return R.id.nav_cart;
            case TAB_ORDERS:
                return R.id.nav_orders;
            case TAB_PROFILE:
                return R.id.nav_profile;
            case TAB_HOME:
            default:
                return R.id.nav_home;
        }
    }

    private void selectBottomNav(int selectedId) {
        updateNavItem(R.id.navHomeIconBg, R.id.navHomeIcon, R.id.navHomeLabel, selectedId == R.id.nav_home);
        updateNavItem(R.id.navCartIconBg, R.id.navCartIcon, R.id.navCartLabel, selectedId == R.id.nav_cart);
        updateNavItem(R.id.navOrdersIconBg, R.id.navOrdersIcon, R.id.navOrdersLabel, selectedId == R.id.nav_orders);
        updateNavItem(R.id.navProfileIconBg, R.id.navProfileIcon, R.id.navProfileLabel, selectedId == R.id.nav_profile);
    }

    private void updateNavItem(int bgId, int iconId, int labelId, boolean selected) {
        FrameLayout iconBg = findViewById(bgId);
        ImageView icon = findViewById(iconId);
        TextView label = findViewById(labelId);

        iconBg.setBackgroundResource(selected ? R.drawable.bg_nav_selected_icon : android.R.color.transparent);
        icon.setColorFilter(getColor(selected ? android.R.color.black : android.R.color.darker_gray));
        label.setTextColor(getColor(selected ? android.R.color.black : android.R.color.darker_gray));
        label.setTypeface(label.getTypeface(), selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
