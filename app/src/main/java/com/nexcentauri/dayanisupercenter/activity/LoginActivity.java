package com.nexcentauri.dayanisupercenter.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.nexcentauri.dayanisupercenter.util.GoogleSignInSupport;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private EditText edtEmail, edtPassword;
    private TextView txtEmailError, txtPasswordError;
    private Button btnLogin;
    private CheckBox chkRemember;
    private ImageButton btnG, btnFb;

    private CredentialManager credentialManager;
    private CancellationSignal googleCancellationSignal;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.matchStatusBarToContent(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);
        callbackManager = CallbackManager.Factory.create();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnLogin = findViewById(R.id.btnLogin);
        TextView txtBottom = findViewById(R.id.txtBottom);
        TextView txtForgot = findViewById(R.id.txtForgot);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        txtEmailError = findViewById(R.id.txtEmailError);
        txtPasswordError = findViewById(R.id.txtPasswordError);
        chkRemember = findViewById(R.id.chkRemember);
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
        edtEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearEmailError(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        edtPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearPasswordError(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnLogin.setOnClickListener(v -> doLogin());
        btnG.setOnClickListener(v -> startGoogleLogin());
        btnFb.setOnClickListener(v -> startFacebookLogin());
        txtBottom.setOnClickListener(v -> startActivity(new Intent(LoginActivity.this, SignupActivity.class)));
        txtForgot.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            intent.putExtra("email", edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "");
            startActivity(intent);
        });

        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        handleFacebookAccessToken(loginResult.getAccessToken());
                    }

                    @Override
                    public void onCancel() {
                        setLoading(false);
                        setGoogleLoading(false);
                        setFacebookLoading(false);
                        AppMessageUtil.show(LoginActivity.this,
                                "Facebook login cancelled.",
                                AppMessageUtil.TYPE_WARNING);
                    }

                    @Override
                    public void onError(@NonNull FacebookException error) {
                        setLoading(false);
                        setGoogleLoading(false);
                        setFacebookLoading(false);
                        String reason = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.US);
                        if (reason.contains("key hash") || reason.contains("keyhash")) {
                            FacebookKeyHashUtil.showSetupHelp(LoginActivity.this);
                        } else {
                            AppMessageUtil.show(LoginActivity.this, "Facebook login failed. Please try again.", AppMessageUtil.TYPE_ERROR);
                        }
                    }
                });
    }

    private void doLogin() {
        if (!validateInputs()) {
            return;
        }

        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (!task.isSuccessful()) {
                        AppMessageUtil.show(this, getLoginErrorMessage(task.getException()), AppMessageUtil.TYPE_ERROR);
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        AppMessageUtil.show(this, "Login failed. Please try again.", AppMessageUtil.TYPE_ERROR);
                        return;
                    }

                    if (!user.isEmailVerified()) {
                        AppMessageUtil.show(this, "Please verify your email before signing in.", AppMessageUtil.TYPE_INFO);
                        Intent i = new Intent(LoginActivity.this, VerifyEmailActivity.class);
                        i.putExtra("email", user.getEmail());
                        startActivity(i);
                        finish();
                        return;
                    }

                    UserFlowNavigator.routeAfterAuth(this, user, true);
                });
    }

    private void startGoogleLogin() {
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
                            AppMessageUtil.show(LoginActivity.this, GoogleSignInSupport.errorMessage(e),
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
            AppMessageUtil.show(this, "Google login failed.", AppMessageUtil.TYPE_ERROR);
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

    private void startFacebookLogin() {
        setLoading(true);
        setFacebookLoading(true);

        LoginManager.getInstance().logInWithReadPermissions(
                LoginActivity.this,
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

            Log.e("FB_GRAPH_RESPONSE", object != null ? object.toString() : "NULL RESPONSE");
            Log.e("FB_EMAIL_VALUE", "email = " + (object != null ? object.optString("email", "NO_EMAIL_FIELD") : "OBJECT_NULL"));
            Log.e("FB_FIREBASE_EMAIL", "firebase email = " + (user != null ? user.getEmail() : "USER_NULL"));

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

    private boolean validateInputs() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        clearErrors();

        if (email.isEmpty()) {
            showEmailError("Please enter your email");
            edtEmail.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showEmailError("Enter a valid email address");
            edtEmail.requestFocus();
            return false;
        }
        if (password.isEmpty()) {
            showPasswordError("Please enter your password");
            edtPassword.requestFocus();
            return false;
        }
        return true;
    }

    private String getLoginErrorMessage(Exception e) {
        String msg = e != null && e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (msg.contains("password is invalid") || msg.contains("credential is incorrect") || msg.contains("invalid_login_credentials")) {
            return "Incorrect email or password.";
        }
        if (msg.contains("no user record") || msg.contains("user-not-found")) {
            return "No account found with this email.";
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
        return "Login failed. Please try again.";
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
        btnLogin.setEnabled(!loading);
        btnLogin.setAlpha(loading ? 0.6f : 1f);
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
