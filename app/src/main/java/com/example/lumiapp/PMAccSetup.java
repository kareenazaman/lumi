package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PMAccSetup extends AppCompatActivity {

    private TextInputLayout tilName, tilAddress, tilType, tilRenter, tilParking;
    private TextInputEditText etName, etAddress, etRenter, etParking;
    private MaterialAutoCompleteTextView etType;
    private MaterialButton btnCreateProperty;
    private FrameLayout uploadImageZone; // placeholder; you can add Storage upload later

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private static final String[] PROPERTY_TYPES = new String[]{
            "Apartment", "Condo", "Townhouse", "Detached House", "Mixed-use", "Commercial"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pmacc_setup);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // If not logged in, kick back to SignupActivity
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
            return;
        }

        bindViews();
        setupDropdown();

        btnCreateProperty.setOnClickListener(v -> trySaveProperty());
        uploadImageZone.setOnClickListener(v ->
                Toast.makeText(this, "Image upload coming soon.", Toast.LENGTH_SHORT).show()
        );
    }

    private void bindViews() {
        tilName    = findViewById(R.id.til_propertyName);
        tilAddress = findViewById(R.id.til_propertyAddress);
        tilType    = findViewById(R.id.tilType);
        tilRenter  = findViewById(R.id.til_renterCapacity);
        tilParking = findViewById(R.id.til_parkingCapacity);

        // Your ids from the XML body
        etName     = findViewById(R.id.input_propertyName);
        etType     = findViewById(R.id.input_propertyType);
        etAddress  = findViewById(R.id.input_propertyAddress);
        etRenter   = findViewById(R.id.input_renterCapacity);
        etParking  = findViewById(R.id.input_ParkingCapacity);

        btnCreateProperty = findViewById(R.id.btn_createProperty);
        uploadImageZone   = findViewById(R.id.uploadImage);
    }

    private void setupDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, PROPERTY_TYPES
        );
        etType.setAdapter(adapter);
        etType.setOnClickListener(v -> etType.showDropDown());
    }

    private void trySaveProperty() {
        clearErrors();

        String name    = s(etName);
        String type    = s(etType);
        String address = s(etAddress);
        String renters = s(etRenter);
        String parking = s(etParking);

        boolean ok = true;
        if (TextUtils.isEmpty(name))   { setErr(tilName, getString(R.string.required)); ok = false; }
        if (TextUtils.isEmpty(type))   { setErr(tilType, getString(R.string.required)); ok = false; }
        if (TextUtils.isEmpty(address)){ setErr(tilAddress, getString(R.string.required)); ok = false; }
        if (TextUtils.isEmpty(renters)){ setErr(tilRenter, getString(R.string.required)); ok = false; }
        if (TextUtils.isEmpty(parking)){ setErr(tilParking, getString(R.string.required)); ok = false; }

        Integer renterCap = null;
        Integer parkingCap = null;
        try { renterCap = Integer.parseInt(renters); } catch (Exception ignored) {}
        try { parkingCap = Integer.parseInt(parking); } catch (Exception ignored) {}

        if (renterCap == null || renterCap < 0) { setErr(tilRenter, getString(R.string.invalid_number)); ok = false; }
        if (parkingCap == null || parkingCap < 0) { setErr(tilParking, getString(R.string.invalid_number)); ok = false; }

        if (!ok) return;

        btnCreateProperty.setEnabled(false);

        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> property = new HashMap<>();
        property.put("name", name);
        property.put("type", type);
        property.put("address", address);
        property.put("renterCapacity", renterCap);
        property.put("parkingCapacity", parkingCap);
        property.put("ownerUid", uid);
        property.put("createdAt", com.google.firebase.Timestamp.now());

        // Save property, then mark user.pmCompleted=true
        db.collection("properties").add(property)
                .addOnSuccessListener(docRef -> {
                    // update user doc
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("pmCompleted", true);
                    updates.put("userType", "manager");
                    updates.put("managerOf", java.util.Collections.singletonList(docRef.getId()));

                    db.collection("users").document(uid).set(updates, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Property saved!", Toast.LENGTH_SHORT).show();
                                goToDashboard();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Saved property, but failed to update user: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                goToDashboard(); // still proceed
                            });
                })
                .addOnFailureListener(e -> {
                    btnCreateProperty.setEnabled(true);
                    Toast.makeText(this, "Failed to save property: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void goToDashboard() {
        Intent i = new Intent(PMAccSetup.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void clearErrors() {
        setErr(tilName, null);
        setErr(tilAddress, null);
        setErr(tilType, null);
        setErr(tilRenter, null);
        setErr(tilParking, null);
    }

    private void setErr(TextInputLayout til, String msg) {
        if (til != null) til.setError(msg);
    }

    private String s(android.widget.TextView tv) {
        CharSequence cs = tv.getText();
        return cs == null ? "" : cs.toString().trim();
    }
}
