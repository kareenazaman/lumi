// app/src/main/java/com/example/lumiapp/CreateFixRequestActivity.java
package com.example.lumiapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateFixRequestActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 2001;

    // Header + fields
    private ImageView headerImage;
    private TextView tvCreatedBy, tvRoomChip, tvPropertyAddress, tvDate, tvTitle;
    private TextInputEditText etDesc;
    private TextInputLayout tilProperty, tilDesc;
    private MaterialAutoCompleteTextView actProperty;
    private MaterialButton btnCreateFix;
    private ImageButton backBtn;

    // Upload card views
    private FrameLayout uploadFixImage;
    private ImageView imgFixPreview;
    private TextView tvFixPlus;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private StorageReference storageRef;

    // user/session state
    private String role;           // "manager" | "renter"
    private String userId;
    private String userName;

    // renter/manager fields
    private String roomNumber;
    private String propertyId;       // set from dropdown (manager) or renter doc
    private String propertyAddress;  // name/address shown

    // image state
    private Uri imageUri = null;

    private final ArrayList<String> propertyNames = new ArrayList<>();
    private final ArrayList<String> propertyIds   = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_fix_request);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userId = auth.getCurrentUser().getUid();

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

        etDesc      = findViewById(R.id.etDesc);
        tilDesc     = findViewById(R.id.tilDesc);
        tilProperty = findViewById(R.id.tilProperty);
        actProperty = findViewById(R.id.actProperty);

        btnCreateFix = findViewById(R.id.btnCreateFix);
        backBtn      = findViewById(R.id.back_btn);

        uploadFixImage = findViewById(R.id.uploadFixImage);
        imgFixPreview  = findViewById(R.id.imgFixPreview);
        tvFixPlus      = findViewById(R.id.tvFixPlus);

        if (tvTitle != null) {
            tvTitle.setText("Create Fix Request");
        }

        // default header
        if (headerImage != null) {
            headerImage.setImageResource(R.drawable.img_dashboard_bg);
        }
    }

    private void fillStaticDate() {
        if (tvDate != null) {
            String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    .format(System.currentTimeMillis());
            tvDate.setText(today);
        }
    }

    private void loadUserProfileThenSetupUI() {
        // First read basic user doc (role + display name)
        db.collection("users").document(userId).get()
                .addOnSuccessListener(userSnap -> {
                    role = userSnap.getString("userType");  // "manager" or "renter"
                    if (role == null) role = "renter";

                    // Use NAME, not email
                    userName = userSnap.getString("name");
                    if (TextUtils.isEmpty(userName) && auth.getCurrentUser() != null) {
                        userName = auth.getCurrentUser().getEmail(); // fallback only
                    }
                    if (tvCreatedBy != null) {
                        tvCreatedBy.setText(userName != null ? userName : "—");
                    }

                    if ("manager".equalsIgnoreCase(role)) {
                        setupManagerUI(userSnap);
                    } else {
                        loadRenterProfileAndSetupUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    /**
     * Manager UI: no room number, choose property from dropdown
     */
    private void setupManagerUI(DocumentSnapshot userSnap) {
        // Property manager has no room → label as such
        if (tvRoomChip != null) {
            tvRoomChip.setText("Property Manager");
        }

        // hide address text, show dropdown
        if (tvPropertyAddress != null) {
            tvPropertyAddress.setVisibility(TextView.GONE);
        }
        if (tilProperty != null) {
            tilProperty.setVisibility(TextInputLayout.VISIBLE);
        }

        @SuppressWarnings("unchecked")
        java.util.List<String> managerOf = (java.util.List<String>) userSnap.get("managerOf");
        loadManagerProperties(userId, managerOf);
    }

    /**
     * Renter profile is stored in renters/{uid}
     * We pull roomNumber, propertyId, propertyName from there and load header image.
     */
    private void loadRenterProfileAndSetupUI() {
        db.collection("renters").document(userId).get()
                .addOnSuccessListener(rSnap -> {
                    if (rSnap != null && rSnap.exists()) {
                        roomNumber = rSnap.getString("roomNumber");
                        propertyId = rSnap.getString("propertyId");

                        // you saved "propertyName" like "name - address"
                        propertyAddress = rSnap.getString("propertyName");
                        if (TextUtils.isEmpty(propertyAddress)) {
                            propertyAddress = rSnap.getString("propertyAddress"); // fallback
                        }
                    }

                    if (tvRoomChip != null) {
                        tvRoomChip.setText(roomNumber != null ? roomNumber : "—");
                    }
                    if (tvPropertyAddress != null) {
                        tvPropertyAddress.setText(
                                propertyAddress != null ? propertyAddress : "—"
                        );
                        tvPropertyAddress.setVisibility(TextView.VISIBLE);
                    }

                    // renter should NOT pick property manually
                    if (tilProperty != null) {
                        tilProperty.setVisibility(TextInputLayout.GONE);
                    }

                    // 🔹 Load property image into header
                    applyHeaderImageForPropertyId(propertyId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load renter profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    if (tvRoomChip != null) tvRoomChip.setText("—");
                    if (tvPropertyAddress != null) {
                        tvPropertyAddress.setText("—");
                        tvPropertyAddress.setVisibility(TextView.VISIBLE);
                    }
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
            if (managerOf.size() <= 10) {
                db.collection("properties")
                        .whereIn(FieldPath.documentId(), managerOf)
                        .get()
                        .addOnSuccessListener(qs -> {
                            for (DocumentSnapshot d : qs) {
                                addPropertyToLists(d);
                            }
                            bindPropertyAdapter();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to load properties: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            bindPropertyAdapter();
                        });
            } else {
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
            db.collection("properties")
                    .whereEqualTo("ownerUid", ownerUid)
                    .get()
                    .addOnSuccessListener(qs -> {
                        for (DocumentSnapshot d : qs) {
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

    private void addPropertyToLists(DocumentSnapshot d) {
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
        if (actProperty == null) return;

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                propertyNames
        );
        actProperty.setAdapter(adapter);

        actProperty.setOnClickListener(v -> actProperty.showDropDown());

        if (!propertyIds.isEmpty()) {
            // preselect first property
            actProperty.setText(propertyNames.get(0), false);
            propertyId      = propertyIds.get(0);
            propertyAddress = propertyNames.get(0);

            // 🔹 show its image
            applyHeaderImageForPropertyId(propertyId);
        } else {
            propertyId = null;
            propertyAddress = null;
            // default header
            if (headerImage != null) {
                headerImage.setImageResource(R.drawable.img_dashboard_bg);
            }
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
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        if (uploadFixImage != null) {
            uploadFixImage.setOnClickListener(v -> openImagePicker());
        }

        if (btnCreateFix != null) {
            btnCreateFix.setOnClickListener(v -> {
                String desc = etDesc != null && etDesc.getText() != null
                        ? etDesc.getText().toString().trim()
                        : "";

                if (TextUtils.isEmpty(desc)) {
                    if (tilDesc != null) {
                        tilDesc.setError("Description required");
                    }
                    if (etDesc != null) {
                        etDesc.requestFocus();
                    }
                    return;
                } else if (tilDesc != null) {
                    tilDesc.setError(null);
                }

                if ("manager".equalsIgnoreCase(role)
                        && (propertyId == null || propertyId.isEmpty())
                        && tilProperty != null) {
                    tilProperty.setError("Select a property");
                    return;
                } else if (tilProperty != null) {
                    tilProperty.setError(null);
                }

                saveFixRequest(desc);
            });
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        );
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            if (imageUri != null && imgFixPreview != null && tvFixPlus != null) {
                imgFixPreview.setVisibility(ImageView.VISIBLE);
                tvFixPlus.setVisibility(TextView.GONE);
                imgFixPreview.setImageURI(imageUri);
            }
        }
    }

    private void saveFixRequest(String description) {
        if (imageUri != null) {
            uploadImageAndSaveFix(description);
        } else {
            saveFixToFirestore(description, null);
        }
    }

    private void uploadImageAndSaveFix(String description) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Uploading image...");
        progress.setCancelable(false);
        progress.show();

        String fileName = "fixRequestImages/" + userId + "_" + System.currentTimeMillis() + ".jpg";
        StorageReference imgRef = storageRef.child(fileName);

        if (btnCreateFix != null) {
            btnCreateFix.setEnabled(false);
        }

        imgRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->
                        imgRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            progress.dismiss();
                            saveFixToFirestore(description, uri.toString());
                        }).addOnFailureListener(e -> {
                            progress.dismiss();
                            if (btnCreateFix != null) btnCreateFix.setEnabled(true);
                            Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_LONG).show();
                        })
                )
                .addOnFailureListener(e -> {
                    progress.dismiss();
                    if (btnCreateFix != null) btnCreateFix.setEnabled(true);
                    Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Save into collection "fixRequests"
     */
    private void saveFixToFirestore(String description, @Nullable String imageUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("createdAt", Timestamp.now());
        data.put("createdDate", tvDate != null ? tvDate.getText().toString() : null);
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
            // renter values from renters/{uid}
            data.put("roomNumber", roomNumber);
            data.put("propertyId", propertyId);
            data.put("propertyAddress", propertyAddress);
        }

        db.collection("fixRequests").add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Fix request submitted", Toast.LENGTH_LONG).show();
                    if (btnCreateFix != null) btnCreateFix.setEnabled(true);
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (btnCreateFix != null) btnCreateFix.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
