package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.checkout.CheckoutPrefs;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CheckoutAddressSelectionActivity extends AppCompatActivity {

    private LinearLayout container;
    private String selectedAddressId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout_address_selection);
        SystemBarUtil.matchStatusBarToContent(this);

        container = findViewById(R.id.addressListContainer);
        selectedAddressId = CheckoutPrefs.getSelectedAddressId(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddNewAddress).setOnClickListener(v -> startActivity(new Intent(this, EditAddressActivity.class)));
        findViewById(R.id.btnApply).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAddresses();
    }

    private void loadAddresses() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .collection("addresses")
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) docs.add(doc);
                    Collections.sort(docs, Comparator.comparing((QueryDocumentSnapshot doc) -> !Boolean.TRUE.equals(doc.getBoolean("isDefault"))));
                    render(docs);
                })
                .addOnFailureListener(e -> com.nexcentauri.dayanisupercenter.util.AppMessageUtil.show(
                        this, "Could not load addresses. Check your connection.",
                        com.nexcentauri.dayanisupercenter.util.AppMessageUtil.TYPE_ERROR));
    }

    private void render(List<QueryDocumentSnapshot> docs) {
        container.removeAllViews();
        View emptyCard = findViewById(R.id.layoutEmptyCard);
        emptyCard.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
        boolean selectedAddressExists = false;
        for (QueryDocumentSnapshot doc : docs) {
            if (TextUtils.equals(selectedAddressId, doc.getId())) selectedAddressExists = true;
        }
        if (!selectedAddressExists) {
            selectedAddressId = null;
            CheckoutPrefs.clearSelectedAddress(this);
        }
        if (docs.size() == 1) {
            selectedAddressId = docs.get(0).getId();
        } else if (TextUtils.isEmpty(selectedAddressId) && !docs.isEmpty()) {
            for (QueryDocumentSnapshot doc : docs) {
                if (Boolean.TRUE.equals(doc.getBoolean("isDefault"))) {
                    selectedAddressId = doc.getId();
                    break;
                }
            }
            if (TextUtils.isEmpty(selectedAddressId)) {
                selectedAddressId = docs.get(0).getId();
            }
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (QueryDocumentSnapshot doc : docs) {
            View row = inflater.inflate(R.layout.item_checkout_address_option, container, false);
            TextView txtLabel = row.findViewById(R.id.txtLabel);
            TextView txtDetails = row.findViewById(R.id.txtDetails);
            TextView txtDefault = row.findViewById(R.id.txtDefault);
            ImageView imgRadio = row.findViewById(R.id.imgRadio);

            txtLabel.setText(safe(doc.getString("label"), "Address"));
            txtDetails.setText(safe(doc.getString("details"), ""));
            txtDefault.setVisibility(Boolean.TRUE.equals(doc.getBoolean("isDefault")) ? View.VISIBLE : View.GONE);

            boolean selected = TextUtils.equals(selectedAddressId, doc.getId())
                    || (TextUtils.isEmpty(selectedAddressId) && Boolean.TRUE.equals(doc.getBoolean("isDefault")))
                    || (docs.size() == 1);
            if (selected) {
                selectedAddressId = doc.getId();
                imgRadio.setImageResource(R.drawable.ic_radio_checked);
                CheckoutPrefs.saveSelectedAddress(this, doc.getId(), safe(doc.getString("label"), "Address"), safe(doc.getString("details"), ""), number(doc.getDouble("lat")), number(doc.getDouble("lng")));
            } else {
                imgRadio.setImageResource(R.drawable.ic_radio_unchecked);
            }

            row.setOnClickListener(v -> {
                selectedAddressId = doc.getId();
                CheckoutPrefs.saveSelectedAddress(this, doc.getId(), safe(doc.getString("label"), "Address"), safe(doc.getString("details"), ""), number(doc.getDouble("lat")), number(doc.getDouble("lng")));
                render(docs);
            });
            container.addView(row);
        }
        if (docs.isEmpty()) {
            CheckoutPrefs.clearSelectedAddress(this);
        }
    }

    private static String safe(String value, String fallback) { return TextUtils.isEmpty(value) ? fallback : value; }
    private static double number(Double value) { return value == null ? 0d : value; }
}
