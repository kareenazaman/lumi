package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private String userId;
    private String role; // "renter" or "manager"

    private ListenerRegistration renterReg;
    private final List<ListenerRegistration> managerRegs = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complains_list); // ✅ your XML

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

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
                startActivity(new Intent(this, CreateComplaint.class))); // use your actual class name
        backBtn.setOnClickListener(v -> finish());

        if (auth.getCurrentUser() == null) { finish(); return; }
        userId = auth.getCurrentUser().getUid();

        loadUserRoleThenListen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // reattach listeners (safe no-op if already attached)
        loadUserRoleThenListen();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (renterReg != null) { renterReg.remove(); renterReg = null; }
        for (ListenerRegistration r : managerRegs) r.remove();
        managerRegs.clear();
    }

    private void loadUserRoleThenListen() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(snap -> {
                    role = snap.getString("userType");
                    if (role == null) role = "renter";

                    // clear existing listeners
                    if (renterReg != null) { renterReg.remove(); renterReg = null; }
                    for (ListenerRegistration r : managerRegs) r.remove();
                    managerRegs.clear();

                    if ("manager".equalsIgnoreCase(role)) {
                        @SuppressWarnings("unchecked")
                        List<String> managerOf = (List<String>) snap.get("managerOf");
                        loadManagerComplaintsOnce(managerOf);

                    } else {
                        listenRenterComplaints();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "loadUserRole failed", e));
    }

    private void loadManagerComplaintsOnce(@Nullable List<String> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            adapter.setItems(new ArrayList<>());
            Log.d(TAG, "Manager has no properties");
            return;
        }

        final ArrayList<Complaint> all = new ArrayList<>();
        final int step = 10; // Firestore whereIn limit
        final int totalBatches = (int) Math.ceil(propertyIds.size() / (double) step);
        final int[] done = {0};

        for (int i = 0; i < propertyIds.size(); i += step) {
            int end = Math.min(i + step, propertyIds.size());
            List<String> sub = propertyIds.subList(i, end);

            // ⚠️ No orderBy here (avoids composite index requirement)
            db.collection("complaints")
                    .whereIn("propertyId", sub)
                    .get()
                    .addOnSuccessListener(qs -> {
                        for (QueryDocumentSnapshot d : qs) {
                            Complaint c = d.toObject(Complaint.class);
                            if (c == null) continue;
                            c.id = d.getId();
                            if (c.shortId == null && c.id.length() >= 6) {
                                c.shortId = c.id.substring(0,6).toUpperCase(Locale.US);
                            }
                            all.add(c);
                        }
                        if (++done[0] == totalBatches) {
                            // sort locally by createdAt desc
                            all.sort((a,b) -> {
                                long la = a.createdAt != null ? a.createdAt.toDate().getTime() : 0L;
                                long lb = b.createdAt != null ? b.createdAt.toDate().getTime() : 0L;
                                return Long.compare(lb, la);
                            });
                            Log.d(TAG, "Manager loaded " + all.size() + " complaints");
                            adapter.setItems(all);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Manager batch load failed", e);
                        if (++done[0] == totalBatches) adapter.setItems(all);
                    });
        }
    }

    /** Realtime list for renters */
    private void listenRenterComplaints() {
        renterReg = db.collection("complaints")
                .whereEqualTo("createdById", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((qs, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Renter listen failed (likely needs index). Falling back.", e);
                        // Fallback WITHOUT orderBy so you still see items
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
                            c.shortId = c.id.substring(0,6).toUpperCase(Locale.US);
                        }
                        list.add(c);
                    }
                    Log.d(TAG, "Renter got " + list.size() + " items");
                    if (!list.isEmpty()) {
                        Log.d(TAG, "First doc fields: " + qs.getDocuments().get(0).getData());
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
                            c.shortId = c.id.substring(0,6).toUpperCase(Locale.US);
                        }
                        list.add(c);
                    }
                    Log.d(TAG, "Fallback renter got " + list.size() + " items");
                    adapter.setItems(list);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Fallback renter load failed", e));
    }

    /** Realtime list for managers (handles >10 property IDs in batches) */
    private void listenManagerComplaints(@Nullable List<String> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            Log.d(TAG, "Manager has no properties");
            adapter.setItems(new ArrayList<>());
            return;
        }

        int step = 10;
        for (int i = 0; i < propertyIds.size(); i += step) {
            int end = Math.min(i + step, propertyIds.size());
            List<String> sub = propertyIds.subList(i, end);

            ListenerRegistration reg = db.collection("complaints")
                    .whereIn("propertyId", sub)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener((qs, e) -> {
                        if (e != null) {
                            Log.e(TAG, "Manager listen failed (maybe needs index). Falling back this batch.", e);
                            fallbackManagerLoad(sub);
                            return;
                        }
                        mergeAndDisplayManager(qs);
                    });

            managerRegs.add(reg);
        }
    }

    private void fallbackManagerLoad(List<String> sub) {
        db.collection("complaints")
                .whereIn("propertyId", sub)
                .get()
                .addOnSuccessListener(this::mergeAndDisplayManager)
                .addOnFailureListener(e -> Log.e(TAG, "Fallback manager load failed", e));
    }

    /** Merge results from multiple listeners/batches and display in one sorted list */
    private synchronized void mergeAndDisplayManager(@Nullable com.google.firebase.firestore.QuerySnapshot qs) {
        // Collect from all active batches
        ArrayList<Complaint> all = new ArrayList<>();
        for (ListenerRegistration ignored : managerRegs) {
            // We can't read other batches' snapshots directly here,
            // so just rebuild from Firestore quickly (fast for small apps).
        }
        // Simpler: do a full one-time rebuild across all properties on every callback.
        // This trades a bit of efficiency for correctness/clarity.
        db.collection("users").document(userId).get()
                .addOnSuccessListener(snap -> {
                    @SuppressWarnings("unchecked")
                    List<String> allProps = (List<String>) snap.get("managerOf");
                    if (allProps == null || allProps.isEmpty()) { adapter.setItems(new ArrayList<>()); return; }

                    // Batch fetch all again (keep code DRY)
                    int step = 10;
                    final ArrayList<Complaint> merged = new ArrayList<>();
                    final int totalBatches = (int) Math.ceil(allProps.size() / (double) step);
                    final int[] done = {0};

                    for (int i = 0; i < allProps.size(); i += step) {
                        int end = Math.min(i + step, allProps.size());
                        List<String> sub = allProps.subList(i, end);

                        db.collection("complaints")
                                .whereIn("propertyId", sub)
                                .orderBy("createdAt", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(q -> {
                                    for (QueryDocumentSnapshot d : q) {
                                        Complaint c = d.toObject(Complaint.class);
                                        if (c == null) continue;
                                        c.id = d.getId();
                                        if (c.shortId == null && c.id.length() >= 6) {
                                            c.shortId = c.id.substring(0,6).toUpperCase(Locale.US);
                                        }
                                        merged.add(c);
                                    }
                                    if (++done[0] == totalBatches) {
                                        merged.sort((a,b) -> {
                                            long la = a.createdAt != null ? a.createdAt.toDate().getTime() : 0L;
                                            long lb = b.createdAt != null ? b.createdAt.toDate().getTime() : 0L;
                                            return Long.compare(lb, la);
                                        });
                                        Log.d(TAG, "Manager merged " + merged.size() + " items");
                                        if (!merged.isEmpty()) {
                                            Log.d(TAG, "First doc fields: " + merged.get(0).createdByName + ", " + merged.get(0).propertyId);
                                        }
                                        adapter.setItems(merged);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (++done[0] == totalBatches) adapter.setItems(merged);
                                    Log.e(TAG, "Manager rebuild failed", e);
                                });
                    }
                });
    }
}
