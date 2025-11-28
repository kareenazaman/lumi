package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ComplaintList extends AppCompatActivity {

    private static final String TAG = "Complaints";

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvComplaint;
    private ComplaintAdapter adapter;

    private MaterialButton btnCreate;
    private ImageButton backBtn;
    private ImageView headerImage;

    private String userId;
    private String role; // "renter" or "manager"

    private ListenerRegistration renterReg;
    private final List<ListenerRegistration> managerRegs = new ArrayList<>();
    private ListenerRegistration userHeaderReg; // 🔹 for active property header image

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complains_list);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // 🔹 Header title
        TextView recentHeader = findViewById(R.id.recentHeader);
        if (recentHeader != null) {
            recentHeader.setText(getString(R.string.complains));
        }

        headerImage = findViewById(R.id.headerImage);

        rvComplaint = findViewById(R.id.rvComplaint);
        rvComplaint.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ComplaintAdapter(c -> {
            Intent i = new Intent(this, ComplaintDetailActivity.class);
            i.putExtra(ComplaintDetailActivity.EXTRA_COMPLAINT_ID, c.id);
            startActivity(i);
        });
        rvComplaint.setAdapter(adapter);

        btnCreate = findViewById(R.id.create_complain);
        backBtn   = findViewById(R.id.back_btn);

        btnCreate.setOnClickListener(v ->
                startActivity(new Intent(this, CreateComplaint.class))
        );
        backBtn.setOnClickListener(v -> finish());

        if (auth.getCurrentUser() == null) { finish(); return; }
        userId = auth.getCurrentUser().getUid();

        listenActivePropertyHeader();   // 🔹 set header image from selected property
        loadUserRoleThenListen();       // 🔹 set up complaint query based on role
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserRoleThenListen();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (renterReg != null) { renterReg.remove(); renterReg = null; }
        for (ListenerRegistration r : managerRegs) r.remove();
        managerRegs.clear();

        if (userHeaderReg != null) {
            userHeaderReg.remove();
            userHeaderReg = null;
        }
    }

    /**
     * Set header image to the currently selected property's image
     * (activePropertyImageUrl in users/{uid}).
     */
    private void listenActivePropertyHeader() {
        if (userId == null) return;

        userHeaderReg = db.collection("users").document(userId)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null || !snap.exists()) return;

                    String imageUrl = snap.getString("activePropertyImageUrl");

                    if (headerImage == null) return;

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(this)
                                .load(imageUrl)
                                .centerCrop()
                                .placeholder(R.drawable.img_dashboard_bg)
                                .into(headerImage);
                    } else {
                        headerImage.setImageResource(R.drawable.img_dashboard_bg);
                    }
                });
    }

    private void loadUserRoleThenListen() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(snap -> {
                    role = snap.getString("userType");
                    if (role == null) role = "renter";

                    // Clear existing listeners
                    if (renterReg != null) { renterReg.remove(); renterReg = null; }
                    for (ListenerRegistration r : managerRegs) r.remove();
                    managerRegs.clear();

                    if ("manager".equalsIgnoreCase(role)) {
                        // 🔹 Manager: only see complaints for the ACTIVE property
                        String activePropertyId = snap.getString("activePropertyId");

                        if (activePropertyId != null && !activePropertyId.isEmpty()) {
                            listenManagerComplaintsForProperty(activePropertyId);
                        } else {
                            // fallback: old behavior based on managerOf list
                            @SuppressWarnings("unchecked")
                            List<String> managerOf = (List<String>) snap.get("managerOf");
                            loadManagerComplaintsOnce(managerOf);
                        }
                    } else {
                        // 🔹 Renter: only see their own complaints
                        listenRenterComplaints();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "loadUserRole failed", e));
    }

    /**
     * Manager: live listen to complaints for ONE propertyId (active property).
     */
    private void listenManagerComplaintsForProperty(String propertyId) {
        // We'll repurpose renterReg for this single listener
        renterReg = db.collection("complaints")
                .whereEqualTo("propertyId", propertyId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((qs, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Manager listen failed, fallback", e);
                        // As a fallback, you *could* call loadManagerComplaintsOnce
                        // but here we'll just show nothing on error.
                        return;
                    }
                    if (qs == null) {
                        adapter.setItems(new ArrayList<>());
                        return;
                    }

                    ArrayList<Complaint> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : qs) {
                        Complaint c = d.toObject(Complaint.class);
                        if (c == null) continue;
                        c.id = d.getId();
                        if (c.shortId == null && c.id.length() >= 6) {
                            c.shortId = c.id.substring(0, 6).toUpperCase(Locale.US);
                        }
                        list.add(c);
                    }
                    adapter.setItems(list);
                });
    }

    /**
     * OLD behavior (fallback only): manager sees complaints for all properties in managerOf.
     */
    private void loadManagerComplaintsOnce(@Nullable List<String> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            adapter.setItems(new ArrayList<>());
            Log.d(TAG, "Manager has no properties");
            return;
        }

        final ArrayList<Complaint> all = new ArrayList<>();
        final int step = 10;
        final int totalBatches = (int) Math.ceil(propertyIds.size() / (double) step);
        final int[] done = {0};

        for (int i = 0; i < propertyIds.size(); i += step) {
            int end = Math.min(i + step, propertyIds.size());
            List<String> sub = propertyIds.subList(i, end);

            db.collection("complaints")
                    .whereIn("propertyId", sub)
                    .get()
                    .addOnSuccessListener(qs -> {
                        for (QueryDocumentSnapshot d : qs) {
                            Complaint c = d.toObject(Complaint.class);
                            if (c == null) continue;
                            c.id = d.getId();
                            if (c.shortId == null && c.id.length() >= 6) {
                                c.shortId = c.id.substring(0, 6).toUpperCase(Locale.US);
                            }
                            all.add(c);
                        }
                        if (++done[0] == totalBatches) {
                            all.sort((a, b) -> {
                                long la = a.createdAt != null ? a.createdAt.toDate().getTime() : 0L;
                                long lb = b.createdAt != null ? b.createdAt.toDate().getTime() : 0L;
                                return Long.compare(lb, la);
                            });
                            adapter.setItems(all);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Manager batch load failed", e);
                        if (++done[0] == totalBatches) adapter.setItems(all);
                    });
        }
    }

    private void listenRenterComplaints() {
        renterReg = db.collection("complaints")
                .whereEqualTo("createdById", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((qs, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Renter listen failed, fallback", e);
                        fallbackRenterLoad();
                        return;
                    }
                    if (qs == null) { adapter.setItems(new ArrayList<>()); return; }
                    ArrayList<Complaint> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : qs) {
                        Complaint c = d.toObject(Complaint.class);
                        if (c == null) continue;
                        c.id = d.getId();
                        if (c.shortId == null && c.id.length() >= 6) {
                            c.shortId = c.id.substring(0, 6).toUpperCase(Locale.US);
                        }
                        list.add(c);
                    }
                    adapter.setItems(list);
                });
    }

    private void fallbackRenterLoad() {
        db.collection("complaints")
                .whereEqualTo("createdById", userId)
                .get()
                .addOnSuccessListener(qs -> {
                    ArrayList<Complaint> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : qs) {
                        Complaint c = d.toObject(Complaint.class);
                        if (c == null) continue;
                        c.id = d.getId();
                        if (c.shortId == null && c.id.length() >= 6) {
                            c.shortId = c.id.substring(0, 6).toUpperCase(Locale.US);
                        }
                        list.add(c);
                    }
                    adapter.setItems(list);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Fallback renter load failed", e));
    }
}
