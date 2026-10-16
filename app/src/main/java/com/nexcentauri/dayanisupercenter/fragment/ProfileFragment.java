package com.nexcentauri.dayanisupercenter.fragment;

import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.facebook.login.LoginManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.activity.AddressActivity;
import com.nexcentauri.dayanisupercenter.activity.EditProfileActivity;
import com.nexcentauri.dayanisupercenter.activity.HelpCenterActivity;
import com.nexcentauri.dayanisupercenter.activity.LoginActivity;
import com.nexcentauri.dayanisupercenter.activity.PrivacyPolicyActivity;
import com.nexcentauri.dayanisupercenter.activity.SecurityActivity;
import com.nexcentauri.dayanisupercenter.activity.SupportChatActivity;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.SupportChatBadgeUtil;
import com.nexcentauri.dayanisupercenter.util.UserProfileCache;

public class ProfileFragment extends Fragment {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ImageView profileTabImage;
    private TextView profileTabName;
    private TextView profileTabContact;
    private View supportDot;
    private com.google.firebase.firestore.ListenerRegistration supportDotListener;
    private long lastUserServerRefreshMs = 0L;
    private static final long USER_REFRESH_THROTTLE_MS = 30_000L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable android.os.Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_profile_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        profileTabImage = view.findViewById(R.id.profileTabImage);
        profileTabName = view.findViewById(R.id.profileTabName);
        profileTabContact = view.findViewById(R.id.profileTabContact);

        setupProfileRow(view, R.id.profileRowEditProfile, R.drawable.ic_profile_edit_custom, "Edit Profile", EditProfileActivity.class);
        setupProfileRow(view, R.id.profileRowAddress, R.drawable.ic_location_custom, "Address", AddressActivity.class);
        setupProfileRow(view, R.id.profileRowSecurity, R.drawable.ic_security_custom, "Security", SecurityActivity.class);
        setupProfileRow(view, R.id.profileRowPrivacy, R.drawable.ic_privacy_custom, "Privacy Policy", PrivacyPolicyActivity.class);
        setupProfileRow(view, R.id.profileRowHelp, R.drawable.ic_help_center_custom, "Help Center", HelpCenterActivity.class);
        setupProfileRow(view, R.id.profileRowCustomerSupport, R.drawable.ic_customer_support_custom, "Customer Support", null);
        supportDot = view.findViewById(R.id.profileRowCustomerSupportDot);
        setupProfileRow(view, R.id.profileRowLogout, R.drawable.ic_logout_custom, "Logout", null);

        view.findViewById(R.id.profileRowCustomerSupport).setOnClickListener(v -> startActivity(new Intent(requireContext(), SupportChatActivity.class)));
        view.findViewById(R.id.profileRowLogout).setOnClickListener(v -> logout());

