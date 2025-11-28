// app/src/main/java/com/example/lumiapp/FixRequestDetailActivity.java
package com.example.lumiapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class FixRequestDetailActivity extends AppCompatActivity {

    public static final String EXTRA_FIX_ID = "fixId";

    private ImageButton backBtn;
    private TextView tvTitle, tvCreatedBy, tvRoomChip, tvDate, tvDesc, tvProperty;
    private MaterialButton btnStatus, btnDelete;
    private ImageView ivComplaintImage;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String fixId;
    private FixRequest fix;
    private String myUid;
    private String myRole;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complaint_detail); // reuse complaint detail layout

        fixId = getIntent().getStringExtra(EXTRA_FIX_ID);
        if (fixId == null || fixId.isEmpty()) {
            finish();
            return;
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        myUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        bindViews();
        wireClicks();
        loadUserRoleThenFix();
    }

    private void bindViews() {
        backBtn = findViewById(R.id.back_btn);
        tvTitle = findViewById(R.id.tvTitle);
        tvCreatedBy = findViewById(R.id.tvCreatedBy);
        tvRoomChip = findViewById(R.id.tvRoomChip);
        tvDate = findViewById(R.id.tvDate);
        tvDesc = findViewById(R.id.tvDesc);
        tvProperty = findViewById(R.id.tvProperty);
        btnStatus = findViewById(R.id.btnStatus);
        btnDelete = findViewById(R.id.btnDelete);
        ivComplaintImage = findViewById(R.id.ivComplaintImage);
    }

    private void wireClicks() {
        backBtn.setOnClickListener(v -> finish());

        btnStatus.setOnClickListener(v -> {
            if (fix == null) return;
            if (!"manager".equalsIgnoreCase(myRole)) {
                Toast.makeText(this, "Only managers can update status", Toast.LENGTH_SHORT).show();
                return;
            }
            showStatusMenu(v);
        });

        btnDelete.setOnClickListener(v -> {
            if (fix == null) return;

            boolean canDelete = "manager".equalsIgnoreCase(myRole) ||
                    (myUid != null && myUid.equals(fix.createdById));
            if (!canDelete) {
                Toast.makeText(this, "You don't have permission to delete", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Delete fix request")
                    .setMessage("Are you sure you want to delete this request?")
                    .setPositiveButton("Delete", (d, w) -> deleteFix())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void showStatusMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor, Gravity.END);
        menu.getMenu().add("open");
        menu.getMenu().add("pending");
        menu.getMenu().add("closed");
        menu.setOnMenuItemClickListener(item -> {
            String sel = item.getTitle().toString();
            updateStatus(sel);
            return true;
        });
        menu.show();
    }

    private void loadUserRoleThenFix() {
        if (myUid == null) { finish(); return; }

        db.collection("users").document(myUid).get()
                .addOnSuccessListener(snap -> {
                    myRole = snap.getString("userType");
                    if (myRole == null) myRole = "renter";
                    loadFix();
                })
                .addOnFailureListener(e -> {
                    myRole = "renter";
                    loadFix();
                });
    }

    private void loadFix() {
        db.collection("fixRequests").document(fixId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { finish(); return; }
                    fix = doc.toObject(FixRequest.class);
                    if (fix == null) { finish(); return; }
                    fix.id = doc.getId();

                    if ((fix.shortId == null || fix.shortId.isEmpty())
                            && fix.id != null && fix.id.length() >= 6) {
                        fix.shortId = fix.id.substring(0, 6).toUpperCase(Locale.US);
                    }
                    bindDataToUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void bindDataToUI() {
        tvTitle.setText("Fix Request #" + (fix.shortId != null ? fix.shortId : "—"));
        tvCreatedBy.setText(fix.createdByName != null ? fix.createdByName : "—");
        tvRoomChip.setText(fix.roomNumber != null ? fix.roomNumber : "—");
        tvDate.setText(fix.createdDate != null ? fix.createdDate : "—");
        tvDesc.setText(fix.description != null ? fix.description : "—");
        tvProperty.setText(fix.propertyAddress != null ? fix.propertyAddress : "—");

        btnStatus.setText("Status  ▾   " + (fix.status != null ? fix.status : "open"));

        if (!"manager".equalsIgnoreCase(myRole)) {
            btnStatus.setEnabled(false);
            btnStatus.setAlpha(0.85f);
        }

        if (fix.imageUrl != null && !fix.imageUrl.isEmpty()) {
            ivComplaintImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(fix.imageUrl)
                    .into(ivComplaintImage);

            ivComplaintImage.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(fix.imageUrl));
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        } else {
            ivComplaintImage.setVisibility(View.GONE);
        }
    }

    private void updateStatus(String newStatus) {
        if (fix == null) return;

        db.collection("fixRequests").document(fix.id)
                .update("status", newStatus)
                .addOnSuccessListener(unused -> {
                    fix.status = newStatus;
                    btnStatus.setText("Status  ▾   " + newStatus);
                    Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show()
                );
    }

    private void deleteFix() {
        db.collection("fixRequests").document(fix.id)
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Fix request deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                );
    }
}
