package com.example.lumiapp;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateComplaint extends AppCompatActivity {

    private TextView tvCreatedBy, tvRoomChip, tvPropertyAddress, tvDate, tvTitle;
    private TextInputEditText etDesc;
    private TextInputLayout tilProperty, tilDesc;
    private MaterialAutoCompleteTextView actProperty;
    private MaterialButton btnCreate;
    private ImageButton backBtn;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // user/session state
    private String role;           // "manager" | "renter"
    private String userId;
    private String userName;

    // renter fields (optional for manager)
    private String roomNumber;
    private String propertyId;       // set from dropdown (manager) or user doc (renter)
    private String propertyAddress;  // name/address shown

    private final ArrayList<String> propertyNames = new ArrayList<>();
    private final ArrayList<String> propertyIds   = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_complaint); // your existing layout

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        bindViews();
        fillStaticDate();
        loadUserProfileThenSetupUI();
        setListeners();
    }

    private void bindViews() {
        tvTitle = findViewById(R.id.tvTitle);              // if present in your layout
        tvCreatedBy = findViewById(R.id.tvCreatedBy);
        tvRoomChip = findViewById(R.id.tvRoomChip);
        tvPropertyAddress = findViewById(R.id.tvPropertyAddress);
        tvDate = findViewById(R.id.tvDate);
        etDesc = findViewById(R.id.etDesc);
        tilDesc = findViewById(R.id.tilDesc);
        tilProperty = findViewById(R.id.tilProperty);
        actProperty = findViewById(R.id.actProperty);
        btnCreate = findViewById(R.id.btnCreateComplaint);
        backBtn = findViewById(R.id.back_btn);

        // Optional title for create screen (no pre-generated ID)
        if (tvTitle != null) tvTitle.setText("Create Complaint");
    }

    private void fillStaticDate() {
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                .format(System.currentTimeMillis());
        tvDate.setText(today);
    }

    @SuppressWarnings("unchecked")
    private void loadUserProfileThenSetupUI() {
        if (auth.getCurrentUser() == null) { finish(); return; }
        userId = auth.getCurrentUser().getUid();

        db.collection("users").document(userId).get().addOnSuccessListener(snap -> {
            // Your schema from screenshots
            role = snap.getString("userType");  // "manager" or "renter"
            if (role == null) role = "renter";

            userName = snap.getString("name");
            tvCreatedBy.setText(userName != null ? userName : "—");

            if ("manager".equalsIgnoreCase(role)) {
                tvRoomChip.setText("Property Manager");
                tvPropertyAddress.setVisibility(View.GONE);
                tilProperty.setVisibility(View.VISIBLE);

                java.util.List<String> managerOf = (java.util.List<String>) snap.get("managerOf");
                loadManagerPropertiesFromIds(managerOf);

            } else {
                // renter fields (store these in user doc or derive elsewhere)
                roomNumber = snap.getString("roomNumber");
                propertyId = snap.getString("propertyId");
                propertyAddress = snap.getString("propertyAddress");

                tvRoomChip.setText(roomNumber != null ? roomNumber : "—");
                tvPropertyAddress.setText(propertyAddress != null ? propertyAddress : "—");
                tvPropertyAddress.setVisibility(View.VISIBLE);
                tilProperty.setVisibility(View.GONE);
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadManagerPropertiesFromIds(@Nullable java.util.List<String> ids) {
        propertyNames.clear();
        propertyIds.clear();

        if (ids == null || ids.isEmpty()) {
            // Show empty adapter
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, new ArrayList<>());
            actProperty.setAdapter(adapter);
            return;
        }

        if (ids.size() <= 10) {
            db.collection("properties")
                    .whereIn(FieldPath.documentId(), ids)
                    .get()
                    .addOnSuccessListener(qs -> {
                        for (com.google.firebase.firestore.DocumentSnapshot d : qs) {
                            addPropertyToLists(d);
                        }
                        bindPropertyAdapter();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed to load properties", Toast.LENGTH_SHORT).show()
                    );
        } else {
            final int total = ids.size();
            final int[] done = {0};
            for (String pid : ids) {
                db.collection("properties").document(pid).get()
                        .addOnSuccessListener(this::addPropertyToLists)
                        .addOnCompleteListener(task -> {
                            if (++done[0] == total) bindPropertyAdapter();
                        });
            }
        }
    }

    private void addPropertyToLists(com.google.firebase.firestore.DocumentSnapshot d) {
        if (d == null || !d.exists()) return;
        String pid = d.getId();
        String name = d.getString("name");
        String addr = d.getString("address");

        propertyIds.add(pid);
        propertyNames.add(name != null && !name.isEmpty() ? name :
                (addr != null && !addr.isEmpty() ? addr : pid));
    }

    private void bindPropertyAdapter() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, propertyNames);
        actProperty.setAdapter(adapter);

        // Preselect first property for managers (critical so propertyId is not null)
        if (!propertyIds.isEmpty()) {
            actProperty.setText(propertyNames.get(0), false);
            propertyId = propertyIds.get(0);
            propertyAddress = propertyNames.get(0);
        }

        actProperty.setOnItemClickListener((parent, view, position, id) -> {
            propertyId = propertyIds.get(position);
            propertyAddress = propertyNames.get(position);
        });
    }

    private void setListeners() {
        backBtn.setOnClickListener(v -> finish());

        btnCreate.setOnClickListener(v -> {
            String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";
            if (desc.isEmpty()) {
                tilDesc.setError("Description required");
                return;
            } else {
                tilDesc.setError(null);
            }

            if ("manager".equalsIgnoreCase(role) && (propertyId == null || propertyId.isEmpty())) {
                tilProperty.setError("Select a property");
                return;
            } else {
                tilProperty.setError(null);
            }

            saveComplaint(desc);
        });
    }

    private void saveComplaint(String description) {
        Map<String, Object> data = new HashMap<>();
        data.put("createdAt", Timestamp.now());
        data.put("createdDate", tvDate.getText().toString());
        data.put("createdById", userId);
        data.put("createdByName", userName);
        data.put("createdByRole", role != null ? role : "renter");
        data.put("status", "open");
        data.put("description", description);

        if ("manager".equalsIgnoreCase(role)) {
            data.put("roomNumber", "Property Manager");
            data.put("propertyId", propertyId);
            data.put("propertyAddress", propertyAddress);
        } else {
            data.put("roomNumber", roomNumber);
            data.put("propertyId", propertyId);
            data.put("propertyAddress", propertyAddress);
        }

        db.collection("complaints").add(data)
                .addOnSuccessListener(ref -> {
                    String id = ref.getId();
                    String shortId = id.length() >= 6 ? id.substring(0, 6).toUpperCase(Locale.US) : id;
                    ref.update("id", id, "shortId", shortId);

                    Toast.makeText(this, "Complaint created #" + shortId, Toast.LENGTH_LONG).show();

                    // Optionally navigate to detail:
                    // Intent i = new Intent(this, ComplaintDetailActivity.class);
                    // i.putExtra(ComplaintDetailActivity.EXTRA_COMPLAINT_ID, id);
                    // startActivity(i);

                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}
