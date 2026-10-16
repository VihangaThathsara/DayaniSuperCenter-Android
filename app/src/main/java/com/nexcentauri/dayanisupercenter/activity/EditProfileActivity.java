package com.nexcentauri.dayanisupercenter.activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.ProfilePhotoStorageUtil;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;
import com.nexcentauri.dayanisupercenter.util.UserProfileCache;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private static final String DEFAULT_COUNTRY_CODE = "+94";
    private static final String DEFAULT_COUNTRY_ISO = "LK";
    private static final String DEFAULT_COUNTRY_FLAG = "🇱🇰";

    private final String[] genders = {"Male", "Female", "Prefer not to say"};
    private ShapeableImageView imgProfile;
    private EditText edtName, edtDob, edtEmail, edtPhone;
    private MaterialAutoCompleteTextView edtGender;
    private MaterialButton btnSave;
    private FirebaseUser currentUser;
    private FirebaseFirestore firestore;
    private DocumentReference userRef;
    private String localPhotoUri = "";
    private String remotePhotoUrl = "";
    private String selectedCountryCode = DEFAULT_COUNTRY_CODE;
    private String selectedCountryIso = DEFAULT_COUNTRY_ISO;
    private String selectedCountryFlag = DEFAULT_COUNTRY_FLAG;
    private String originalPhotoStoragePathForCleanup = "";
    private String remotePhotoStoragePath = "";
    private File preparedPhotoFile;
    private boolean isSaving = false;
    private boolean profileSaveCompleted = false;
    private boolean isProfileInitialized = false;
    private String originalName = "";
    private String originalDob = "";
    private String originalPhone = "";
    private String originalGender = "";
    private String originalPhoto = "";
    private String savedButtonText = "Update";

    private final TextWatcher changeWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable s) {
            updateSaveButtonState();
        }
    };

    private com.nexcentauri.dayanisupercenter.util.ProfilePhotoPicker photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = new com.nexcentauri.dayanisupercenter.util.ProfilePhotoPicker(this, savedInstanceState, uri -> {
            if (isSaving) return;
            if (preparedPhotoFile != null) preparedPhotoFile.delete();
            preparedPhotoFile = null;
            if (!isProfileInitialized) { captureOriginalValues(); isProfileInitialized = true; }
            localPhotoUri = uri.toString();
            showProfileImage();
            updateSaveButtonState();
        });
        setContentView(R.layout.activity_edit_profile);
        SystemBarUtil.matchStatusBarToContent(this);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        firestore = FirebaseFirestore.getInstance();
        userRef = firestore.collection("users").document(currentUser.getUid());

        imgProfile = findViewById(R.id.imgProfile);
        edtName = findViewById(R.id.edtName);
        edtDob = findViewById(R.id.edtDob);
        edtEmail = findViewById(R.id.edtEmail);
        edtPhone = findViewById(R.id.edtPhone);
        edtGender = findViewById(R.id.edtGender);
        btnSave = findViewById(R.id.btnSave);
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnChangePhoto = findViewById(R.id.btnChangePhoto);

        savedButtonText = String.valueOf(btnSave.getText());
        imgProfile.setImageResource(R.drawable.ic_profile_wireframe);
        edtPhone.setKeyListener(DigitsKeyListener.getInstance("0123456789"));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_gender_dropdown, genders);
        edtGender.setAdapter(adapter);
        edtGender.setSimpleItems(genders);
        edtGender.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        edtGender.setThreshold(0);
        edtGender.setKeyListener(null);
        edtGender.setOnClickListener(v -> edtGender.showDropDown());
        edtGender.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) edtGender.showDropDown();
        });
        edtGender.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                edtGender.showDropDown();
                return true;
            }
            return false;
        });

        edtDob.setOnClickListener(v -> openDatePicker());
        edtDob.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) openDatePicker();
        });
        imgProfile.setOnClickListener(v -> { if (!isSaving) photoPicker.show(); });
        btnChangePhoto.setOnClickListener(v -> { if (!isSaving) photoPicker.show(); });
        btnBack.setOnClickListener(v -> handleBackNavigation());
        btnSave.setOnClickListener(v -> saveProfile());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });

        edtPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (s == null) return;
                String cleaned = s.toString().replaceAll("[^0-9]", "");
                if (!cleaned.equals(s.toString())) {
                    edtPhone.setText(cleaned);
                    edtPhone.setSelection(cleaned.length());
                    return;
                }
                updateSaveButtonState();
            }
        });

        setupChangeDetection();
        lockEmailField(true);
        prefillInstantData();
        captureOriginalValues();
        if (savedInstanceState != null) {
            localPhotoUri = savedInstanceState.getString("selected_photo", "");
            if (!localPhotoUri.isEmpty()) isProfileInitialized = true;
            showProfileImage();
        }
        updateSaveButtonState();
        loadProfileInstantly();
    }

    private void setupChangeDetection() {
        edtName.addTextChangedListener(changeWatcher);
        edtDob.addTextChangedListener(changeWatcher);
        edtGender.addTextChangedListener(changeWatcher);
    }

    private void prefillInstantData() {
        setIfEmpty(edtName, currentUser.getDisplayName());
        setIfNotEmpty(edtEmail, resolveEmail(""));

        String cachedName = UserProfileCache.getName(this);
        if (!TextUtils.isEmpty(cachedName)) {
            setIfEmpty(edtName, cachedName);
        }

        String cachedPhone = UserProfileCache.getPhone(this);
        if (!TextUtils.isEmpty(cachedPhone)) {
            String displayPhone = removeCountryCodeFromPhone(cachedPhone);
            setIfEmpty(edtPhone, displayPhone);
        }

        String cachedDob = UserProfileCache.getDob(this);
        if (isValidDateOfBirth(cachedDob)) {
            setIfEmpty(edtDob, cachedDob);
        }

        String cachedGender = UserProfileCache.getGender(this);
        if (!TextUtils.isEmpty(cachedGender)) {
            setIfEmpty(edtGender, cachedGender);
        }

        if (currentUser.getPhotoUrl() != null) {
            remotePhotoUrl = currentUser.getPhotoUrl().toString();
        }

        String cachedPhoto = UserProfileCache.getPhoto(this);
        if (!TextUtils.isEmpty(cachedPhoto)
                && !cachedPhoto.startsWith("content://")
                && !cachedPhoto.startsWith("file://")) {
            remotePhotoUrl = cachedPhoto;
        }
        showProfileImage();
    }

    private void loadProfileInstantly() {
        userRef.get(Source.CACHE)
                .addOnSuccessListener(snapshot -> {
                    bindProfile(snapshot);
                    refreshProfileFromServer();
                })
                .addOnFailureListener(e -> refreshProfileFromServer());
    }

    private void refreshProfileFromServer() {
        userRef.get(Source.SERVER)
                .addOnSuccessListener(this::bindProfile)
                .addOnFailureListener(e -> showProfileImage());
    }

    private void bindProfile(DocumentSnapshot snapshot) {
        if (isSaving || profileSaveCompleted || preparedPhotoFile != null) return;
        if (snapshot == null || !snapshot.exists()) {
            showProfileImage();
            if (!isProfileInitialized) {
                captureOriginalValues();
                isProfileInitialized = true;
                updateSaveButtonState();
            }
            return;
        }

        String name = snapshot.getString("name");
        if (!TextUtils.isEmpty(name) && TextUtils.isEmpty(edtName.getText())) {
            edtName.setText(name);
        }

        String savedDob = snapshot.getString("dob");
        if (TextUtils.isEmpty(edtDob.getText()) && isValidDateOfBirth(savedDob)) {
            edtDob.setText(savedDob);
        }

        String savedPhoneLocal = snapshot.getString("phoneLocal");
        if (TextUtils.isEmpty(savedPhoneLocal)) {
            savedPhoneLocal = snapshot.getString("phone");
        }
        String displayPhone = removeCountryCodeFromPhone(savedPhoneLocal);
        if (TextUtils.isEmpty(edtPhone.getText()) && !TextUtils.isEmpty(displayPhone)) {
            edtPhone.setText(displayPhone);
        }

        String savedGender = snapshot.getString("gender");
        if (TextUtils.isEmpty(edtGender.getText()) && !TextUtils.isEmpty(savedGender)) {
            edtGender.setText(savedGender, false);
        }

        String savedCountryCode = snapshot.getString("countryCode");
        String savedCountryIso = snapshot.getString("countryIso");
        String savedCountryFlag = snapshot.getString("countryFlagEmoji");
        if (!TextUtils.isEmpty(savedCountryCode)) selectedCountryCode = savedCountryCode;
        if (!TextUtils.isEmpty(savedCountryIso)) selectedCountryIso = savedCountryIso;
        if (!TextUtils.isEmpty(savedCountryFlag)) selectedCountryFlag = savedCountryFlag;

        String savedRemoteStoragePath = snapshot.getString("photoStoragePath");
        if (!TextUtils.isEmpty(savedRemoteStoragePath)) {
            remotePhotoStoragePath = savedRemoteStoragePath;
        }

        String photoUrl = snapshot.getString("photoUrl");
        if (TextUtils.isEmpty(localPhotoUri) && !TextUtils.isEmpty(photoUrl)) {
            remotePhotoUrl = photoUrl;
            localPhotoUri = "";
        } else if (TextUtils.isEmpty(localPhotoUri)) {
            String savedLocalUri = snapshot.getString("localPhotoUri");
            if (!TextUtils.isEmpty(savedLocalUri)) {
                localPhotoUri = savedLocalUri;
            }
        }

        String email = resolveEmail(snapshot.getString("email"));
        if (!TextUtils.isEmpty(email)) {
            edtEmail.setText(email);
        }

        showProfileImage();
        if (!isProfileInitialized) {
            captureOriginalValues();
            isProfileInitialized = true;
        }
        updateSaveButtonState();
    }

    private void showProfileImage() {
        if (preparedPhotoFile != null && preparedPhotoFile.isFile()) {
            Glide.with(this).load(preparedPhotoFile).circleCrop()
                    .placeholder(R.drawable.ic_profile_wireframe)
                    .error(R.drawable.ic_profile_wireframe).into(imgProfile);
            return;
        }
        if (!TextUtils.isEmpty(localPhotoUri)) {
            Glide.with(this)
                    .load(Uri.parse(localPhotoUri))
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile_wireframe)
                    .error(R.drawable.ic_profile_wireframe)
                    .into(imgProfile);
            return;
        }

        if (!TextUtils.isEmpty(remotePhotoUrl)) {
            Glide.with(this)
                    .load(UserProfileCache.getPhotoForDisplay(this, remotePhotoUrl))
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile_wireframe)
                    .error(R.drawable.ic_profile_wireframe)
                    .into(imgProfile);
            return;
        }

        imgProfile.setImageResource(R.drawable.ic_profile_wireframe);
    }

    private void lockEmailField(boolean locked) {
        edtEmail.setFocusable(false);
        edtEmail.setFocusableInTouchMode(false);
        edtEmail.setClickable(false);
        edtEmail.setCursorVisible(false);
        edtEmail.setKeyListener(null);
        edtEmail.setLongClickable(false);
        edtEmail.setTextIsSelectable(false);
        edtEmail.setShowSoftInputOnFocus(false);
        edtEmail.setBackgroundResource(R.drawable.bg_profile_input_disabled);
        edtEmail.setTextColor(0xFF666666);
    }

    private String resolveEmail(String savedEmail) {
        String authEmail = currentUser.getEmail();
        if (!TextUtils.isEmpty(authEmail)) return authEmail.trim();
        if (!TextUtils.isEmpty(savedEmail)) return savedEmail.trim();
        String cachedEmail = UserProfileCache.getEmail(this);
        return TextUtils.isEmpty(cachedEmail) ? edtEmail.getText().toString().trim() : cachedEmail.trim();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        photoPicker.saveState(state);
        state.putString("selected_photo", localPhotoUri);
    }

    @Override protected void onDestroy() {
        if (isFinishing() && !isSaving) {
            if (photoPicker != null) photoPicker.clearSelected();
            if (preparedPhotoFile != null) preparedPhotoFile.delete();
        }
        super.onDestroy();
    }

    private void setIfNotEmpty(TextView view, String value) {
        if (!TextUtils.isEmpty(value)) view.setText(value);
    }

    private void setIfEmpty(TextView view, String value) {
        if (TextUtils.isEmpty(view.getText()) && !TextUtils.isEmpty(value)) {
            view.setText(value);
        }
    }

    private void openDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR) - 18;
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        if (isValidDateOfBirth(edtDob.getText().toString().trim())) {
            try {
                Date parsed = new SimpleDateFormat("MM/dd/yyyy", Locale.US).parse(edtDob.getText().toString().trim());
                if (parsed != null) {
                    calendar.setTime(parsed);
                    year = calendar.get(Calendar.YEAR);
                    month = calendar.get(Calendar.MONTH);
                    day = calendar.get(Calendar.DAY_OF_MONTH);
                }
            } catch (ParseException ignored) {
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) ->
                edtDob.setText(String.format(Locale.US, "%02d/%02d/%04d", m + 1, d, y)), year, month, day);

        Calendar maxDate = Calendar.getInstance();
        maxDate.add(Calendar.YEAR, -13);
        dialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
        dialog.show();
    }

    private boolean isValidDateOfBirth(String dob) {
        if (TextUtils.isEmpty(dob)) return false;
        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        format.setLenient(false);
        try {
            Date parsedDate = format.parse(dob);
            if (parsedDate == null) return false;
            Calendar dobCal = Calendar.getInstance();
            dobCal.setTime(parsedDate);
            Calendar minAllowed = Calendar.getInstance();
            minAllowed.add(Calendar.YEAR, -13);
            return !parsedDate.after(new Date()) && !dobCal.after(minAllowed);
        } catch (ParseException e) {
            return false;
        }
    }

    private String normalizeLocalPhone(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.startsWith("94") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        if (digits.length() == 9 && !digits.startsWith("0")) {
            digits = "0" + digits;
        }
        if (digits.length() > 10 && digits.startsWith("0")) {
            digits = digits.substring(0, 10);
        }
        return digits;
    }

    private boolean isValidPhoneNumber(String localNumber) {
        return !TextUtils.isEmpty(localNumber)
                && localNumber.length() == 10
                && localNumber.startsWith("0");
    }

    private String toFirestorePhoneLocal(String displayPhone) {
        String normalized = normalizeLocalPhone(displayPhone);
        if (normalized.startsWith("0") && normalized.length() == 10) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private String removeCountryCodeFromPhone(String phone) {
        if (TextUtils.isEmpty(phone)) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("94") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        } else if (digits.length() == 9) {
            digits = "0" + digits;
        } else if (digits.length() > 10) {
            digits = digits.substring(digits.length() - 9);
            digits = "0" + digits;
        }
        return digits;
    }

    private void captureOriginalValues() {
        originalName = safeText(edtName.getText().toString());
        originalDob = safeText(edtDob.getText().toString());
        originalPhone = normalizeLocalPhone(edtPhone.getText().toString());
        originalGender = safeText(edtGender.getText().toString());
        originalPhoto = currentPhotoValue();
    }

    private String currentPhotoValue() {
        if (!TextUtils.isEmpty(localPhotoUri)) return localPhotoUri;
        if (!TextUtils.isEmpty(remotePhotoUrl)) return remotePhotoUrl;
        return "";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasChanges() {
        if (!isProfileInitialized) return false;
        return !safeText(edtName.getText().toString()).equals(originalName)
                || !safeText(edtDob.getText().toString()).equals(originalDob)
                || !normalizeLocalPhone(edtPhone.getText().toString()).equals(originalPhone)
                || !safeText(edtGender.getText().toString()).equals(originalGender)
                || !currentPhotoValue().equals(originalPhoto);
    }

    private void updateSaveButtonState() {
        boolean enabled = hasChanges();
        btnSave.setEnabled(enabled);
        btnSave.setClickable(enabled);
        btnSave.setAlpha(enabled ? 1f : 0.55f);
    }

    private void setSavingState(boolean saving) {
        isSaving = saving;
        btnSave.setText(saving ? "Updating..." : savedButtonText);
        updateSaveButtonState();
        if (saving) {
            btnSave.setEnabled(true);
            btnSave.setClickable(true);
            btnSave.setAlpha(1f);
        }
    }

    private void handleBackNavigation() {
        if (isSaving) {
            return;
        }
        if (!hasChanges()) {
            finish();
            return;
        }
        showDiscardChangesDialog();
    }

    private void showDiscardChangesDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_discard_changes, null);
        dialog.setContentView(view);

        MaterialButton btnCancelDiscard = view.findViewById(R.id.btnCancelDiscard);
        MaterialButton btnConfirmDiscard = view.findViewById(R.id.btnConfirmDiscard);

        if (btnCancelDiscard != null) {
            btnCancelDiscard.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnConfirmDiscard != null) {
            btnConfirmDiscard.setOnClickListener(v -> {
                dialog.dismiss();
                finish();
            });
        }

        dialog.show();
    }

    private void saveProfile() {
        if (isSaving) return;
        if (!hasChanges()) {
            updateSaveButtonState();
            return;
        }

        String name = edtName.getText().toString().trim();
        String dob = edtDob.getText().toString().trim();
        String email = resolveEmail(edtEmail.getText().toString());
        String phoneInput = edtPhone.getText().toString().trim();
        String normalizedDisplayPhone = normalizeLocalPhone(phoneInput);
        String normalizedLocalPhone = toFirestorePhoneLocal(normalizedDisplayPhone);
        String gender = edtGender.getText().toString().trim();

        if (name.isEmpty()) {
            AppMessageUtil.show(this, "Please enter your full name.", AppMessageUtil.TYPE_ERROR);
            edtName.requestFocus();
            return;
        }
        if (!isValidDateOfBirth(dob)) {
            AppMessageUtil.show(this, "Please select a valid date of birth.", AppMessageUtil.TYPE_ERROR);
            return;
        }
        if (TextUtils.isEmpty(email)) {
            AppMessageUtil.show(this, "Email is required.", AppMessageUtil.TYPE_ERROR);
            return;
        }
        if (!isValidPhoneNumber(normalizedDisplayPhone)) {
            AppMessageUtil.show(this, "Please enter a valid 10-digit phone number.", AppMessageUtil.TYPE_ERROR);
            edtPhone.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(gender)) {
            AppMessageUtil.show(this, "Please select your gender.", AppMessageUtil.TYPE_ERROR);
            edtGender.showDropDown();
            return;
        }

        edtPhone.setText(normalizedDisplayPhone);
        edtPhone.setSelection(normalizedDisplayPhone.length());

        setSavingState(true);

        String fullPhone = selectedCountryCode + normalizedLocalPhone;
        boolean hasNewLocalPhoto = !TextUtils.isEmpty(localPhotoUri);

        if (hasNewLocalPhoto) {
            originalPhotoStoragePathForCleanup = remotePhotoStoragePath;
            Uri selectedPhotoUri = Uri.parse(localPhotoUri);
            ProfilePhotoStorageUtil.prepareProfilePhoto(this, selectedPhotoUri,
                    new ProfilePhotoStorageUtil.PreparationCallback() {
                        @Override
                        public void onSuccess(@NonNull File photoFile) {
                            if (isFinishing() || isDestroyed()) {
                                photoFile.delete();
                                return;
                            }
                            preparedPhotoFile = photoFile;
                            uploadPreparedPhoto(name, dob, email, gender, fullPhone, normalizedLocalPhone);
                        }

                        @Override
                        public void onFailure() {
                            if (isFinishing() || isDestroyed()) return;
                            setSavingState(false);
                            AppMessageUtil.show(EditProfileActivity.this, "Could not prepare profile photo. Please select it again.", AppMessageUtil.TYPE_ERROR);
                        }
                    });
            return;
        }

        persistProfile(name, dob, email, gender, fullPhone, normalizedLocalPhone, remotePhotoUrl, remotePhotoStoragePath);
    }

    private void uploadPreparedPhoto(String name, String dob, String email, String gender,
                                     String fullPhone, String normalizedLocalPhone) {
        ProfilePhotoStorageUtil.uploadProfilePhoto(currentUser.getUid(), Uri.fromFile(preparedPhotoFile), remotePhotoStoragePath,
                    new ProfilePhotoStorageUtil.UploadCallback() {
                        @Override
                        public void onSuccess(@NonNull String downloadUrl, @NonNull String storagePath) {
                            remotePhotoUrl = downloadUrl;
                            remotePhotoStoragePath = storagePath;
                            localPhotoUri = "";
                            persistProfile(name, dob, email, gender, fullPhone, normalizedLocalPhone, downloadUrl, storagePath);
                        }

                        @Override
                        public void onFailure() {
                            if (preparedPhotoFile != null) preparedPhotoFile.delete();
                            preparedPhotoFile = null;
                            setSavingState(false);
                            AppMessageUtil.show(EditProfileActivity.this, "Could not upload profile photo.", AppMessageUtil.TYPE_ERROR);
                        }
                    });
    }

    private void persistProfile(String name,
                                String dob,
                                String email,
                                String gender,
                                String fullPhone,
                                String normalizedLocalPhone,
                                String finalPhotoUrl,
                                String finalPhotoStoragePath) {
        final String previousPhotoStoragePath = originalPhotoStoragePathForCleanup;
        Map<String, Object> data = new HashMap<>();
        data.put("uid", currentUser.getUid());
        data.put("name", name);
        data.put("dob", dob);
        data.put("email", email);
        data.put("phone", fullPhone);
        data.put("phoneLocal", normalizedLocalPhone);
        data.put("countryCode", selectedCountryCode);
        data.put("countryIso", selectedCountryIso);
        data.put("countryFlagEmoji", selectedCountryFlag);
        data.put("gender", gender);
        data.put("localPhotoUri", FieldValue.delete());
        if (!TextUtils.isEmpty(finalPhotoUrl)) {
            data.put("photoUrl", finalPhotoUrl);
        } else {
            data.put("photoUrl", FieldValue.delete());
        }
        if (!TextUtils.isEmpty(finalPhotoStoragePath)) {
            data.put("photoStoragePath", finalPhotoStoragePath);
        } else {
            data.put("photoStoragePath", FieldValue.delete());
        }
        data.put("profileCompleted", true);
        data.put("emailVerified", currentUser.isEmailVerified());

        userRef.set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    profileSaveCompleted = true;
                    if (!TextUtils.isEmpty(previousPhotoStoragePath)
                            && !previousPhotoStoragePath.equals(finalPhotoStoragePath)) {
                        ProfilePhotoStorageUtil.deleteByPath(previousPhotoStoragePath);
                    }
                    originalPhotoStoragePathForCleanup = "";
                    photoPicker.clearSelected();
                    UserProfileCache.save(this, name, email, fullPhone, finalPhotoUrl, dob, gender);
                    if (preparedPhotoFile != null) {
                        UserProfileCache.savePhotoPreview(this, finalPhotoUrl, preparedPhotoFile);
                        preparedPhotoFile = null;
                    }

                    UserProfileChangeRequest.Builder profileBuilder = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name);
                    profileBuilder.setPhotoUri(TextUtils.isEmpty(finalPhotoUrl) ? null : Uri.parse(finalPhotoUrl));

                    // Firestore is the profile source; Auth synchronization need not delay returning.
                    currentUser.updateProfile(profileBuilder.build());
                    captureOriginalValues();
                    showProfileImage();
                    setSavingState(false);
                    AppMessageUtil.show(this, "Profile updated successfully.", AppMessageUtil.TYPE_SUCCESS);
                    setResult(RESULT_OK);
                    btnSave.postDelayed(this::finish, 650);
                })
                .addOnFailureListener(e -> {
                    setSavingState(false);
                    AppMessageUtil.show(this, "Could not update profile.", AppMessageUtil.TYPE_ERROR);
                });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View currentFocus = getCurrentFocus();
            if (currentFocus instanceof EditText) {
                Rect outRect = new Rect();
                currentFocus.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    hideKeyboardAndClearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void hideKeyboardAndClearFocus() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
            focus.clearFocus();
        }
        findViewById(android.R.id.content).requestFocus();
    }
}
