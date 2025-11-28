package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PMDashboardFragment extends Fragment {

    private ImageView headerImage;
    private TextView propertyNameText, addressText;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration userReg;

    public PMDashboardFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_pm_dashboard, container, false);

        headerImage      = view.findViewById(R.id.headerImage);
        propertyNameText = view.findViewById(R.id.propertyNameText);
        addressText      = view.findViewById(R.id.addressText);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Setup RecyclerView (demo data for now)
        RecyclerView rv = view.findViewById(R.id.rvRecent);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<PMDashboardRecentItems> data = Arrays.asList(
                new PMDashboardRecentItems(
                        "Ahsan Habib",
                        "520 Battle Street,\nKamloops, BC, V2C2M2",
                        "900", getString(R.string.rent_paid)
                ),
                new PMDashboardRecentItems(
                        "Ahsan Habib",
                        "520 Battle Street,\nKamloops, BC, V2C2M2",
                        "900", getString(R.string.rent_paid)
                ),
                new PMDashboardRecentItems(
                        "Kareena Zaman",
                        "531 Garibaldi Dr,\nKamloops, BC, V2C1E2",
                        null, getString(R.string.complain_generated)
                ),
                new PMDashboardRecentItems(
                        "Kareena Zaman",
                        "531 Garibaldi Dr,\nKamloops, BC, V2C1E2",
                        null, getString(R.string.complain_generated)
                ),
                new PMDashboardRecentItems(
                        "Kareena Zaman",
                        "531 Garibaldi Dr,\nKamloops, BC, V2C1E2",
                        null, getString(R.string.maintenance_requested)
                )
        );

        PMDashboardRecentItems_Adapter adapter = new PMDashboardRecentItems_Adapter(
                data,
                item -> {
                    // later you can hook this to open complaint/fix detail if needed
                }
        );
        rv.setAdapter(adapter);

        // Buttons
        ImageView complaintsBtn = view.findViewById(R.id.complaint_page_btn);
        if (complaintsBtn != null) {
            complaintsBtn.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), ComplaintList.class);
                startActivity(i);
            });
        }

        ImageView fixBtn = view.findViewById(R.id.fix_request_page_btn);
        if (fixBtn != null) {
            fixBtn.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), FixRequestList.class);
                startActivity(i);
            });
        }

        ImageView contactsBtn = view.findViewById(R.id.contacts_page_btn);
        if (contactsBtn != null) {
            contactsBtn.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), ContactList.class);
                i.putExtra("role", "admin"); // so FAB + edit works
                startActivity(i);
            });
        }

        // Load active property for header
        listenForActiveProperty();

        return view;
    }

    private void listenForActiveProperty() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        // Watch user doc so when they pick a property in PropertyDetailsActivity,
        // this header updates automatically.
        userReg = db.collection("users").document(uid)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) return;

                    String name     = snapshot.getString("activePropertyName");
                    String address  = snapshot.getString("activePropertyAddress");
                    String imageUrl = snapshot.getString("activePropertyImageUrl");

                    if (name != null && !name.isEmpty()) {
                        applyPropertyToHeader(name, address, imageUrl);
                    } else {
                        // No active property set yet → pick first property for manager
                        loadFirstPropertyForUser(uid);
                    }
                });
    }

    private void loadFirstPropertyForUser(String uid) {
        // For managers: use properties where ownerUid == uid
        db.collection("properties")
                .whereEqualTo("ownerUid", uid)
                .orderBy("name", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        DocumentSnapshot doc = qs.getDocuments().get(0);
                        String id       = doc.getId();
                        String name     = doc.getString("name");
                        String address  = doc.getString("address");
                        String type     = doc.getString("type");
                        String imageUrl = doc.getString("imageUrl");

                        applyPropertyToHeader(name, address, imageUrl);
                        saveActivePropertyToUser(uid, id, name, address, type, imageUrl);
                    }
                });
    }

    private void saveActivePropertyToUser(String uid,
                                          String propertyId,
                                          String name,
                                          String address,
                                          String type,
                                          String imageUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("activePropertyId", propertyId);
        updates.put("activePropertyName", name);
        updates.put("activePropertyAddress", address);
        updates.put("activePropertyType", type);
        updates.put("activePropertyImageUrl", imageUrl);

        db.collection("users").document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge());
    }

    private void applyPropertyToHeader(String name, String address, String imageUrl) {
        if (propertyNameText != null) {
            propertyNameText.setText(
                    name != null && !name.isEmpty()
                            ? name
                            : getString(R.string.app_name)
            );
        }

        if (addressText != null) {
            addressText.setText(
                    address != null && !address.isEmpty()
                            ? address
                            : getString(R.string.addr_line)
            );
        }

        if (headerImage != null) {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(this)
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.img_dashboard_bg)
                        .into(headerImage);
            } else {
                headerImage.setImageResource(R.drawable.img_dashboard_bg);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userReg != null) {
            userReg.remove();
            userReg = null;
        }
    }
}
