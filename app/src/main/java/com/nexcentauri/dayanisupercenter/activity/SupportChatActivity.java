package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.adapter.SupportMessageAdapter;
import com.nexcentauri.dayanisupercenter.model.SupportMessage;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.ConversationSession;
import com.nexcentauri.dayanisupercenter.util.ProfilePhotoRepair;
import java.util.UUID;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;
import com.nexcentauri.dayanisupercenter.util.UserProfileCache;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupportChatActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private ListenerRegistration messagesListener;
    private ListenerRegistration profileListener;
    private ListenerRegistration threadListener;
    private String activeConversationId = ConversationSession.LEGACY;
    private boolean threadReady;
    private boolean sending;
    private boolean resetting;
    private boolean markingRead;
    private long unreadForUser;
    private String remotePhotoStoragePath = "";
    private MaterialCardView btnNewConversation;
    private String remoteProfileImage;
    private String remoteUserName;

    private RecyclerView recyclerMessages;
    private LinearLayout layoutEmpty;
    private EditText inputMessage;
    private MaterialButton btnSend;
    private TextView txtSubtitle;

    private final List<SupportMessage> messages = new ArrayList<>();
    private SupportMessageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_chat);
        SystemBarUtil.matchStatusBarToContent(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        ImageView btnBack = findViewById(R.id.btnBack);
        btnNewConversation = findViewById(R.id.cardNewConversation);
        recyclerMessages = findViewById(R.id.recyclerSupportMessages);
        layoutEmpty = findViewById(R.id.layoutEmptySupport);
        inputMessage = findViewById(R.id.inputSupportMessage);
        btnSend = findViewById(R.id.btnSendSupport);
        txtSubtitle = findViewById(R.id.txtSupportSubtitle);

        btnBack.setOnClickListener(v -> finish());
        btnNewConversation.setOnClickListener(v -> resetConversation());
        btnSend.setOnClickListener(v -> sendMessage());

        adapter = new SupportMessageAdapter(messages);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(false);
        recyclerMessages.setLayoutManager(manager);
        recyclerMessages.setAdapter(adapter);

        btnSend.setEnabled(false);
        updateSubtitle();
        profileListener = db.collection("users").document(currentUser.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;
                    remoteProfileImage = webPhoto(snapshot.getString("photoUrl"));
                    remoteUserName = snapshot.getString("name");
                    remotePhotoStoragePath = value(snapshot.getString("photoStoragePath"), "");
                    ProfilePhotoRepair.repair(this, currentUser.getUid(), snapshot);
                    syncThreadUserMeta();
                });
        syncThreadUserMeta();
        threadListener = chatDoc().addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                AppMessageUtil.show(this, "Unable to load support chat.", AppMessageUtil.TYPE_ERROR);
                return;
            }
            String session = ConversationSession.normalize(snapshot.getString("activeConversationId"));
            boolean changed = !threadReady || !session.equals(activeConversationId);
            activeConversationId = session;
            threadReady = true;
            Long unread = snapshot.getLong("unreadForUserCount");
            unreadForUser = unread == null ? 0L : unread;
            btnSend.setEnabled(!sending && !resetting);
            if (changed) observeMessages();
            markThreadReadForUser();
        });
    }

    private void updateSubtitle() {
        String email = currentUser.getEmail();
        txtSubtitle.setText(TextUtils.isEmpty(email) ? currentUser.getUid() : email);
    }

    private DocumentReference chatDoc() {
        return db.collection("support_chats").document(currentUser.getUid());
    }

    private CollectionReference messagesCollection() {
        return chatDoc().collection("messages");
    }

    private void observeMessages() {
        if (messagesListener != null) messagesListener.remove();
        final String observedSession = activeConversationId;
        Query messagesQuery = ConversationSession.LEGACY.equals(observedSession)
                ? messagesCollection().orderBy("createdAtClient", Query.Direction.ASCENDING)
                : messagesCollection().whereEqualTo("conversationId", observedSession);
        messagesListener = messagesQuery.addSnapshotListener((snapshots, error) -> {
                    if (!observedSession.equals(activeConversationId) || isFinishing() || isDestroyed()) return;
                    if (error != null) {
                        AppMessageUtil.show(this, "Unable to load support chat.", AppMessageUtil.TYPE_ERROR);
                        return;
                    }
                    messages.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            if (!ConversationSession.includes(observedSession, doc.getString("conversationId"))) continue;
                            messages.add(new SupportMessage(
                                    doc.getId(),
                                    value(doc.getString("text"), ""),
                                    value(doc.getString("senderType"), "user"),
                                    readTime(doc)
                            ));
                        }
                    }
                    messages.sort((a, b) -> Long.compare(a.getCreatedAtMillis(), b.getCreatedAtMillis()));
                    adapter.notifyDataSetChanged();
                    layoutEmpty.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerMessages.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
                    if (!messages.isEmpty()) {
                        recyclerMessages.post(() -> recyclerMessages.smoothScrollToPosition(messages.size() - 1));
                    }
                });
    }

    private void sendMessage() {
        if (!threadReady || sending || resetting) return;
        String text = inputMessage.getText() == null ? "" : inputMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            AppMessageUtil.show(this, "Type your message first.", AppMessageUtil.TYPE_WARNING);
            return;
        }
        sending = true;
        btnSend.setEnabled(false);
        btnNewConversation.setEnabled(false);

        Map<String, Object> message = new HashMap<>();
        message.put("text", text);
        message.put("senderType", "user");
        message.put("senderId", currentUser.getUid());
        message.put("senderEmail", value(currentUser.getEmail(), ""));
        message.put("createdAt", FieldValue.serverTimestamp());
        message.put("createdAtClient", new Date());

        Map<String, Object> chatMeta = new HashMap<>();
        chatMeta.put("userId", currentUser.getUid());
        chatMeta.put("userEmail", value(currentUser.getEmail(), ""));
        chatMeta.put("userName", resolveUserName());
        chatMeta.put("userProfileImage", resolveUserProfileImage());
        chatMeta.put("userPhotoStoragePath", remotePhotoStoragePath);
        chatMeta.put("lastMessage", text);
        chatMeta.put("lastMessageAt", FieldValue.serverTimestamp());
        chatMeta.put("lastMessageAtClient", new Date());
        chatMeta.put("lastSenderType", "user");
        chatMeta.put("unreadForAdminCount", FieldValue.increment(1));
        chatMeta.put("unreadForUserCount", 0);
        chatMeta.put("updatedAt", FieldValue.serverTimestamp());

        DocumentReference messageRef = messagesCollection().document();
        db.runTransaction(transaction -> {
            DocumentSnapshot thread = transaction.get(chatDoc());
            message.put("conversationId", ConversationSession.normalize(thread.getString("activeConversationId")));
            transaction.set(messageRef, message);
            transaction.set(chatDoc(), chatMeta, SetOptions.merge());
            return null;
        }).addOnSuccessListener(unused -> {
            inputMessage.setText("");
            finishChatAction();
        }).addOnFailureListener(e -> {
            finishChatAction();
            AppMessageUtil.show(this, "Message send failed.", AppMessageUtil.TYPE_ERROR);
        });
    }

    private void resetConversation() {
        if (sending || resetting || !threadReady) return;
        resetting = true;
        btnSend.setEnabled(false);
        btnNewConversation.setEnabled(false);
        final String newSession = UUID.randomUUID().toString();
        db.runTransaction(transaction -> {
            DocumentSnapshot thread = transaction.get(chatDoc());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("activeConversationId", newSession);
            metadata.put("userResetAt", FieldValue.serverTimestamp());
            metadata.put("unreadForUserCount", 0);
            metadata.put("updatedAt", FieldValue.serverTimestamp());
            metadata.put("userId", currentUser.getUid());
            metadata.put("userEmail", value(currentUser.getEmail(), ""));
            metadata.put("userName", resolveUserName());
            metadata.put("userProfileImage", resolveUserProfileImage());
            metadata.put("userPhotoStoragePath", remotePhotoStoragePath);
            if (!thread.exists()) {
                metadata.put("lastMessage", "");
                metadata.put("lastMessageAtClient", new Date());
            }
            // Keep the thread and all messages for the admin's history.
            transaction.set(chatDoc(), metadata, SetOptions.merge());
            return null;
        }).addOnSuccessListener(unused -> {
            finishChatAction();
            AppMessageUtil.show(this, "Started a new conversation.", AppMessageUtil.TYPE_SUCCESS);
        }).addOnFailureListener(e -> {
            finishChatAction();
            AppMessageUtil.show(this, "Could not start a new conversation.", AppMessageUtil.TYPE_ERROR);
        });
    }

    private void finishChatAction() {
        sending = false;
        resetting = false;
        btnSend.setEnabled(threadReady);
        btnNewConversation.setEnabled(true);
    }

    private void syncThreadUserMeta() {
        chatDoc().get().addOnSuccessListener(snapshot -> {
            if (snapshot == null || !snapshot.exists()) {
                return;
            }
            Map<String, Object> chatMeta = new HashMap<>();
            chatMeta.put("userId", currentUser.getUid());
            chatMeta.put("userEmail", value(currentUser.getEmail(), ""));
            chatMeta.put("userName", resolveUserName());
            chatMeta.put("userProfileImage", resolveUserProfileImage());
            chatMeta.put("userPhotoStoragePath", remotePhotoStoragePath);
            chatMeta.put("updatedAt", FieldValue.serverTimestamp());
            chatDoc().set(chatMeta, SetOptions.merge());
        });
    }

    private void markThreadReadForUser() {
        if (currentUser == null || unreadForUser <= 0 || markingRead) return;
        markingRead = true;
        db.runTransaction(transaction -> {
            DocumentSnapshot thread = transaction.get(chatDoc());
            Long unread = thread.getLong("unreadForUserCount");
            if (thread.exists() && unread != null && unread > 0) transaction.update(chatDoc(), "unreadForUserCount", 0);
            return null;
        }).addOnCompleteListener(task -> markingRead = false);
    }

    private long readTime(DocumentSnapshot doc) {
        Timestamp client = doc.getTimestamp("createdAtClient");
        if (client != null) return client.toDate().getTime();
        Timestamp server = doc.getTimestamp("createdAt");
        if (server != null) return server.toDate().getTime();
        return System.currentTimeMillis();
    }

    private String resolveUserName() {
        if (!TextUtils.isEmpty(remoteUserName)) return remoteUserName;
        String cached = UserProfileCache.getName(this);
        if (!TextUtils.isEmpty(cached)) return cached;
        if (!TextUtils.isEmpty(currentUser.getDisplayName())) return currentUser.getDisplayName();
        String email = currentUser.getEmail();
        if (!TextUtils.isEmpty(email) && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "Customer";
    }


    private String resolveUserProfileImage() {
        if (remoteProfileImage != null) return remoteProfileImage;
        String cached = webPhoto(UserProfileCache.getPhoto(this));
        if (!TextUtils.isEmpty(cached)) return cached;
        if (currentUser.getPhotoUrl() != null) {
            String photo = webPhoto(currentUser.getPhotoUrl().toString());
            if (!TextUtils.isEmpty(photo)) return photo;
        }
        return "";
    }

    private String webPhoto(String photo) {
        if (photo == null) return "";
        android.net.Uri uri = android.net.Uri.parse(photo.trim());
        String scheme = uri.getScheme();
        return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                && !TextUtils.isEmpty(uri.getHost()) ? photo.trim() : "";
    }

    private String value(String text, String fallback) {
        return TextUtils.isEmpty(text) ? fallback : text;
    }

    @Override
    protected void onResume() {
        super.onResume();
        markThreadReadForUser();
    }

    @Override
    protected void onDestroy() {
        if (messagesListener != null) messagesListener.remove();
        if (profileListener != null) profileListener.remove();
        if (threadListener != null) threadListener.remove();
        super.onDestroy();
    }
}
