package com.example.lumiapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateComplaint extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1001;

    // Header + fields
    private ImageView headerImage;
    private TextView tvCreatedBy, tvRoomChip, tvPropertyAddress, tvDate, tvTitle;
    private TextInputEditText etDesc;
    private TextInputLayout tilProperty, tilDesc;
    private MaterialAutoCompleteTextView actProperty;
    private MaterialButton btnCreate;
    private ImageButton backBtn;

    // Upload card views
    private FrameLayout uploadComplaintImage;
    private ImageView imgComplaintPreview;
    private TextView tvComplaintPlus;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private StorageReference storageRef;

    // user/session state
    private String role;           // "manager" | "renter"
    private String userId;
    private String userName;

    // renter / manager fields
    private String roomNumber;
    private String propertyId;       // set from dropdown (manager) or renters doc (renter)
    private String propertyAddress;  // nice display string for property

    // image state
    private Uri imageUri = null;

    private final ArrayList<String> propertyNames = new ArrayList<>();
    private final ArrayList<String> propertyIds   = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_complaint);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();

        bindViews();
        fillStaticDate();
        loadUserProfileThenSetupUI();
        setListeners();
    }

    private void bindViews() {
        headerImage       = findViewById(R.id.headerImage);
        tvTitle           = findViewById(R.id.tvTitle);
        tvCreatedBy       = findViewById(R.id.tvCreatedBy);
        tvRoomChip        = findViewById(R.id.tvRoomChip);
        tvPropertyAddress = findViewById(R.id.tvPropertyAddress);
        tvDate            = findViewById(R.id.tvDate);
        etDesc            = findViewById(R.id.etDesc);
        tilDesc           = findViewById(R.id.tilDesc);
        tilProperty       = findViewById(R.id.tilProperty);
        actProperty       = findViewById(R.id.actProperty);
        btnCreate         = findViewById(R.id.btnCreateComplaint);
        backBtn           = findViewById(R.id.back_btn);

        // Card-style uploader
        uploadComplaintImage = findViewById(R.id.uploadComplaintImage);
        imgComplaintPreview  = findViewById(R.id.imgComplaintPreview);
        tvComplaintPlus      = findViewById(R.id.tvComplaintPlus);

        if (tvTitle != null) tvTitle.setText("Create Complaint");

        // default header image
        if (headerImage != null) {
            headerImage.setImageResource(R.drawable.img_dashboard_bg);
        }
    }

    private void fillStaticDate() {
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                .format(System.currentTimeMillis());
        tvDate.setText(today);
    }

    @SuppressWarnings("unchecked")
    private void loadUserProfileThenSetupUI() {
        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }
        userId = auth.getCurrentUser().getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(snap -> {
                    role = snap.getString("userType");  // "manager" or "renter"
                    if (role == null) role = "renter";

                    // createdBy = NAME, not email
                    userName = snap.getString("name");
                    tvCreatedBy.setText(userName != null ? userName : "—");

                    if ("manager".equalsIgnoreCase(role)) {
                        // 🔹 MANAGER UI
                        tvRoomChip.setText("Property Manager");
                        tvPropertyAddress.setVisibility(View.GONE);
                        tilProperty.setVisibility(View.VISIBLE);

                        // Use managerOf list if present, fallback to ownerUid
                        java.util.List<String> managerOf =
                                (java.util.List<String>) snap.get("managerOf");
                        loadManagerProperties(userId, managerOf);

                    } else {
                        // 🔹 RENTER UI → read details from /renters/{uid}
                        db.collection("renters")
                                .document(userId)
                                .get()
                                .addOnSuccessListener(rSnap -> {
                                    if (rSnap != null && rSnap.exists()) {
                                        roomNumber = rSnap.getString("roomNumber");
                                        propertyId = rSnap.getString("propertyId");

                                        String propertyName = rSnap.getString("propertyName");
                                        String prevAddress  = rSnap.getString("previousAddress");

                                        // Build a nice display string
                                        if (!TextUtils.isEmpty(propertyName) && !TextUtils.isEmpty(prevAddress)) {
                                            propertyAddress = propertyName + " - " + prevAddress;
                                        } else if (!TextUtils.isEmpty(propertyName)) {
                                            propertyAddress = propertyName;
                                        } else {
                                            propertyAddress = prevAddress; // may be null
                                        }

                                        tvRoomChip.setText(
                                                !TextUtils.isEmpty(roomNumber) ? roomNumber : "—"
                                        );
                                        tvPropertyAddress.setText(
                                                !TextUtils.isEmpty(propertyAddress) ? propertyAddress : "—"
                                        );

                                        // 🔹 renter header image → their property
                                        applyHeaderImageForPropertyId(propertyId);
                                    } else {
                                        // No renter doc yet
                                        tvRoomChip.setText("—");
                                        tvPropertyAddress.setText("—");
                                        roomNumber = null;
                                        propertyId = null;
                                        propertyAddress = null;
                                    }

                                    tvPropertyAddress.setVisibility(View.VISIBLE);
                                    tilProperty.setVisibility(View.GONE);
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this,
                                            "Failed to load renter details: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();

                                    tvRoomChip.setText("—");
                                    tvPropertyAddress.setText("—");
                                    tvPropertyAddress.setVisibility(View.VISIBLE);
                                    tilProperty.setVisibility(View.GONE);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    /**
     * For manager: load all properties they manage.
     * 1) If managerOf has IDs, use those.
     * 2) Otherwise, fallback to ownerUid == userId.
     */
    private void loadManagerProperties(String ownerUid, @Nullable java.util.List<String> managerOf) {
        propertyNames.clear();
        propertyIds.clear();

        if (managerOf != null && !managerOf.isEmpty()) {
            // Use managerOf array (IDs) → whereIn on documentId
            if (managerOf.size() <= 10) {
                db.collection("properties")
                        .whereIn(FieldPath.documentId(), managerOf)
                        .get()
                        .addOnSuccessListener(qs -> {
                            for (com.google.firebase.firestore.DocumentSnapshot d : qs) {
                                addPropertyToLists(d);
                            }
                            bindPropertyAdapter();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to load properties: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            bindPropertyAdapter();
                        });
            } else {
                // If >10, fetch one-by-one
                final int total = managerOf.size();
                final int[] done = {0};

                for (String pid : managerOf) {
                    db.collection("properties").document(pid).get()
                            .addOnSuccessListener(this::addPropertyToLists)
                            .addOnCompleteListener(task -> {
                                if (++done[0] == total) {
                                    bindPropertyAdapter();
                                }
                            });
                }
            }
        } else {
            // Fallback: query by ownerUid
            db.collection("properties")
                    .whereEqualTo("ownerUid", ownerUid)
                    .get()
                    .addOnSuccessListener(qs -> {
                        for (com.google.firebase.firestore.DocumentSnapshot d : qs) {
                            addPropertyToLists(d);
                        }
                        bindPropertyAdapter();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load properties: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        bindPropertyAdapter();
                    });
        }
    }

    private void addPropertyToLists(com.google.firebase.firestore.DocumentSnapshot d) {
        if (d == null || !d.exists()) return;
        String pid  = d.getId();
        String name = d.getString("name");
        String addr = d.getString("address");

        propertyIds.add(pid);
        propertyNames.add(
                !TextUtils.isEmpty(name)
                        ? name
                        : (!TextUtils.isEmpty(addr) ? addr : pid)
        );
    }

    private void bindPropertyAdapter() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, propertyNames);
        actProperty.setAdapter(adapter);

        // Force dropdown to open when clicked
        actProperty.setOnClickListener(v -> actProperty.showDropDown());

        // Preselect first property (so propertyId is never null if there is at least one)
        if (!propertyIds.isEmpty()) {
            actProperty.setText(propertyNames.get(0), false);
            propertyId      = propertyIds.get(0);
            propertyAddress = propertyNames.get(0);

            // 🔹 manager header image → first property
            applyHeaderImageForPropertyId(propertyId);
        } else {
            propertyId = null;
            propertyAddress = null;
            applyHeaderImageForPropertyId(null);
        }

        actProperty.setOnItemClickListener((parent, view, position, id) -> {
            propertyId      = propertyIds.get(position);
            propertyAddress = propertyNames.get(position);

            // 🔹 update header when manager changes property
            applyHeaderImageForPropertyId(propertyId);
        });
    }

    /**
     * Load property imageUrl from properties/{propertyId} into headerImage.
     */
    private void applyHeaderImageForPropertyId(@Nullable String propertyId) {
        if (headerImage == null || TextUtils.isEmpty(propertyId)) {
            if (headerImage != null) {
                headerImage.setImageResource(R.drawable.img_dashboard_bg);
            }
            return;
        }

        db.collection("properties").document(propertyId).get()
                .addOnSuccessListener(doc -> {
                    String imageUrl = doc.getString("imageUrl");
                    if (!TextUtils.isEmpty(imageUrl)) {
                        Glide.with(this)
                                .load(imageUrl)
                                .centerCrop()
                                .placeholder(R.drawable.img_dashboard_bg)
                                .into(headerImage);
                    } else {
                        headerImage.setImageResource(R.drawable.img_dashboard_bg);
                    }
                })
                .addOnFailureListener(e -> {
                    if (headerImage != null) {
                        headerImage.setImageResource(R.drawable.img_dashboard_bg);
                    }
                });
    }

    private void setListeners() {
        backBtn.setOnClickListener(v -> finish());

        // Click dashed card area to pick image
        if (uploadComplaintImage != null) {
            uploadComplaintImage.setOnClickListener(v -> openImagePicker());
        }

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

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            if (imageUri != null) {
                imgComplaintPreview.setVisibility(View.VISIBLE);
                imgComplaintPreview.setImageURI(imageUri);
                tvComplaintPlus.setVisibility(View.GONE);
            }
        }
    }

    private void saveComplaint(String description) {
        if (imageUri != null) {
            uploadImageAndSaveComplaint(description);
        } else {
            saveComplaintToFirestore(description, null);
        }
    }

    private void uploadImageAndSaveComplaint(String description) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Uploading image...");
        progress.setCancelable(false);
        progress.show();

        String fileName = "complaintImages/" + userId + "_" + System.currentTimeMillis() + ".jpg";
        StorageReference imgRef = storageRef.child(fileName);

        btnCreate.setEnabled(false);

        imgRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->
                        imgRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            progress.dismiss();
                            saveComplaintToFirestore(description, uri.toString());
                        }).addOnFailureListener(e -> {
                            progress.dismiss();
                            btnCreate.setEnabled(true);
                            Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_LONG).show();
                        })
                )
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    btnCreate.setEnabled(true);
                    Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveComplaintToFirestore(String description, @Nullable String imageUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("createdAt", Timestamp.now());
        data.put("createdDate", tvDate.getText().toString());
        data.put("createdById", userId);
        data.put("createdByName", userName);
        data.put("createdByRole", role != null ? role : "renter");
        data.put("status", "open");
        data.put("description", description);

        if (imageUrl != null) {
            data.put("imageUrl", imageUrl);
        }

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
                    btnCreate.setEnabled(true);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnCreate.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
