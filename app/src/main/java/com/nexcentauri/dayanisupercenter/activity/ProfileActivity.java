package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.facebook.login.LoginManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.SupportChatBadgeUtil;
import com.nexcentauri.dayanisupercenter.util.UserProfileCache;

public class ProfileActivity extends AppCompatActivity {

    private ImageView imgProfile;
    private View supportUnreadDot;
    private ListenerRegistration supportDotListener;
    private TextView txtName;
    private TextView txtContact;
    private Switch switchDarkMode;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        SystemBarUtil.matchStatusBarToContent(this);

        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        imgProfile = findViewById(R.id.imgProfile);
        txtName = findViewById(R.id.txtName);
        txtContact = findViewById(R.id.txtContact);
        switchDarkMode = findViewById(R.id.switchDarkMode);
        supportUnreadDot = findViewById(R.id.rowCustomerSupportDot);

        setupRow(R.id.rowEditProfile, R.drawable.ic_profile_edit_custom, "Edit Profile");
        setupRow(R.id.rowAddress, R.drawable.ic_location_custom, "Address");
        setupRow(R.id.rowSecurity, R.drawable.ic_security_custom, "Security");
        setupRow(R.id.rowPrivacy, R.drawable.ic_privacy_custom, "Privacy Policy");
        setupRow(R.id.rowHelp, R.drawable.ic_help_center_custom, "Help Center");
        setupRow(R.id.rowCustomerSupport, R.drawable.ic_customer_support_custom, "Customer Support");
        setupRow(R.id.rowLogout, R.drawable.ic_logout_custom, "Logout");

        bindRow(R.id.rowEditProfile, EditProfileActivity.class);
        bindRow(R.id.rowAddress, AddressActivity.class);
        bindRow(R.id.rowSecurity, SecurityActivity.class);
        bindRow(R.id.rowPrivacy, PrivacyPolicyActivity.class);
        bindRow(R.id.rowHelp, HelpCenterActivity.class);

        MaterialCardView rowCustomerSupport = findViewById(R.id.rowCustomerSupport);
        rowCustomerSupport.setOnClickListener(v -> startActivity(new Intent(this, SupportChatActivity.class)));

        MaterialCardView rowLogout = findViewById(R.id.rowLogout);
        rowLogout.setOnClickListener(v -> logout());

        boolean isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
        switchDarkMode.setChecked(isDark);
        switchDarkMode.setOnCheckedChangeListener((buttonView, checked) ->
                AppCompatDelegate.setDefaultNightMode(checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO));

