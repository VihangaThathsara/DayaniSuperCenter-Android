package com.nexcentauri.dayanisupercenter.activity;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.model.CountryItem;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;
import com.nexcentauri.dayanisupercenter.util.ProfilePhotoStorageUtil;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;
import com.nexcentauri.dayanisupercenter.util.UserFlowNavigator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FillProfileActivity extends AppCompatActivity {

    private EditText edtFullName, edtDob, edtEmail, edtPhone;
    private MaterialAutoCompleteTextView edtGender;
    private TextView txtCountryFlag, txtCountryCode;
    private TextView txtFullNameError, txtDobError, txtEmailError, txtPhoneError, txtGenderError;
    private MaterialButton btnContinue;
    private ShapeableImageView imgProfile;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private String localPhotoUri = "";
    private String remotePhotoUrl = "";
    private String originalPhotoStoragePathForCleanup = "";
    private String remotePhotoStoragePath = "";
    private CountryItem selectedCountry;
    private final List<CountryItem> countries = new ArrayList<>();
    private final String[] genders = {"Male", "Female", "Prefer not to say"};

    private com.nexcentauri.dayanisupercenter.util.ProfilePhotoPicker photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = new com.nexcentauri.dayanisupercenter.util.ProfilePhotoPicker(this, savedInstanceState, uri -> {
            localPhotoUri = uri.toString();
            showProfileImage();
        });
        setContentView(R.layout.activity_fill_profile);
        SystemBarUtil.matchStatusBarToContent(this);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        if (currentUser == null) {
            UserFlowNavigator.launch(this, LoginActivity.class, true);
            return;
        }

        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnEditPhoto = findViewById(R.id.btnEditPhoto);
        imgProfile = findViewById(R.id.imgProfile);
        edtFullName = findViewById(R.id.edtFullName);
        edtDob = findViewById(R.id.edtDob);
        edtEmail = findViewById(R.id.edtEmail);
        edtPhone = findViewById(R.id.edtPhone);
        edtGender = findViewById(R.id.edtGender);
        txtCountryFlag = findViewById(R.id.txtCountryFlag);
        txtCountryCode = findViewById(R.id.txtCountryCode);
        txtFullNameError = findViewById(R.id.txtFullNameError);
        txtDobError = findViewById(R.id.txtDobError);
        txtEmailError = findViewById(R.id.txtEmailError);
        txtPhoneError = findViewById(R.id.txtPhoneError);
        txtGenderError = findViewById(R.id.txtGenderError);
        btnContinue = findViewById(R.id.btnContinue);

        btnBack.setOnClickListener(v -> finish());
        btnEditPhoto.setOnClickListener(v -> photoPicker.show());
        imgProfile.setOnClickListener(v -> photoPicker.show());

        setupCountries();
        setupGenderDropdown();
        bindPickers();
        bindErrorClearers();
        prefillData();
        if (savedInstanceState != null) {
            localPhotoUri = savedInstanceState.getString("selected_photo", "");
            showProfileImage();
        }
        btnContinue.setOnClickListener(v -> saveProfile());

        View rootContent = findViewById(android.R.id.content);
        rootContent.setOnClickListener(v -> hideKeyboardAndClearFocus());
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        photoPicker.saveState(state);
        state.putString("selected_photo", localPhotoUri);
    }

    private void setupGenderDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_gender_dropdown, genders);
        edtGender.setAdapter(adapter);
        edtGender.setSimpleItems(genders);
        edtGender.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        edtGender.setOnClickListener(v -> edtGender.showDropDown());
        edtGender.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                edtGender.showDropDown();
            }
        });
        edtGender.setOnItemClickListener((parent, view, position, id) -> hideError(txtGenderError));
    }

    private void bindPickers() {
        edtDob.setOnClickListener(v -> openDatePicker());
        edtDob.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                openDatePicker();
            }
        });

        findViewById(R.id.countryPickerContainer).setOnClickListener(v -> showCountryPicker());
        txtCountryFlag.setOnClickListener(v -> showCountryPicker());
        txtCountryCode.setOnClickListener(v -> showCountryPicker());

        edtPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (s == null) {
                    return;
                }
                String cleaned = s.toString().replaceAll("[^0-9]", "");
                if (!cleaned.equals(s.toString())) {
                    edtPhone.setText(cleaned);
                    edtPhone.setSelection(cleaned.length());
                }
            }
        });
    }

    private void bindErrorClearers() {
        addClearErrorWatcher(edtFullName, txtFullNameError);
        addClearErrorWatcher(edtDob, txtDobError);
        addClearErrorWatcher(edtEmail, txtEmailError);
        addClearErrorWatcher(edtPhone, txtPhoneError);
        addClearErrorWatcher(edtGender, txtGenderError);
    }

    private void addClearErrorWatcher(EditText field, TextView errorView) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                hideError(errorView);
            }
        });
    }

    private void prefillData() {
        if (!TextUtils.isEmpty(currentUser.getDisplayName())) {
            edtFullName.setText(currentUser.getDisplayName());
        }

        if (currentUser.getPhotoUrl() != null) {
            remotePhotoUrl = currentUser.getPhotoUrl().toString();
        }

        String firebaseEmail = currentUser.getEmail();
        if (!TextUtils.isEmpty(firebaseEmail)) {
            edtEmail.setText(firebaseEmail);
            lockEmailField(true);
        } else {
            lockEmailField(false);
        }

        db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                showProfileImage();
                return;
            }

            setIfEmpty(edtFullName, snapshot.getString("name"));
            setIfEmpty(edtDob, snapshot.getString("dob"));
            setIfEmpty(edtGender, snapshot.getString("gender"));

            String savedPhoneLocal = snapshot.getString("phoneLocal");
            if (TextUtils.isEmpty(savedPhoneLocal)) {
                savedPhoneLocal = snapshot.getString("phone");
            }
            setIfEmpty(edtPhone, removeCountryCodeFromPhone(savedPhoneLocal));

            String savedEmail = snapshot.getString("email");
            if (!TextUtils.isEmpty(savedEmail)) {
                edtEmail.setText(savedEmail);
                lockEmailField(true);
            }

            String savedCountryCode = snapshot.getString("countryCode");
            String savedCountryIso = snapshot.getString("countryIso");
            if (!TextUtils.isEmpty(savedCountryIso) || !TextUtils.isEmpty(savedCountryCode)) {
                CountryItem matched = findCountry(savedCountryIso, savedCountryCode);
                if (matched != null) {
                    applySelectedCountry(matched);
                }
            }

            String savedRemoteStoragePath = snapshot.getString("photoStoragePath");
            if (!TextUtils.isEmpty(savedRemoteStoragePath)) {
                remotePhotoStoragePath = savedRemoteStoragePath;
            }

            String savedRemotePhotoUrl = snapshot.getString("photoUrl");
            if (TextUtils.isEmpty(localPhotoUri) && !TextUtils.isEmpty(savedRemotePhotoUrl)) {
                remotePhotoUrl = savedRemotePhotoUrl;
                localPhotoUri = "";
            } else if (TextUtils.isEmpty(localPhotoUri)) {
                String savedLocalPhotoUri = snapshot.getString("localPhotoUri");
                if (!TextUtils.isEmpty(savedLocalPhotoUri)) {
                    localPhotoUri = savedLocalPhotoUri;
                }
            }

            showProfileImage();
        }).addOnFailureListener(e -> showProfileImage());
    }

    private void showProfileImage() {
        if (!TextUtils.isEmpty(localPhotoUri)) {
            imgProfile.setPadding(0, 0, 0, 0);
            imgProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this)
                    .load(Uri.parse(localPhotoUri))
                    .placeholder(R.drawable.ic_profile_wireframe)
                    .error(R.drawable.ic_profile_wireframe)
                    .into(imgProfile);
            return;
        }

        if (!TextUtils.isEmpty(remotePhotoUrl)) {
            imgProfile.setPadding(0, 0, 0, 0);
            imgProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this)
                    .load(remotePhotoUrl)
                    .placeholder(R.drawable.ic_profile_wireframe)
                    .error(R.drawable.ic_profile_wireframe)
                    .into(imgProfile);
            return;
        }

        imgProfile.setPadding(0, 0, 0, 0);
        imgProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgProfile.setImageResource(R.drawable.ic_profile_wireframe);
    }


    private void setIfEmpty(EditText view, String value) {
        if (TextUtils.isEmpty(view.getText()) && !TextUtils.isEmpty(value)) {
            view.setText(value);
        }
    }

    private void lockEmailField(boolean locked) {
        edtEmail.setFocusable(!locked);
        edtEmail.setFocusableInTouchMode(!locked);
        edtEmail.setClickable(!locked);
        edtEmail.setCursorVisible(!locked);
        edtEmail.setLongClickable(!locked);
        edtEmail.setEnabled(true);
        edtEmail.setAlpha(1f);
    }

    private void openDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR) - 18;
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(this, (datePicker, y, m, d) ->
                edtDob.setText(String.format(Locale.US, "%02d/%02d/%04d", m + 1, d, y)), year, month, day);

        Calendar maxDate = Calendar.getInstance();
        maxDate.add(Calendar.YEAR, -13);
        dialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
        dialog.show();
    }

    private void showCountryPicker() {
        String[] items = new String[countries.size()];
        for (int i = 0; i < countries.size(); i++) {
            items[i] = countries.get(i).toString();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select country")
                .setItems(items, (dialog, which) -> applySelectedCountry(countries.get(which)))
                .show();
    }

    private void applySelectedCountry(CountryItem country) {
        selectedCountry = country;
        txtCountryFlag.setText(country.getFlagEmoji());
        txtCountryCode.setText(country.getPhoneCode());
    }

    private CountryItem findCountry(String isoCode, String phoneCode) {
        for (CountryItem item : countries) {
            boolean isoMatches = !TextUtils.isEmpty(isoCode) && item.getIsoCode().equalsIgnoreCase(isoCode);
            boolean codeMatches = !TextUtils.isEmpty(phoneCode) && item.getPhoneCode().equals(phoneCode);
            if (isoMatches || codeMatches) {
                return item;
            }
        }
        return null;
    }

    private void setupCountries() {
        countries.clear();
        countries.add(new CountryItem("Sri Lanka", "LK", "+94", "🇱🇰"));
        countries.add(new CountryItem("United States", "US", "+1", "🇺🇸"));
        countries.add(new CountryItem("United Kingdom", "GB", "+44", "🇬🇧"));
        countries.add(new CountryItem("India", "IN", "+91", "🇮🇳"));
        countries.add(new CountryItem("Australia", "AU", "+61", "🇦🇺"));
        countries.add(new CountryItem("Canada", "CA", "+1", "🇨🇦"));
        countries.add(new CountryItem("Germany", "DE", "+49", "🇩🇪"));
        countries.add(new CountryItem("France", "FR", "+33", "🇫🇷"));
        countries.add(new CountryItem("Italy", "IT", "+39", "🇮🇹"));
        countries.add(new CountryItem("Japan", "JP", "+81", "🇯🇵"));
        countries.add(new CountryItem("Singapore", "SG", "+65", "🇸🇬"));
        countries.add(new CountryItem("Malaysia", "MY", "+60", "🇲🇾"));
        countries.add(new CountryItem("United Arab Emirates", "AE", "+971", "🇦🇪"));
        countries.add(new CountryItem("Saudi Arabia", "SA", "+966", "🇸🇦"));
        countries.add(new CountryItem("Qatar", "QA", "+974", "🇶🇦"));
        countries.add(new CountryItem("Kuwait", "KW", "+965", "🇰🇼"));
        countries.add(new CountryItem("Bahrain", "BH", "+973", "🇧🇭"));
        countries.add(new CountryItem("Oman", "OM", "+968", "🇴🇲"));
        countries.add(new CountryItem("Maldives", "MV", "+960", "🇲🇻"));
        countries.add(new CountryItem("Pakistan", "PK", "+92", "🇵🇰"));
        countries.add(new CountryItem("Bangladesh", "BD", "+880", "🇧🇩"));
        countries.add(new CountryItem("Nepal", "NP", "+977", "🇳🇵"));
        countries.add(new CountryItem("South Africa", "ZA", "+27", "🇿🇦"));
        countries.add(new CountryItem("New Zealand", "NZ", "+64", "🇳🇿"));
        applySelectedCountry(countries.get(0));
    }

    private void saveProfile() {
        clearAllErrors();

        String fullName = edtFullName.getText().toString().trim();
        String dob = edtDob.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String phoneLocalRaw = edtPhone.getText().toString().trim();
        String gender = edtGender.getText().toString().trim();

        if (fullName.isEmpty()) {
            showError(txtFullNameError, "Please enter your full name.");
            edtFullName.requestFocus();
            return;
        }

        if (!isValidDateOfBirth(dob)) {
            showError(txtDobError, "Please select a valid date of birth.");
            edtDob.requestFocus();
            return;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(txtEmailError, "Please enter a valid email address.");
            edtEmail.requestFocus();
            return;
        }

        if (selectedCountry == null) {
            showError(txtPhoneError, "Please select your country and phone number.");
            edtPhone.requestFocus();
            return;
        }

        String normalizedLocalPhone = normalizeLocalPhone(phoneLocalRaw);
        if (!isValidPhoneNumber(normalizedLocalPhone)) {
            showError(txtPhoneError, "Please enter a valid phone number.");
            edtPhone.requestFocus();
            return;
        }

        if (gender.isEmpty()) {
            showError(txtGenderError, "Please select your gender.");
            edtGender.requestFocus();
            return;
        }

        btnContinue.setEnabled(false);
        btnContinue.setAlpha(0.7f);

        String fullPhone = selectedCountry.getPhoneCode() + normalizedLocalPhone;

        if (!TextUtils.isEmpty(localPhotoUri)) {
            originalPhotoStoragePathForCleanup = remotePhotoStoragePath;
            Uri selectedPhotoUri = Uri.parse(localPhotoUri);
            ProfilePhotoStorageUtil.uploadProfilePhoto(currentUser.getUid(), selectedPhotoUri, remotePhotoStoragePath,
                    new ProfilePhotoStorageUtil.UploadCallback() {
                        @Override
                        public void onSuccess(@NonNull String downloadUrl, @NonNull String storagePath) {
                            remotePhotoUrl = downloadUrl;
                            remotePhotoStoragePath = storagePath;
                            localPhotoUri = "";
                            persistProfile(fullName, dob, email, gender, fullPhone, normalizedLocalPhone, downloadUrl, storagePath);
                        }

                        @Override
                        public void onFailure() {
                            btnContinue.setEnabled(true);
                            btnContinue.setAlpha(1f);
                            AppMessageUtil.show(FillProfileActivity.this, "Could not upload profile photo. Please try again.", AppMessageUtil.TYPE_ERROR);
                        }
                    });
            return;
        }

        persistProfile(fullName, dob, email, gender, fullPhone, normalizedLocalPhone, remotePhotoUrl, remotePhotoStoragePath);
    }

    private void persistProfile(String fullName,
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
        data.put("name", fullName);
        data.put("dob", dob);
        data.put("email", email);
        data.put("phone", fullPhone);
        data.put("phoneLocal", normalizedLocalPhone);
        data.put("countryCode", selectedCountry.getPhoneCode());
        data.put("countryIso", selectedCountry.getIsoCode());
        data.put("countryFlagEmoji", selectedCountry.getFlagEmoji());
        data.put("gender", gender);
        data.put("profileCompleted", true);
        data.put("emailVerified", currentUser.isEmailVerified());
        data.put("localPhotoUri", com.google.firebase.firestore.FieldValue.delete());
        if (!TextUtils.isEmpty(finalPhotoUrl)) {
            data.put("photoUrl", finalPhotoUrl);
        } else {
            data.put("photoUrl", com.google.firebase.firestore.FieldValue.delete());
        }
        if (!TextUtils.isEmpty(finalPhotoStoragePath)) {
            data.put("photoStoragePath", finalPhotoStoragePath);
        } else {
            data.put("photoStoragePath", com.google.firebase.firestore.FieldValue.delete());
        }

        db.collection("users").document(currentUser.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (!TextUtils.isEmpty(previousPhotoStoragePath)
                            && !previousPhotoStoragePath.equals(finalPhotoStoragePath)) {
                        ProfilePhotoStorageUtil.deleteByPath(previousPhotoStoragePath);
                    }
                    originalPhotoStoragePathForCleanup = "";
                    photoPicker.clearSelected();
                    currentUser.updateProfile(
                                new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                        .setDisplayName(fullName)
                                        .setPhotoUri(TextUtils.isEmpty(finalPhotoUrl) ? null : Uri.parse(finalPhotoUrl))
                                        .build())
                        .addOnCompleteListener(task -> UserFlowNavigator.launch(this, CreatePinActivity.class, true));
                })
                .addOnFailureListener(e -> {
                    btnContinue.setEnabled(true);
                    btnContinue.setAlpha(1f);
                    AppMessageUtil.show(this, "Could not save profile. Please try again.", AppMessageUtil.TYPE_ERROR);
                });
    }

    private void clearAllErrors() {
        hideError(txtFullNameError);
        hideError(txtDobError);
        hideError(txtEmailError);
        hideError(txtPhoneError);
        hideError(txtGenderError);
    }

    private void showError(TextView view, String message) {
        view.setText(message);
        view.setVisibility(TextView.VISIBLE);
    }

    private void hideError(TextView view) {
        view.setText("");
        view.setVisibility(TextView.GONE);
    }

    private boolean isValidDateOfBirth(String dob) {
        if (TextUtils.isEmpty(dob)) {
            return false;
        }

        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        format.setLenient(false);
        try {
            Date parsedDate = format.parse(dob);
            if (parsedDate == null) {
                return false;
            }

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
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("[^0-9]", "");
        while (digits.startsWith("0") && digits.length() > 9) {
            digits = digits.substring(1);
        }
        return digits;
    }

    private boolean isValidPhoneNumber(String localNumber) {
        return !TextUtils.isEmpty(localNumber) && localNumber.length() >= 7 && localNumber.length() <= 12;
    }

    private String removeCountryCodeFromPhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() > 12) {
            return digits.substring(digits.length() - 9);
        }
        return digits;
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
