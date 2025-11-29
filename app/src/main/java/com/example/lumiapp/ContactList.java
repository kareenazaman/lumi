package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class ContactList extends AppCompatActivity {

    private static final String TAG = "Contacts";

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvContacts;
    private ContactAdapter adapter;
    private EditText etSearch;
    private ImageButton btnSearch;
    private ImageButton backBtn;
    private FloatingActionButton fabAdd;

    private String userId;
    // From users/{uid}.userType → "manager" or "renter"
    private String role;

    // contacts collection docs
    private final List<Contact> customContacts = new ArrayList<>();
    // renters (from renters + users)
    private final List<Contact> renterContacts = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        initViews();
        setupRecycler();
        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserRoleAndContacts();
    }

    /** true if current user is a Property Manager */
    private boolean isManager() {
        return role != null && role.equalsIgnoreCase("manager");
    }

    private void initViews() {
        rvContacts = findViewById(R.id.rvContacts);
        etSearch   = findViewById(R.id.etSearch);
        btnSearch  = findViewById(R.id.btnSearch);
        backBtn    = findViewById(R.id.back_btn);
        fabAdd     = findViewById(R.id.fabAdd);

        // Live search while typing
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applySearch();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupRecycler() {
        adapter = new ContactAdapter(contact -> {
            Intent i = new Intent(ContactList.this, ViewContactActivity.class);
            i.putExtra("contactId", contact.getId());
            i.putExtra("name", contact.getName());
            i.putExtra("phone", contact.getPhone());
            i.putExtra("email", contact.getEmail());
            i.putExtra("propertyName", contact.getPropertyName());
            i.putExtra("isCustom", contact.isCustom());
            i.putExtra("role", role);   // "manager" or "renter"
            startActivity(i);
        });

        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        rvContacts.setAdapter(adapter);
    }

    private void setupButtons() {
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                Intent i = new Intent(ContactList.this, AddContactActivity.class);
                startActivity(i);
            });
        }

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> applySearch());
        }
    }

    // ================== ROLE + DATA LOADING ==================

    private void loadUserRoleAndContacts() {
        if (userId == null) {
            Log.w(TAG, "No logged-in user");
            return;
        }

        db.collection("users").document(userId).get()
                .addOnSuccessListener(this::onUserLoaded)
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to load user for role", e));
    }

    private void onUserLoaded(DocumentSnapshot snap) {
        if (snap != null && snap.exists()) {
            role = snap.getString("userType");   // "manager" or "renter"
        }

        // Update FAB visibility based on role
        if (fabAdd != null) {
            if (isManager()) fabAdd.show();
            else fabAdd.hide();  // renter cannot add contacts
        }

        loadContactsInternal();
    }

    private void loadContactsInternal() {
        customContacts.clear();
        renterContacts.clear();

        // 1) Load ALL contacts from contacts collection (no owner filter)
        db.collection("contacts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String id           = doc.getId();
                        String name         = doc.getString("name");
                        String phone        = doc.getString("phone");
                        String email        = doc.getString("email");
                        String propertyName = doc.getString("propertyName");

                        Contact c = new Contact(id, name, phone, email, propertyName, true);
                        customContacts.add(c);
                    }

                    // 2) Load ALL renters (for both manager & renter view)
                    loadAllRenters();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load contacts", e);
                    // Even if contact load fails, still try renters
                    loadAllRenters();
                });
    }

    /**
     * Load ALL renters (no activeProperty filter) and join with users collection
     * Grouped later by propertyName only (no room number).
     */
    private void loadAllRenters() {
        db.collection("renters")
                .get()
                .addOnSuccessListener(renterQs -> {
                    renterContacts.clear();

                    if (renterQs.isEmpty()) {
                        applySearch();
                        return;
                    }

                    final int total = renterQs.size();
                    final int[] done = {0};

                    for (QueryDocumentSnapshot renterDoc : renterQs) {
                        String renterUid    = renterDoc.getId();

                        // 🔹 Use ONLY propertyName (already "name - address" in your data)
                        String propertyName = renterDoc.getString("propertyName");
                        String finalPropertyName = propertyName;

                        db.collection("users").document(renterUid)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc != null && userDoc.exists()) {
                                        String name  = userDoc.getString("name");
                                        String phone = userDoc.getString("phone");
                                        String email = userDoc.getString("email");

                                        Contact c = new Contact(
                                                renterUid,
                                                name,
                                                phone,
                                                email,
                                                finalPropertyName, // ✅ no room added here
                                                false              // renter contact
                                        );
                                        renterContacts.add(c);
                                    }
                                })
                                .addOnCompleteListener(t -> {
                                    done[0]++;
                                    if (done[0] == total) {
                                        // All renters processed → update UI
                                        applySearch();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load renters", e);
                    applySearch();
                });
    }

    // ================== LIST BUILD + SEARCH ==================

    private void buildAndShowList(List<Contact> custom, List<Contact> renters) {
        List<ContactListItem> items = new ArrayList<>();

        // Custom contacts (from contacts collection)
        if (!custom.isEmpty()) {
            items.add(ContactListItem.header("Contacts"));
            for (Contact c : custom) {
                items.add(ContactListItem.contact(c));
            }
        }

        // Group renters by property name
        Map<String, List<Contact>> byProperty =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Contact c : renters) {
            String key = c.getPropertyName();
            if (key == null || key.trim().isEmpty()) key = "Renters";
            if (!byProperty.containsKey(key)) {
                byProperty.put(key, new ArrayList<>());
            }
            byProperty.get(key).add(c);
        }

        for (String property : byProperty.keySet()) {
            items.add(ContactListItem.header(property));
            for (Contact c : byProperty.get(property)) {
                items.add(ContactListItem.contact(c));
            }
        }

        adapter.submitList(items);
    }

    private void applySearch() {
        if (etSearch == null) {
            buildAndShowList(customContacts, renterContacts);
            return;
        }

        String query = etSearch.getText().toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (TextUtils.isEmpty(query)) {
            buildAndShowList(customContacts, renterContacts);
            return;
        }

        List<Contact> filteredCustom  = new ArrayList<>();
        List<Contact> filteredRenters = new ArrayList<>();

        for (Contact c : customContacts) {
            if (matchesQuery(c, query)) filteredCustom.add(c);
        }
        for (Contact c : renterContacts) {
            if (matchesQuery(c, query)) filteredRenters.add(c);
        }

        buildAndShowList(filteredCustom, filteredRenters);
    }

    private boolean matchesQuery(Contact c, String q) {
        if (c.getName() != null &&
                c.getName().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (c.getPhone() != null &&
                c.getPhone().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (c.getEmail() != null &&
                c.getEmail().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (c.getPropertyName() != null &&
                c.getPropertyName().toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }
}