        loadUser(user);
    }

    private void bindRow(int viewId, Class<?> activityClass) {
        findViewById(viewId).setOnClickListener(v -> startActivity(new Intent(this, activityClass)));
    }

    private void setupRow(int viewId, int iconRes, String title) {
        MaterialCardView cardView = findViewById(viewId);
        ImageView iconStart = cardView.findViewById(R.id.iconStart);
        iconStart.setImageResource(iconRes);
        if (viewId == R.id.rowAddress) {
            iconStart.clearColorFilter();
            iconStart.setImageTintList(null);
            iconStart.getLayoutParams().width = (int) (24 * getResources().getDisplayMetrics().density);
            iconStart.getLayoutParams().height = (int) (24 * getResources().getDisplayMetrics().density);
            iconStart.requestLayout();
        }
        ((TextView) cardView.findViewById(R.id.txtTitle)).setText(title);
    }

    private void loadUser(@NonNull FirebaseUser user) {
        String cachedName = UserProfileCache.getName(this);
        String cachedEmail = UserProfileCache.getEmail(this);
        String cachedPhone = UserProfileCache.getPhone(this);
        String cachedPhoto = UserProfileCache.getPhoto(this);

        String fallbackName = !TextUtils.isEmpty(cachedName)
                ? cachedName
                : (!TextUtils.isEmpty(user.getDisplayName()) ? user.getDisplayName() : "Dayani User");
        String fallbackSubtitle = getProfileSubtitle(
                !TextUtils.isEmpty(cachedEmail) ? cachedEmail : user.getEmail(),
                !TextUtils.isEmpty(cachedPhone) ? cachedPhone : user.getPhoneNumber()
        );
        txtName.setText(fallbackName);
        txtContact.setText(fallbackSubtitle);

        String initialPhoto = !TextUtils.isEmpty(cachedPhoto)
                ? cachedPhoto
                : (user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        if (!TextUtils.isEmpty(initialPhoto)) {
            Glide.with(this).load(UserProfileCache.getPhotoForDisplay(this, initialPhoto)).circleCrop().placeholder(R.drawable.ic_profile_wireframe).into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.ic_profile_wireframe);
        }

        FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get(Source.CACHE)
                .addOnSuccessListener(snapshot -> {
                    bindUserSnapshot(snapshot, fallbackName, user.getEmail(), user.getPhoneNumber());
                    FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get(Source.SERVER)
                            .addOnSuccessListener(serverSnapshot -> bindUserSnapshot(serverSnapshot, fallbackName, user.getEmail(), user.getPhoneNumber()));
                })
                .addOnFailureListener(e -> FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get(Source.SERVER)
                        .addOnSuccessListener(serverSnapshot -> bindUserSnapshot(serverSnapshot, fallbackName, user.getEmail(), user.getPhoneNumber())));
    }

    private void bindUserSnapshot(com.google.firebase.firestore.DocumentSnapshot snapshot, String fallbackName, String fallbackEmail, String fallbackPhone) {
        if (snapshot == null || !snapshot.exists()) return;
        String name = snapshot.getString("name");
        String phone = snapshot.getString("phone");
        String email = snapshot.getString("email");
        String photoUrl = snapshot.getString("photoUrl");
        String localPhotoUri = snapshot.getString("localPhotoUri");
        String finalName = !TextUtils.isEmpty(name) ? name : fallbackName;
        String finalEmail = TextUtils.isEmpty(email) ? fallbackEmail : email;
        String finalPhone = TextUtils.isEmpty(phone) ? fallbackPhone : phone;
        String finalSubtitle = getProfileSubtitle(finalEmail, finalPhone);
        String displayPhoto = !TextUtils.isEmpty(localPhotoUri) ? localPhotoUri : photoUrl;

        txtName.setText(finalName);
        txtContact.setText(finalSubtitle);
        UserProfileCache.save(this, finalName, finalEmail, finalPhone, displayPhoto);

        if (!TextUtils.isEmpty(displayPhoto)) {
            Glide.with(this).load(UserProfileCache.getPhotoForDisplay(this, displayPhoto)).circleCrop().placeholder(R.drawable.ic_profile_wireframe).into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.ic_profile_wireframe);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user != null) {
            loadUser(user);
        }
        if (supportDotListener != null) supportDotListener.remove();
        supportDotListener = SupportChatBadgeUtil.observeUserUnreadDot(supportUnreadDot);
    }

    @Override
    protected void onPause() {
        if (supportDotListener != null) {
            supportDotListener.remove();
            supportDotListener = null;
        }
        super.onPause();
    }

    private String getProfileSubtitle(String email, String phone) {
        if (!TextUtils.isEmpty(email)) {
            return email.trim();
        }
        return maskPhone(phone);
    }

    private String maskPhone(String phone) {
        if (!TextUtils.isEmpty(phone) && phone.trim().length() >= 4) {
            String normalized = phone.replaceAll("\\s+", "");
            int len = normalized.length();
            String prefix = normalized.substring(0, Math.min(4, len));
            String suffix = normalized.substring(Math.max(len - 3, 0));
            return prefix + "****" + suffix;
        }
        return "Manage your account details";
    }

    private void logout() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        dialog.setContentView(view);
        dialog.setCancelable(true);

        view.findViewById(R.id.btnCancelLogout).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirmLogout).setOnClickListener(v -> {
            dialog.dismiss();
            LoginManager.getInstance().logOut();
            auth.signOut();
            AppMessageUtil.show(this, "Logged out successfully.", AppMessageUtil.TYPE_INFO);
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity();
        });

        dialog.show();
    }
}
