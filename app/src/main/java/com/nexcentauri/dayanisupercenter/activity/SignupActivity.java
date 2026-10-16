package com.nexcentauri.dayanisupercenter.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.nexcentauri.dayanisupercenter.util.GoogleSignInSupport;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.FacebookKeyHashUtil;
import com.nexcentauri.dayanisupercenter.util.UserFlowNavigator;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignupActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private ImageView btnBack;
    private EditText edtEmail, edtPassword;
    private CheckBox chkRemember;
    private Button btnSignup;
    private TextView txtBottom;
    private TextView txtEmailError, txtPasswordError;
    private ImageButton btnG, btnFb;

    private CredentialManager credentialManager;
    private CancellationSignal googleCancellationSignal;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.matchStatusBarToContent(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);
        callbackManager = CallbackManager.Factory.create();

        btnBack = findViewById(R.id.btnBack);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        chkRemember = findViewById(R.id.chkRemember);
        btnSignup = findViewById(R.id.btnSignup);
        txtBottom = findViewById(R.id.txtBottom);
        txtEmailError = findViewById(R.id.txtEmailError);
        txtPasswordError = findViewById(R.id.txtPasswordError);
        btnG = findViewById(R.id.btnG);
        btnFb = findViewById(R.id.btnFb);
        btnFb.setOnLongClickListener(v -> {
            FacebookKeyHashUtil.showSetupHelp(this);
            return true;
        });

        edtEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                edtEmail.post(() -> edtEmail.setSelection(0));
            }
        });

        btnBack.setOnClickListener(v -> finish());

        txtBottom.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });

        edtEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearEmailError(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        edtPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearPasswordError(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        btnSignup.setOnClickListener(v -> doSignup());
        btnG.setOnClickListener(v -> startGoogleSignup());
        btnFb.setOnClickListener(v -> startFacebookSignup());

        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        handleFacebookAccessToken(loginResult.getAccessToken());
                    }

                    @Override
                    public void onCancel() {
                        setLoading(false);
                        setFacebookLoading(false);
                        AppMessageUtil.show(SignupActivity.this,
                                "Facebook sign up cancelled.",
                                AppMessageUtil.TYPE_WARNING);
                    }

                    @Override
                    public void onError(@NonNull FacebookException error) {
                        setLoading(false);
                        setFacebookLoading(false);
                        String reason = error.getMessage() == null ? "" : error.getMessage().toLowerCase(java.util.Locale.US);
                        if (reason.contains("key hash") || reason.contains("keyhash")) FacebookKeyHashUtil.showSetupHelp(SignupActivity.this);
                        else AppMessageUtil.show(SignupActivity.this, "Facebook sign up failed.", AppMessageUtil.TYPE_ERROR);
                    }
                });
    }

    private void doSignup() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        clearErrors();

        if (email.isEmpty()) {
            showEmailError("Please enter your email");
            edtEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showEmailError("Enter a valid email address");
            edtEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            showPasswordError("Please enter your password");
            edtPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            showPasswordError("Password must be at least 6 characters");
            edtPassword.requestFocus();
            return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        AppMessageUtil.show(this, getSignupErrorMessage(task.getException()), AppMessageUtil.TYPE_ERROR);
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        AppMessageUtil.show(this, "Sign up failed. Please try again.", AppMessageUtil.TYPE_ERROR);
                        return;
                    }

                    String uid = user.getUid();
                    long authCreatedMillis = user.getMetadata() != null
                            ? user.getMetadata().getCreationTimestamp()
                            : System.currentTimeMillis();
                    Timestamp joinedAt = new Timestamp(new Date(authCreatedMillis));

                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", uid);
                    data.put("email", email);
                    data.put("rememberMe", chkRemember.isChecked());
                    data.put("provider", "email");
                    data.put("emailVerified", false);
                    data.put("createdAt", FieldValue.serverTimestamp());
                    data.put("createdAtClient", joinedAt);

                    db.collection("users").document(uid).set(data, SetOptions.merge())
                            .addOnCompleteListener(saveTask -> user.sendEmailVerification()
                                    .addOnCompleteListener(mailTask -> {
                                        setLoading(false);

                                        if (mailTask.isSuccessful()) {
                                            Intent i = new Intent(SignupActivity.this, VerifyEmailActivity.class);
                                            i.putExtra("email", email);
                                            startActivity(i);
                                            finish();
                                        } else {
                                            AppMessageUtil.show(this,
                                                    "Account created, but verification email could not be sent.",
                                                    AppMessageUtil.TYPE_WARNING);
                                        }
                                    }));
                });
    }

    private void startGoogleSignup() {
        setLoading(true);
        setGoogleLoading(true);

        GetCredentialRequest request = GoogleSignInSupport.buttonRequest(getString(R.string.default_web_client_id));
        googleCancellationSignal = new CancellationSignal();

        credentialManager.getCredentialAsync(
                this,
                request,
                googleCancellationSignal,
                executorService,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) handleGoogleSignInResult(result);
                        });
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        android.util.Log.w("GoogleSignIn", "Credential request failed: " + e.getClass().getSimpleName(), e);
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            setLoading(false);
                            setGoogleLoading(false);
                            AppMessageUtil.show(SignupActivity.this, GoogleSignInSupport.errorMessage(e),
                                    e instanceof androidx.credentials.exceptions.GetCredentialCancellationException
                                            ? AppMessageUtil.TYPE_INFO : AppMessageUtil.TYPE_ERROR);
                        });
                    }
                }
        );
    }

    private void handleGoogleSignInResult(GetCredentialResponse result) {
        if (result == null || result.getCredential() == null) {
            setLoading(false);
            setGoogleLoading(false);
            AppMessageUtil.show(this, "Google sign up failed.", AppMessageUtil.TYPE_ERROR);
            return;
        }

        Credential credential = result.getCredential();

        if (!(credential instanceof CustomCredential)) {
            setLoading(false);
            setGoogleLoading(false);
            AppMessageUtil.show(this, "Invalid Google credential.", AppMessageUtil.TYPE_ERROR);
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;

        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
            setLoading(false);
            setGoogleLoading(false);
            AppMessageUtil.show(this, "Unexpected credential type.", AppMessageUtil.TYPE_ERROR);
            return;
        }

        try {
            GoogleIdTokenCredential googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(customCredential.getData());

            String idToken = googleIdTokenCredential.getIdToken();
            firebaseAuthWithGoogle(idToken);

        } catch (Exception e) {
            setLoading(false);
            setGoogleLoading(false);
            AppMessageUtil.show(this, "Unable to read Google account data.", AppMessageUtil.TYPE_ERROR);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);

        auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        setGoogleLoading(false);
                        android.util.Log.w("GoogleSignIn", "Firebase Google authentication failed", task.getException());
                        AppMessageUtil.show(this, GoogleSignInSupport.errorMessage(task.getException()), AppMessageUtil.TYPE_ERROR);
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        setGoogleLoading(false);
                        AppMessageUtil.show(this, "User data not found.", AppMessageUtil.TYPE_ERROR);
                        return;
                    }

                    saveSocialUserAndContinue(user, "google", null, null, null, null, null);
                });
    }

    private void startFacebookSignup() {
        setLoading(true);
        setFacebookLoading(true);

        LoginManager.getInstance().logInWithReadPermissions(
                SignupActivity.this,
                Arrays.asList("public_profile", "email", "user_birthday", "user_gender")
        );
    }

    private void handleFacebookAccessToken(AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());

        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        setFacebookLoading(false);
                        AppMessageUtil.show(this,
                                "Facebook authentication failed.",
                                AppMessageUtil.TYPE_ERROR);
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        setFacebookLoading(false);
                        AppMessageUtil.show(this,
                                "User data not found.",
                                AppMessageUtil.TYPE_ERROR);
                        return;
                    }

                    fetchFacebookProfileAndContinue(token, user);
                });
    }

    private void fetchFacebookProfileAndContinue(AccessToken token, FirebaseUser user) {
        GraphRequest request = GraphRequest.newMeRequest(token, (object, response) -> {
            String name = null;
            String email = null;
            String photoUrl = null;
            String facebookId = null;
            String birthday = null;
            String gender = null;

            if (object != null) {
                name = object.optString("name", null);
                email = object.optString("email", null);
                facebookId = object.optString("id", null);
                birthday = object.optString("birthday", null);
                gender = object.optString("gender", null);

                JSONObject pictureObject = object.optJSONObject("picture");
                if (pictureObject != null) {
                    JSONObject dataObject = pictureObject.optJSONObject("data");
                    if (dataObject != null) {
                        photoUrl = dataObject.optString("url", null);
                    }
                }
            }

            if ((photoUrl == null || photoUrl.trim().isEmpty()) &&
                    facebookId != null && !facebookId.trim().isEmpty()) {
                photoUrl = "https://graph.facebook.com/" + facebookId + "/picture?type=large";
            }

            saveSocialUserAndContinue(user, "facebook", name, email, photoUrl, birthday, gender);
        });

        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name,email,birthday,gender,picture.width(400).height(400)");
        request.setParameters(parameters);
        request.executeAsync();
    }

    private void saveSocialUserAndContinue(@NonNull FirebaseUser user,
                                           @NonNull String provider,
                                           String preferredName,
                                           String preferredEmail,
                                           String preferredPhotoUrl,
                                           String preferredDob,
                                           String preferredGender) {
        String uid = user.getUid();
        long authCreatedMillis = user.getMetadata() != null
                ? user.getMetadata().getCreationTimestamp()
                : System.currentTimeMillis();
        Timestamp joinedAt = new Timestamp(new Date(authCreatedMillis));

        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("createdAtClient", joinedAt);

        String finalEmail = notNullOr(preferredEmail, user.getEmail());
        if (finalEmail != null && !finalEmail.trim().isEmpty()) {
            data.put("email", finalEmail);
        }

        data.put("name", notNullOr(preferredName, user.getDisplayName()));
        data.put("photoUrl", notNullOr(preferredPhotoUrl,
                user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : ""));
        data.put("provider", provider);
        data.put("rememberMe", chkRemember != null && chkRemember.isChecked());
        data.put("emailVerified", "facebook".equals(provider) ? null : user.isEmailVerified());

        String convertedDob = convertFacebookBirthdayToAppFormat(preferredDob);
        if (convertedDob != null && !convertedDob.trim().isEmpty()) {
            data.put("dob", convertedDob);
        }

        String normalizedGender = normalizeGender(preferredGender);
        if (normalizedGender != null && !normalizedGender.trim().isEmpty()) {
            data.put("gender", normalizedGender);
        }

        com.google.firebase.firestore.DocumentReference profileRef = db.collection("users").document(uid);
        profileRef.get().continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    com.google.firebase.firestore.DocumentSnapshot existing = task.getResult();
                    if (existing != null && existing.exists()) {
                        // Repeated social sign-ins must preserve the customer's edited profile.
                        data.remove("createdAt");
                        data.remove("createdAtClient");
                        if (Boolean.TRUE.equals(existing.getBoolean("profileCompleted"))) {
                            data.remove("name");
                            data.remove("photoUrl");
                            data.remove("dob");
                            data.remove("gender");
                        }
                    }
                    return profileRef.set(data, SetOptions.merge());
                })
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    setGoogleLoading(false);
                    setFacebookLoading(false);
                    UserFlowNavigator.routeAfterAuth(this, user, true);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    setGoogleLoading(false);
                    setFacebookLoading(false);
                    AppMessageUtil.show(this,
                            "Signed in, but profile save failed.",
                            AppMessageUtil.TYPE_WARNING);

                    UserFlowNavigator.routeAfterAuth(this, user, true);
                });
    }

    private String convertFacebookBirthdayToAppFormat(String facebookBirthday) {
        if (facebookBirthday == null || facebookBirthday.trim().isEmpty()) {
            return "";
        }

        String[] inputFormats = {"MM/dd/yyyy", "MM/dd"};
        for (String inputFormat : inputFormats) {
            try {
                java.text.SimpleDateFormat in =
                        new java.text.SimpleDateFormat(inputFormat, java.util.Locale.US);
                in.setLenient(false);

                java.util.Date date = in.parse(facebookBirthday);
                if (date != null) {
                    java.text.SimpleDateFormat out =
                            new java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US);
                    return out.format(date);
                }
            } catch (Exception ignored) {
            }
        }

        return facebookBirthday;
    }

    private String normalizeGender(String rawGender) {
        if (rawGender == null || rawGender.trim().isEmpty()) {
            return "";
        }

        String value = rawGender.trim().toLowerCase();
        if ("male".equals(value)) return "Male";
        if ("female".equals(value)) return "Female";
        return "Prefer not to say";
    }

    private String notNullOr(String preferred, String fallback) {
        if (preferred != null && !preferred.trim().isEmpty()) {
            return preferred;
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback;
        }
        return "";
    }

    private String getSignupErrorMessage(Exception e) {
        String msg = e != null && e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (msg.contains("email address is already in use") || msg.contains("email-already-in-use")) {
            return "This email is already registered. Please sign in instead.";
        }
        if (msg.contains("badly formatted") || msg.contains("invalid email")) {
            return "Please enter a valid email address.";
        }
        if (msg.contains("network error")) {
            return "Network error. Please check your internet connection.";
        }
        if (msg.contains("too many requests")) {
            return "Too many attempts. Please try again later.";
        }
        if (msg.contains("password") && msg.contains("6")) {
            return "Password must be at least 6 characters.";
        }
        return "Sign up failed. Please try again.";
    }

    private void showEmailError(String message) {
        txtEmailError.setText(message);
        txtEmailError.setVisibility(View.VISIBLE);
    }

    private void showPasswordError(String message) {
        txtPasswordError.setText(message);
        txtPasswordError.setVisibility(View.VISIBLE);
    }

    private void clearEmailError() {
        txtEmailError.setText("");
        txtEmailError.setVisibility(View.GONE);
    }

    private void clearPasswordError() {
        txtPasswordError.setText("");
        txtPasswordError.setVisibility(View.GONE);
    }

    private void clearErrors() {
        clearEmailError();
        clearPasswordError();
    }

    private void setLoading(boolean loading) {
        btnSignup.setEnabled(!loading);
        btnSignup.setAlpha(loading ? 0.6f : 1f);
    }

    private void setGoogleLoading(boolean loading) {
        btnG.setEnabled(!loading);
        btnG.setAlpha(loading ? 0.6f : 1f);
    }

    private void setFacebookLoading(boolean loading) {
        btnFb.setEnabled(!loading);
        btnFb.setAlpha(loading ? 0.6f : 1f);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        callbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View current = getCurrentFocus();
            if (current != null) {
                hideKeyboard(current);
                current.clearFocus();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (googleCancellationSignal != null) googleCancellationSignal.cancel();
        executorService.shutdown();
    }
}
