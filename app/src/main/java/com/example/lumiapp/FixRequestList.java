// app/src/main/java/com/example/lumiapp/FixRequestList.java
package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

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

public class FixRequestList extends AppCompatActivity {

    private static final String TAG = "FixRequests";

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvFix;
    private FixRequestAdapter adapter;

    private MaterialButton btnCreate;
    private ImageButton backBtn;

    private String userId;
    private String role; // "renter" or "manager"

    private ListenerRegistration renterReg;
    private final List<ListenerRegistration> managerRegs = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Reuse complaints list layout
        setContentView(R.layout.activity_complains_list);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Header text → "Fix Requests"
        TextView recentHeader = findViewById(R.id.recentHeader);
        if (recentHeader != null) {
            recentHeader.setText("Fix Requests");
        }

        rvFix = findViewById(R.id.rvComplaint);
        rvFix.setLayoutManager(new LinearLayoutManager(this));

        adapter = new FixRequestAdapter(f -> {
            Intent i = new Intent(this, FixRequestDetailActivity.class);
            i.putExtra(FixRequestDetailActivity.EXTRA_FIX_ID, f.id);
            startActivity(i);
        });
        rvFix.setAdapter(adapter);

        btnCreate = findViewById(R.id.create_complain);
        backBtn   = findViewById(R.id.back_btn);

        if (btnCreate != null) {
            btnCreate.setText("Create Fix Request");
            btnCreate.setOnClickListener(v ->
                    startActivity(new Intent(this, CreateFixRequestActivity.class)));
        }

        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }
        userId = auth.getCurrentUser().getUid();

        loadUserRoleThenListen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserRoleThenListen();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (renterReg != null) {
            renterReg.remove();
            renterReg = null;
        }
        for (ListenerRegistration r : managerRegs) r.remove();
        managerRegs.clear();
    }

    private void loadUserRoleThenListen() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(snap -> {
                    role = snap.getString("userType");
                    if (role == null) role = "renter";

                    // clear existing listeners
                    if (renterReg != null) {
                        renterReg.remove();
                        renterReg = null;
                    }
                    for (ListenerRegistration r : managerRegs) r.remove();
                    managerRegs.clear();

                    if ("manager".equalsIgnoreCase(role)) {
                        @SuppressWarnings("unchecked")
                        List<String> managerOf = (List<String>) snap.get("managerOf");
                        loadManagerFixesOnce(managerOf);
                    } else {
                        listenRenterFixes();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "loadUserRole failed", e));
    }

    // Manager: one-time load
    private void loadManagerFixesOnce(@Nullable List<String> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            adapter.setItems(new ArrayList<>());
            Log.d(TAG, "Manager has no properties (fixRequests)");
            return;
        }

        final ArrayList<FixRequest> all = new ArrayList<>();
        final int step = 10;
        final int totalBatches = (int) Math.ceil(propertyIds.size() / (double) step);
        final int[] done = {0};

        for (int i = 0; i < propertyIds.size(); i += step) {
            int end = Math.min(i + step, propertyIds.size());
            List<String> sub = propertyIds.subList(i, end);

            db.collection("fixRequests")
                    .whereIn("propertyId", sub)
                    .get()
                    .addOnSuccessListener(qs -> {
                        for (QueryDocumentSnapshot d : qs) {
                            FixRequest f = d.toObject(FixRequest.class);
                            if (f == null) continue;
                            f.id = d.getId();
                            if (f.shortId == null && f.id.length() >= 6) {
                                f.shortId = f.id.substring(0, 6).toUpperCase(Locale.US);
                            }
                            all.add(f);
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
                        Log.e(TAG, "Manager batch load failed (fixRequests)", e);
                        if (++done[0] == totalBatches) adapter.setItems(all);
                    });
        }
    }

    // Renter realtime
    private void listenRenterFixes() {
        renterReg = db.collection("fixRequests")
                .whereEqualTo("createdById", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((qs, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Renter listen failed (fixRequests). Falling back.", e);
                        fallbackRenterLoad();
                        return;
                    }
                    if (qs == null) {
                        adapter.setItems(new ArrayList<>());
                        return;
                    }

                    ArrayList<FixRequest> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : qs) {
                        FixRequest f = d.toObject(FixRequest.class);
                        if (f == null) continue;
                        f.id = d.getId();
                        if (f.shortId == null && f.id.length() >= 6) {
                            f.shortId = f.id.substring(0, 6).toUpperCase(Locale.US);
                        }
                        list.add(f);
                    }
                    adapter.setItems(list);
                });
    }

    private void fallbackRenterLoad() {
        db.collection("fixRequests")
                .whereEqualTo("createdById", userId)
                .get()
                .addOnSuccessListener(qs -> {
                    ArrayList<FixRequest> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : qs) {
                        FixRequest f = d.toObject(FixRequest.class);
                        if (f == null) continue;
                        f.id = d.getId();
                        if (f.shortId == null && f.id.length() >= 6) {
                            f.shortId = f.id.substring(0, 6).toUpperCase(Locale.US);
                        }
                        list.add(f);
                    }
                    adapter.setItems(list);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Fallback renter load (fixRequests) failed", e));
    }
}
