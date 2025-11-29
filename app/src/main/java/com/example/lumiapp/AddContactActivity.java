package com.example.lumiapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AddContactActivity extends AppCompatActivity {

    private TextInputEditText etName, etPhone, etEmail;
    private MaterialAutoCompleteTextView actProperty;
    private TextInputLayout tilProperty;
    private MaterialButton btnSave;
    private ImageButton backBtn;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private final ArrayList<String> propertyNames = new ArrayList<>();
    private ArrayAdapter<String> propertyAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contact);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initViews();
        setupPropertyDropdown();
        setupListeners();
    }

    private void initViews() {
        backBtn     = findViewById(R.id.back_btn);
        etName      = findViewById(R.id.etName);
        etPhone     = findViewById(R.id.etPhone);
        etEmail     = findViewById(R.id.etEmail);
        tilProperty = findViewById(R.id.tilProperty);
        actProperty = findViewById(R.id.actProperty);
        btnSave     = findViewById(R.id.btnSave);

        actProperty.setOnClickListener(v -> actProperty.showDropDown());
    }

    private void setupPropertyDropdown() {
        propertyAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                propertyNames
        );
        actProperty.setAdapter(propertyAdapter);

        db.collection("properties")
                .get()
                .addOnSuccessListener(qs -> {
                    propertyNames.clear();
                    for (QueryDocumentSnapshot doc : qs) {
                        String name = doc.getString("name");
                        String addr = doc.getString("address");
                        String display;
                        if (!TextUtils.isEmpty(name)) display = name;
                        else if (!TextUtils.isEmpty(addr)) display = addr;
                        else display = doc.getId();

                        if (!propertyNames.contains(display)) {
                            propertyNames.add(display);
                        }
                    }
                    propertyAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(
                        this,
                        "Failed to load properties: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
    }

    private void setupListeners() {
        backBtn.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveContact());
    }

    private void saveContact() {
        // Make sure we have a logged-in manager
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be logged in to add a contact.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String currentUserId = user.getUid();

        String name     = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone    = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String email    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String property = actProperty.getText() != null ? actProperty.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("phone", phone);
        data.put("email", email);
        data.put("propertyName", property);
        data.put("createdAt", Timestamp.now());
        data.put("isCustom", true);

        // 🔹 IMPORTANT: mark who created this contact
        data.put("createdById", currentUserId);

        btnSave.setEnabled(false);

        db.collection("contacts")
                .add(data)
                .addOnSuccessListener(docRef -> {
                    Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this,
                            "Failed to add contact: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
