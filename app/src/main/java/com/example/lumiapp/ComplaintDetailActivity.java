package com.example.lumiapp;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class ComplaintDetailActivity extends AppCompatActivity {

    public static final String EXTRA_COMPLAINT_ID = "complaintId";

    private ImageButton backBtn;
    private TextView tvTitle, tvCreatedBy, tvRoomChip, tvDate, tvDesc, tvProperty;
    private MaterialButton btnStatus, btnDelete;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String complaintId;
    private Complaint complaint;   // model from previous step
    private String myUid;
    private String myRole;         // renter/manager

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complaint_detail);

        complaintId = getIntent().getStringExtra(EXTRA_COMPLAINT_ID);
        if (complaintId == null || complaintId.isEmpty()) {
            finish();
            return;
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        myUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        bindViews();
        wireClicks();
        loadUserRoleThenComplaint();
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
    }

    private void wireClicks() {
        backBtn.setOnClickListener(v -> finish());

        btnStatus.setOnClickListener(v -> {
            if (complaint == null) return;
            if (!"manager".equalsIgnoreCase(myRole)) {
                Toast.makeText(this, "Only managers can update status", Toast.LENGTH_SHORT).show();
                return;
            }
            showStatusMenu(v);
        });

        btnDelete.setOnClickListener(v -> {
            if (complaint == null) return;

            boolean canDelete = "manager".equalsIgnoreCase(myRole) ||
                    (myUid != null && myUid.equals(complaint.createdById));
            if (!canDelete) {
                Toast.makeText(this, "You don't have permission to delete", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Delete complaint")
                    .setMessage("Are you sure you want to delete this complaint?")
                    .setPositiveButton("Delete", (d, w) -> deleteComplaint())
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

    private void loadUserRoleThenComplaint() {
        if (myUid == null) { finish(); return; }

        db.collection("users").document(myUid).get()
                .addOnSuccessListener(snap -> {
                    myRole = String.valueOf(snap.get("role")); // renter/manager
                    loadComplaint();
                })
                .addOnFailureListener(e -> {
                    myRole = "renter";
                    loadComplaint();
                });
    }

    private void loadComplaint() {
        DocumentReference ref = db.collection("complaints").document(complaintId);
        ref.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) { finish(); return; }
            complaint = doc.toObject(Complaint.class);
            if (complaint == null) { finish(); return; }
            complaint.id = doc.getId();

            // derive short if missing
            if ((complaint.shortId == null || complaint.shortId.isEmpty())
                    && complaint.id != null && complaint.id.length() >= 6) {
                complaint.shortId = complaint.id.substring(0, 6).toUpperCase(Locale.US);
            }
            bindDataToUI();

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void bindDataToUI() {
        tvTitle.setText("Complain #" + (complaint.shortId != null ? complaint.shortId : "—"));
        tvCreatedBy.setText(complaint.createdByName != null ? complaint.createdByName : "—");
        tvRoomChip.setText(complaint.roomNumber != null ? complaint.roomNumber : "—");
        tvDate.setText(complaint.createdDate != null ? complaint.createdDate : "—");
        tvDesc.setText(complaint.description != null ? complaint.description : "—");
        tvProperty.setText(complaint.propertyAddress != null ? complaint.propertyAddress : "—");

        // reflect status style on the button text
        btnStatus.setText("Status  ▾   " + (complaint.status != null ? complaint.status : "open"));

        // If renter, lock status button look (optional); managers can tap to change
        if (!"manager".equalsIgnoreCase(myRole)) {
            btnStatus.setEnabled(false);
            btnStatus.setAlpha(0.85f);
        }
    }

    private void updateStatus(String newStatus) {
        if (complaint == null) return;

        db.collection("complaints").document(complaint.id)
                .update("status", newStatus)
                .addOnSuccessListener(unused -> {
                    complaint.status = newStatus;
                    btnStatus.setText("Status  ▾   " + newStatus);
                    Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show()
                );
    }

    private void deleteComplaint() {
        db.collection("complaints").document(complaint.id)
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Complaint deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                );
    }
}
