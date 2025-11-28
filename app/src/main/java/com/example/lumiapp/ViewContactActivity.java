package com.example.lumiapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ViewContactActivity extends AppCompatActivity {

    private TextInputEditText etName, etPhone, etEmail;
    private TextInputLayout tilProperty;
    private MaterialAutoCompleteTextView actProperty;

    private MaterialButton btnEdit, btnSave, btnDelete;
    private ImageButton backBtn;

    private FirebaseFirestore db;

    private String contactId;
    private boolean isCustom; // true = from contacts, false = renter (users collection)
    private String role;      // "admin" / "manager" / "renter"
    private boolean isAdmin;

    private final ArrayList<String> propertyNames = new ArrayList<>();
    private android.widget.ArrayAdapter<String> propertyAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_contact);

        db = FirebaseFirestore.getInstance();

        readIntentExtras();
        initViews();
        setupPropertyDropdown();   // load properties into dropdown
        fillInitialData();
        setupRolePermissions();
        setupListeners();
    }

    private void readIntentExtras() {
        contactId = getIntent().getStringExtra("contactId");
        isCustom = getIntent().getBooleanExtra("isCustom", false);
        role = getIntent().getStringExtra("role");
    }

    private void initViews() {
        backBtn   = findViewById(R.id.back_btn);
        btnEdit   = findViewById(R.id.btnEdit);
        btnSave   = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);

        etName  = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);

        tilProperty = findViewById(R.id.tilProperty);
        actProperty = findViewById(R.id.actProperty);

        // dropdown behavior
        if (actProperty != null) {
            actProperty.setOnClickListener(v -> actProperty.showDropDown());
        }

        // Start as read-only
        setFieldsEditable(false);
    }

    private void setupPropertyDropdown() {
        if (actProperty == null) return;

        propertyAdapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                propertyNames
        );
        actProperty.setAdapter(propertyAdapter);

        db.collection("properties")
                .get()
                .addOnSuccessListener(qs -> {
                    propertyNames.clear();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : qs) {
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
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Failed to load properties: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void fillInitialData() {
        String name     = getIntent().getStringExtra("name");
        String phone    = getIntent().getStringExtra("phone");
        String email    = getIntent().getStringExtra("email");
        String property = getIntent().getStringExtra("propertyName");

        if (etName != null)      etName.setText(name);
        if (etPhone != null)     etPhone.setText(phone);
        if (etEmail != null)     etEmail.setText(email);
        if (actProperty != null) actProperty.setText(property, false);
    }

    private void setupRolePermissions() {
        isAdmin = role != null
                && role.toLowerCase(Locale.ROOT).equals("admin");

        if (!isAdmin) {
            // Non-admin: view only
            if (btnEdit != null)   btnEdit.setVisibility(MaterialButton.GONE);
            if (btnSave != null)   btnSave.setVisibility(MaterialButton.GONE);
            if (btnDelete != null) btnDelete.setVisibility(MaterialButton.GONE);
            setFieldsEditable(false);
        } else {
            // Admin: can edit and delete
            if (btnEdit != null)   btnEdit.setVisibility(MaterialButton.VISIBLE);
            if (btnSave != null)   btnSave.setVisibility(MaterialButton.GONE); // only in edit mode
            if (btnDelete != null) btnDelete.setVisibility(MaterialButton.VISIBLE);
        }
    }

    private void setupListeners() {
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> {
                setFieldsEditable(true);
                btnSave.setVisibility(MaterialButton.VISIBLE);
            });
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveChanges());
        }

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> confirmDelete());
        }
    }

    private void setFieldsEditable(boolean editable) {
        if (etName != null) etName.setEnabled(editable);
        if (etPhone != null) etPhone.setEnabled(editable);
        if (etEmail != null) etEmail.setEnabled(editable);
        if (actProperty != null) actProperty.setEnabled(editable);
        if (tilProperty != null) tilProperty.setEnabled(editable);
    }

    private void saveChanges() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String property = actProperty.getText() != null ? actProperty.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(contactId)) {
            Toast.makeText(this, "Missing contact ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();

        if (isCustom) {
            // custom contact in "contacts" collection
            updates.put("name", name);
            updates.put("phone", phone);
            updates.put("email", email);
            updates.put("propertyName", property);

            btnSave.setEnabled(false);

            db.collection("contacts")
                    .document(contactId)
                    .update(updates)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                        setFieldsEditable(false);
                        btnSave.setVisibility(MaterialButton.GONE);
                    })
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(this,
                                "Failed to update: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        } else {
            // renter contact → "users" collection
            updates.put("fullName", name);          // adjust to your user doc schema if needed
            updates.put("phone", phone);
            updates.put("email", email);
            updates.put("propertyName", property);  // optional, depending on schema

            btnSave.setEnabled(false);

            db.collection("users")
                    .document(contactId)
                    .update(updates)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Renter updated", Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                        setFieldsEditable(false);
                        btnSave.setVisibility(MaterialButton.GONE);
                    })
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(this,
                                "Failed to update renter: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void confirmDelete() {
        if (!isAdmin) {
            Toast.makeText(this, "You don't have permission to delete this contact", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(contactId)) {
            Toast.makeText(this, "Missing contact ID", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete contact")
                .setMessage("Are you sure you want to delete this contact?")
                .setPositiveButton("Delete", (dialog, which) -> performDelete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performDelete() {
        if (btnDelete != null) btnDelete.setEnabled(false);

        // isCustom = true  → "contacts" collection
        // isCustom = false → "users" collection (renter contact)
        String collection = isCustom ? "contacts" : "users";

        db.collection(collection)
                .document(contactId)
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
                    finish(); // go back to list
                })
                .addOnFailureListener(e -> {
                    if (btnDelete != null) btnDelete.setEnabled(true);
                    Toast.makeText(this,
                            "Failed to delete: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
