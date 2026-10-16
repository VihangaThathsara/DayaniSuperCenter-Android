package com.nexcentauri.dayanisupercenter.util;

import android.view.View;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public final class SupportChatBadgeUtil {

    private SupportChatBadgeUtil() {
    }

    @Nullable
    public static ListenerRegistration observeUserUnreadDot(View dotView) {
        if (dotView == null) return null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            dotView.setVisibility(View.GONE);
            return null;
        }
        return FirebaseFirestore.getInstance()
                .collection("support_chats")
                .document(user.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        dotView.setVisibility(View.GONE);
                        return;
                    }
                    Long unread = snapshot.getLong("unreadForUserCount");
                    dotView.setVisibility(unread != null && unread > 0 ? View.VISIBLE : View.GONE);
                });
    }
}
