package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class PMPropertyFragment extends Fragment {

    private static final String TAG = "PMPropertyFragment";

    private RecyclerView rvProperties;
    private MaterialButton btnCreateProperty;
    private ImageView headerImage;

    private PMPropertyAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration propertyReg;
    private ListenerRegistration userReg;   // listen to active property changes

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pm_property, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvProperties      = view.findViewById(R.id.rvProperties);
        btnCreateProperty = view.findViewById(R.id.btnCreateProperty);
        headerImage       = view.findViewById(R.id.headerImage);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // 🔹 Back button → always go back to dashboard (MainActivity)
        ImageButton backBtn = view.findViewById(R.id.back_btn);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), MainActivity.class);
                // avoid stacking multiple MainActivity instances
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }

        // 🔹 Recycler + adapter
        adapter = new PMPropertyAdapter(requireContext(), property -> {
            Intent intent = new Intent(requireContext(), PropertyDetailsActivity.class);
            intent.putExtra("propertyId", property.getId());
            startActivity(intent);
        });

        rvProperties.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProperties.setAdapter(adapter);

        // 🔹 Create new property
        btnCreateProperty.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PMAccSetup.class);
            startActivity(intent);
        });

        loadManagerProperties();
        listenToActiveProperty();   // 🔹 update header with selected property image
    }

    private void loadManagerProperties() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No logged-in user; cannot load properties");
            return;
        }

        String uid = user.getUid();

        propertyReg = db.collection("properties")
                .whereEqualTo("ownerUid", uid)
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error loading properties", e);
                        return;
                    }

                    if (snapshot == null) return;

                    List<Property> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Property property = doc.toObject(Property.class);
                        property.setId(doc.getId());
                        list.add(property);
                    }
                    adapter.setData(list);
                });
    }

    /**
     * Listen to the currently active property stored in the user doc and
     * update the header background image to that property's image.
     *
     * Requires:
     * users/{uid} has "activePropertyImageUrl" (set in PropertyDetailsActivity).
     */
    private void listenToActiveProperty() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        userReg = db.collection("users").document(uid)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) return;

                    String imageUrl = snapshot.getString("activePropertyImageUrl");

                    if (headerImage == null) return;

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(imageUrl)
                                .centerCrop()
                                .placeholder(R.drawable.img_dashboard_bg)
                                .into(headerImage);
                    } else {
                        // fallback if no active property image
                        headerImage.setImageResource(R.drawable.img_dashboard_bg);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (propertyReg != null) {
            propertyReg.remove();
            propertyReg = null;
        }
        if (userReg != null) {
            userReg.remove();
            userReg = null;
        }
    }
}
