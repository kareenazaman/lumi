package com.example.lumiapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RenterAccSetup extends AppCompatActivity {

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // UI
    private MaterialAutoCompleteTextView inputProperty;
    private TextInputEditText inputRoom, inputRefName, inputRefContact,
            inputPrevAddress, inputEmgName, inputEmgNumber, inputCarPlate;
    private FrameLayout uploadProfile;
    private ImageView imgProfile;
    private MaterialButton btnComplete;

    private Uri selectedImageUri = null;
    private ArrayAdapter<String> propertyAdapter;
    private final List<String> propertyNames = new ArrayList<>();
    private final List<String> propertyIds = new ArrayList<>();

    // Image picker launcher
    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                selectedImageUri = uri;
                                imgProfile.setImageURI(uri);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_renteracc_setup);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initFirebase();
        initViews();
        setupPropertyDropdown();
        setupListeners();
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
    }

    private void initViews() {
        inputProperty = findViewById(R.id.input_property);
        inputRoom = findViewById(R.id.input_room);
        inputRefName = findViewById(R.id.input_ref_name);
        inputRefContact = findViewById(R.id.input_ref_contact);
        inputPrevAddress = findViewById(R.id.input_prev_address);
        inputEmgName = findViewById(R.id.input_emg_name);
        inputEmgNumber = findViewById(R.id.input_emg_number);
        inputCarPlate = findViewById(R.id.input_car_plate);

        uploadProfile = findViewById(R.id.uploadProfile);
        imgProfile = findViewById(R.id.imgProfile);
        btnComplete = findViewById(R.id.btnComplete);

        propertyAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                propertyNames
        );
        inputProperty.setAdapter(propertyAdapter);
    }

    /**
     * Load all available properties from Firestore into the dropdown
     * Adjust collection name and fields according to your schema.
     */
    private void setupPropertyDropdown() {
        db.collection("properties")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    propertyNames.clear();
                    propertyIds.clear();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String id = doc.getId();

                        // You have: address, name, ownerUid, type, etc.
                        String name = doc.getString("name");
                        String address = doc.getString("address");

                        if (name == null) name = "(Unnamed)";
                        if (address == null) address = "";

                        // You can format how it shows in dropdown:
                        String display = name;
                        if (!address.isEmpty()) {
                            display = name + " - " + address;
                        }

                        propertyIds.add(id);
                        propertyNames.add(display);
                    }

                    propertyAdapter.notifyDataSetChanged();

                    if (propertyNames.isEmpty()) {
                        Toast.makeText(this, "No properties found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load properties: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }


    private void setupListeners() {
        uploadProfile.setOnClickListener(v -> openImagePicker());

        btnComplete.setOnClickListener(v -> {
            if (!validateInputs()) return;
            saveProfile();
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }

    private boolean validateInputs() {
        if (inputProperty.getText() == null || inputProperty.getText().toString().trim().isEmpty()) {
            inputProperty.setError("Please select a property");
            inputProperty.requestFocus();
            return false;
        }

        if (inputRoom.getText() == null || inputRoom.getText().toString().trim().isEmpty()) {
            inputRoom.setError("Room number is required");
            inputRoom.requestFocus();
            return false;
        }

        if (inputEmgNumber.getText() == null || inputEmgNumber.getText().toString().trim().isEmpty()) {
            inputEmgNumber.setError("Emergency contact number is required");
            inputEmgNumber.requestFocus();
            return false;
        }

        // You can add more validation here if you want (car plate, ref contact, etc.)
        return true;
    }

    private void saveProfile() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = auth.getCurrentUser().getUid();
        String selectedPropertyName = inputProperty.getText().toString().trim();
        String propertyId = null;

        // Map the selected property name back to its id
        int index = propertyNames.indexOf(selectedPropertyName);
        if (index >= 0 && index < propertyIds.size()) {
            propertyId = propertyIds.get(index);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("propertyName", selectedPropertyName);
        data.put("propertyId", propertyId);
        data.put("roomNumber", textOrNull(inputRoom));
        data.put("referenceName", textOrNull(inputRefName));
        data.put("referenceContact", textOrNull(inputRefContact));
        data.put("previousAddress", textOrNull(inputPrevAddress));
        data.put("emergencyName", textOrNull(inputEmgName));
        data.put("emergencyNumber", textOrNull(inputEmgNumber));
        data.put("carPlate", textOrNull(inputCarPlate));

        // If user picked an image, upload to Storage then save Firestore
        if (selectedImageUri != null) {
            uploadProfileImageAndSave(userId, data);
        } else {
            // No photo, just save profile data
            saveProfileToFirestore(userId, data);
        }
    }

    private void uploadProfileImageAndSave(String userId, Map<String, Object> data) {
        StorageReference ref = storage.getReference()
                .child("profile_photos")
                .child(userId + ".jpg");

        ref.putFile(selectedImageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> {
                    data.put("profilePhotoUrl", uri.toString());
                    saveProfileToFirestore(userId, data);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to upload photo: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    private void saveProfileToFirestore(String userId, Map<String, Object> data) {
        // Adjust collection name to your actual users/renters collection
        db.collection("renters")
                .document(userId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Profile completed!", Toast.LENGTH_SHORT).show();
                    // TODO: Navigate to Dashboard or finish() if needed
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to save profile: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    private String textOrNull(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) return null;
        String t = editText.getText().toString().trim();
        return t.isEmpty() ? null : t;
    }
}
