package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.SystemBarUtil;
import com.nexcentauri.dayanisupercenter.util.AppMessageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AddressActivity extends AppCompatActivity {

    private LinearLayout addressListContainer;
    private TextView txtEmpty;
    private View emptyStateCard;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address);
        SystemBarUtil.matchStatusBarToContent(this);

        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        addressListContainer = findViewById(R.id.addressListContainer);
        txtEmpty = findViewById(R.id.txtEmpty);
        emptyStateCard = findViewById(R.id.emptyStateCard);
        MaterialButton btnAdd = findViewById(R.id.btnAddAddress);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> startActivity(new Intent(this, EditAddressActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAddresses();
    }

    private void loadAddresses() {
        addressListContainer.removeAllViews();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("users").document(user.getUid())
                .collection("addresses").get(Source.CACHE)
                .addOnSuccessListener(this::renderAddresses)
                .addOnFailureListener(e -> { });

        firestore.collection("users").document(user.getUid())
                .collection("addresses").get(Source.SERVER)
                .addOnSuccessListener(this::renderAddresses);
    }

    private void renderAddresses(com.google.firebase.firestore.QuerySnapshot snapshots) {
        if (snapshots == null) return;
        addressListContainer.removeAllViews();
        List<QueryDocumentSnapshot> docs = new ArrayList<>();
        for (QueryDocumentSnapshot snapshot : snapshots) {
            docs.add(snapshot);
        }
        Collections.sort(docs, Comparator.comparing((QueryDocumentSnapshot doc) -> !Boolean.TRUE.equals(doc.getBoolean("isDefault"))));

        boolean isEmpty = docs.isEmpty();
        emptyStateCard.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        addressListContainer.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        txtEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (QueryDocumentSnapshot doc : docs) {
            View view = inflater.inflate(R.layout.item_address, addressListContainer, false);
            TextView txtLabel = view.findViewById(R.id.txtLabel);
            TextView txtAddress = view.findViewById(R.id.txtAddress);
            TextView txtDefault = view.findViewById(R.id.txtDefault);
            ImageView imgEdit = view.findViewById(R.id.imgEdit);

            String label = doc.getString("label");
            String details = doc.getString("details");
            boolean isDefault = Boolean.TRUE.equals(doc.getBoolean("isDefault"));

            txtLabel.setText(label == null || label.trim().isEmpty() ? "Address" : label);
            txtAddress.setText(details == null || details.trim().isEmpty() ? "No address details" : details);
            txtDefault.setVisibility(isDefault ? View.VISIBLE : View.GONE);

            imgEdit.setOnClickListener(v -> {
                Intent intent = new Intent(this, EditAddressActivity.class);
                intent.putExtra("addressId", doc.getId());
                startActivity(intent);
            });

            view.setOnLongClickListener(v -> {
                showDeleteDialog(doc.getReference(), isDefault);
                return true;
            });
            addressListContainer.addView(view);
        }
    }

    private void showDeleteDialog(DocumentReference addressRef, boolean wasDefault) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Address")
                .setMessage("Are you sure you want to delete this address?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteAddress(addressRef, wasDefault))
                .show();
    }

    private void deleteAddress(DocumentReference addressRef, boolean wasDefault) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        addressRef.delete()
                .addOnSuccessListener(unused -> {
                    if (wasDefault) {
                        setAnotherAddressAsDefault(firestore);
                    } else {
                        loadAddresses();
                    }
                })
                .addOnFailureListener(e -> AppMessageUtil.show(this, "Could not delete address.", AppMessageUtil.TYPE_ERROR));
    }

    private void setAnotherAddressAsDefault(FirebaseFirestore firestore) {
        firestore.collection("users").document(user.getUid())
                .collection("addresses")
                .limit(1)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        snapshots.getDocuments().get(0).getReference()
                                .update("isDefault", true)
                                .addOnCompleteListener(task -> loadAddresses());
                    } else {
                        loadAddresses();
                    }
                })
                .addOnFailureListener(e -> loadAddresses());
    }
}