        MaterialCardView logoutCard = view.findViewById(R.id.profileRowLogout);
        logoutCard.setCardBackgroundColor(requireContext().getColor(android.R.color.transparent));
        logoutCard.setBackgroundResource(R.drawable.bg_profile_logout_row);
        ImageView logoutIcon = logoutCard.findViewById(R.id.iconStart);
        TextView logoutTitle = logoutCard.findViewById(R.id.txtTitle);
        ImageView logoutArrow = logoutCard.findViewById(R.id.iconEnd);
        logoutIcon.setColorFilter(0xFFD94C3D);
        logoutTitle.setTextColor(0xFFD94C3D);
        logoutArrow.setColorFilter(0xFFD94C3D);
    }

    @Override
    public void onResume() {
        super.onResume();
        FirebaseUser currentUser = auth != null ? auth.getCurrentUser() : null;
        if (currentUser != null) {
            loadUserDetails(currentUser, shouldRefreshUserFromServer());
        }
        if (supportDotListener != null) supportDotListener.remove();
        supportDotListener = SupportChatBadgeUtil.observeUserUnreadDot(supportDot);
    }

    @Override
    public void onPause() {
        if (supportDotListener != null) {
            supportDotListener.remove();
            supportDotListener = null;
        }
        super.onPause();
    }

    private void setupProfileRow(View parent, int rowId, int iconRes, String title, Class<?> activityClass) {
        MaterialCardView card = parent.findViewById(rowId);
        ImageView iconStart = card.findViewById(R.id.iconStart);
        TextView txtTitle = card.findViewById(R.id.txtTitle);
        iconStart.setImageResource(iconRes);
        if (rowId == R.id.profileRowAddress) {
            iconStart.clearColorFilter();
            iconStart.setImageTintList(null);
            iconStart.getLayoutParams().width = (int) (24 * getResources().getDisplayMetrics().density);
            iconStart.getLayoutParams().height = (int) (24 * getResources().getDisplayMetrics().density);
            iconStart.requestLayout();
        }
        txtTitle.setText(title);
        if (activityClass != null) {
            card.setOnClickListener(v -> startActivity(new Intent(requireContext(), activityClass)));
        }
    }

    private void logout() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_logout, null);
        dialog.setContentView(view);
        dialog.setCancelable(true);

        view.findViewById(R.id.btnCancelLogout).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirmLogout).setOnClickListener(v -> {
            dialog.dismiss();
            LoginManager.getInstance().logOut();
            auth.signOut();
            AppMessageUtil.show(requireActivity(), "Logged out successfully.", AppMessageUtil.TYPE_INFO);
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
        });

        dialog.show();
    }

    private void loadUserDetails(@NonNull FirebaseUser currentUser, boolean refreshFromServer) {
        String uid = currentUser.getUid();
        String cachedName = UserProfileCache.getName(requireContext());
        String cachedEmail = UserProfileCache.getEmail(requireContext());
        String cachedPhone = UserProfileCache.getPhone(requireContext());
        String cachedPhoto = UserProfileCache.getPhoto(requireContext());

        String fallbackName = !TextUtils.isEmpty(cachedName)
                ? cachedName
                : (currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty()
                ? currentUser.getDisplayName() : "User");
        String fallbackPhoto = !TextUtils.isEmpty(cachedPhoto)
                ? cachedPhoto
                : (currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "");
        String fallbackSubtitle = getProfileSubtitle(
                !TextUtils.isEmpty(cachedEmail) ? cachedEmail : currentUser.getEmail(),
                !TextUtils.isEmpty(cachedPhone) ? cachedPhone : currentUser.getPhoneNumber()
        );

        profileTabName.setText(fallbackName);
        profileTabContact.setText(fallbackSubtitle);

        if (!fallbackPhoto.isEmpty()) {
            Glide.with(this)
                    .load(UserProfileCache.getPhotoForDisplay(requireContext(), fallbackPhoto))
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile_wireframe)
                    .error(R.drawable.ic_profile_wireframe)
                    .into(profileTabImage);
        } else {
            profileTabImage.setImageResource(R.drawable.ic_profile_wireframe);
        }

        db.collection("users").document(uid).get(Source.CACHE).addOnSuccessListener(documentSnapshot -> {
            bindUserDetailsSnapshot(documentSnapshot, fallbackName, fallbackSubtitle, fallbackPhoto);
            if (refreshFromServer) {
                db.collection("users").document(uid).get(Source.SERVER)
                        .addOnSuccessListener(serverSnapshot -> bindUserDetailsSnapshot(serverSnapshot, fallbackName, fallbackSubtitle, fallbackPhoto));
            }
        }).addOnFailureListener(e -> {
            if (refreshFromServer) {
                db.collection("users").document(uid).get(Source.SERVER)
                        .addOnSuccessListener(serverSnapshot -> bindUserDetailsSnapshot(serverSnapshot, fallbackName, fallbackSubtitle, fallbackPhoto));
            }
        });
    }

    private void bindUserDetailsSnapshot(DocumentSnapshot documentSnapshot, String fallbackName, String fallbackSubtitle, String fallbackPhoto) {
        if (documentSnapshot == null || !documentSnapshot.exists() || !isAdded()) return;

        String name = documentSnapshot.getString("name");
        String email = documentSnapshot.getString("email");
        String phone = documentSnapshot.getString("phone");
        String photoUrl = documentSnapshot.getString("photoUrl");
        String localPhotoUri = documentSnapshot.getString("localPhotoUri");

        String finalName = name != null && !name.trim().isEmpty() ? name : fallbackName;
        String finalSubtitle = getProfileSubtitle(email, phone);
        if (TextUtils.isEmpty(finalSubtitle)) {
            finalSubtitle = fallbackSubtitle;
        }
        String displayPhoto = localPhotoUri != null && !localPhotoUri.trim().isEmpty() ? localPhotoUri : photoUrl;
        if (TextUtils.isEmpty(displayPhoto)) {
            displayPhoto = fallbackPhoto;
        }

        profileTabName.setText(finalName);
        profileTabContact.setText(finalSubtitle);
        UserProfileCache.save(requireContext(), finalName, email, phone, displayPhoto);

        if (!TextUtils.isEmpty(displayPhoto)) {
            Glide.with(this)
                    .load(UserProfileCache.getPhotoForDisplay(requireContext(), displayPhoto))
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile_wireframe)
                    .error(R.drawable.ic_profile_wireframe)
                    .into(profileTabImage);
        } else {
            profileTabImage.setImageResource(R.drawable.ic_profile_wireframe);
        }
    }


    private boolean shouldRefreshUserFromServer() {
        long now = System.currentTimeMillis();
        if (now - lastUserServerRefreshMs >= USER_REFRESH_THROTTLE_MS) {
            lastUserServerRefreshMs = now;
            return true;
        }
        return false;
    }

    private String getProfileSubtitle(String email, String phone) {
        if (email != null && !email.trim().isEmpty()) {
            return email.trim();
        }
        return maskPhone(phone);
    }

    private String maskPhone(String phone) {
        if (phone != null && phone.trim().length() >= 4) {
            String clean = phone.replaceAll("\\s+", "").trim();
            int len = clean.length();
            String prefix = clean.substring(0, Math.min(4, len));
            String suffix = clean.substring(Math.max(len - 3, 0));
            return prefix + "****" + suffix;
        }
        return "Manage your account details";
    }
}
