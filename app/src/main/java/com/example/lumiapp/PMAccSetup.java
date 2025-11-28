package com.example.lumiapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class PMAccSetup extends AppCompatActivity {

    private TextInputLayout tilName, tilAddress, tilType, tilRenter, tilParking;
    private TextInputEditText etName, etAddress, etRenter, etParking;
    private MaterialAutoCompleteTextView etType;
    private MaterialButton btnCreateProperty;
    private FrameLayout uploadImageZone;
    private ImageView imgPreview;
    private TextView tvPlus;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private Uri selectedImageUri = null;
    private ActivityResultLauncher<String> pickImageLauncher;

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
        setupImagePicker();

        btnCreateProperty.setOnClickListener(v -> trySaveProperty());
        uploadImageZone.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void bindViews() {
        tilName    = findViewById(R.id.til_propertyName);
        tilAddress = findViewById(R.id.til_propertyAddress);
        tilType    = findViewById(R.id.tilType);
        tilRenter  = findViewById(R.id.til_renterCapacity);
        tilParking = findViewById(R.id.til_parkingCapacity);

        etName     = findViewById(R.id.input_propertyName);
        etType     = findViewById(R.id.input_propertyType);
        etAddress  = findViewById(R.id.input_propertyAddress);
        etRenter   = findViewById(R.id.input_renterCapacity);
        etParking  = findViewById(R.id.input_ParkingCapacity);

        btnCreateProperty = findViewById(R.id.btn_createProperty);
        uploadImageZone   = findViewById(R.id.uploadImage);
        imgPreview        = findViewById(R.id.imgPreview);
        tvPlus            = findViewById(R.id.tvPlus);
    }

    private void setupDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, PROPERTY_TYPES
        );
        etType.setAdapter(adapter);
        etType.setOnClickListener(v -> etType.showDropDown());
    }

    private void setupImagePicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgPreview.setImageURI(uri);
                        imgPreview.setVisibility(android.view.View.VISIBLE);
                        if (tvPlus != null) {
                            tvPlus.setVisibility(android.view.View.GONE);
                        }
                    }
                }
        );
    }

    private void trySaveProperty() {
        clearErrors();

        String name    = s(etName);
        String type    = s(etType);
        String address = s(etAddress);
        String renters = s(etRenter);
        String parking = s(etParking);

        boolean ok = true;
        if (TextUtils.isEmpty(name))    { setErr(tilName, getString(R.string.required)); ok = false; }
        if (TextUtils.isEmpty(type))    { setErr(tilType, getString(R.string.required)); ok = false; }
        if (TextUtils.isEmpty(address)) { setErr(tilAddress, getString(R.string.required)); ok = false; }
        if (TextUtils.isEmpty(renters)) { setErr(tilRenter, getString(R.string.required)); ok = false; }
        if (TextUtils.isEmpty(parking)) { setErr(tilParking, getString(R.string.required)); ok = false; }

        Integer renterCap = null;
        Integer parkingCap = null;
        try { renterCap = Integer.parseInt(renters); } catch (Exception ignored) {}
        try { parkingCap = Integer.parseInt(parking); } catch (Exception ignored) {}

        if (renterCap == null || renterCap < 0) {
            setErr(tilRenter, getString(R.string.invalid_number)); ok = false;
        }
        if (parkingCap == null || parkingCap < 0) {
            setErr(tilParking, getString(R.string.invalid_number)); ok = false;
        }

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
        property.put("createdAt", Timestamp.now());

        // Pre-create a document so we can use its ID for Storage path
        DocumentReference propRef = db.collection("properties").document();
        String propertyId = propRef.getId();

        if (selectedImageUri != null) {
            uploadImageAndSaveProperty(propertyId, propRef, uid, property);
        } else {
            // No image selected, just save property doc
            savePropertyDoc(propRef, uid, property);
        }
    }

    private void uploadImageAndSaveProperty(String propertyId,
                                            DocumentReference propRef,
                                            String uid,
                                            Map<String, Object> property) {

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("propertyImages")
                .child(propertyId + ".jpg");

        storageRef.putFile(selectedImageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return storageRef.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    property.put("imageUrl", uri.toString());
                    savePropertyDoc(propRef, uid, property);
                })
                .addOnFailureListener(e -> {
                    btnCreateProperty.setEnabled(true);
                    Toast.makeText(this,
                            "Image upload or URL failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void savePropertyDoc(DocumentReference propRef,
                                 String uid,
                                 Map<String, Object> property) {

        propRef.set(property)
                .addOnSuccessListener(unused -> {
                    // update user doc
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("pmCompleted", true);
                    updates.put("userType", "manager");
                    // Keep adding properties instead of overwriting
                    updates.put("managerOf", FieldValue.arrayUnion(propRef.getId()));

                    db.collection("users").document(uid)
                            .set(updates, SetOptions.merge())
                            .addOnSuccessListener(v -> {
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
