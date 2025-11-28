package com.example.lumiapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class RenterPropertyFragment extends Fragment {

    private TextView tvPropertyName, tvPropertyAddress, tvRoomNumber, tvStatus;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public RenterPropertyFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_renter_property, container, false);

        tvPropertyName   = view.findViewById(R.id.tvPropertyName);
        tvPropertyAddress= view.findViewById(R.id.tvPropertyAddress);
        tvRoomNumber     = view.findViewById(R.id.tvRoomNumber);
        tvStatus         = view.findViewById(R.id.tvStatus);
        progressBar      = view.findViewById(R.id.progressBar);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        loadRenterProperty();

        return view;
    }

    private void loadRenterProperty() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            tvStatus.setText("You are not signed in.");
            progressBar.setVisibility(View.GONE);
            return;
        }

        String uid = user.getUid();
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Loading your property...");

        // RenterAccSetup writes /renters/{uid} with propertyName, propertyId, roomNumber, previousAddress etc.
        db.collection("renters")
                .document(uid)
                .get()
                .addOnSuccessListener(this::applyRenterDoc)
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Failed to load property: " + e.getMessage());
                });
    }

    private void applyRenterDoc(DocumentSnapshot doc) {
        progressBar.setVisibility(View.GONE);

        if (doc == null || !doc.exists()) {
            tvStatus.setText("You are not assigned to any property yet.");
            return;
        }

        String propertyName = doc.getString("propertyName");
        String propertyId   = doc.getString("propertyId");
        String roomNumber   = doc.getString("roomNumber");
        String prevAddress  = doc.getString("previousAddress");

        if (TextUtils.isEmpty(propertyName) && TextUtils.isEmpty(propertyId)) {
            tvStatus.setText("You are not assigned to any property yet.");
        } else {
            tvStatus.setText(""); // clear status
        }

        tvPropertyName.setText(
                !TextUtils.isEmpty(propertyName) ? propertyName : "Your Property"
        );

        // Use previousAddress if you stored it, otherwise just show propertyId as fallback
        if (!TextUtils.isEmpty(prevAddress)) {
            tvPropertyAddress.setText(prevAddress);
        } else if (!TextUtils.isEmpty(propertyId)) {
            tvPropertyAddress.setText("Property ID: " + propertyId);
        } else {
            tvPropertyAddress.setText("Address not set");
        }

        tvRoomNumber.setText(
                !TextUtils.isEmpty(roomNumber) ? "Room / Unit: " + roomNumber : "Room not set"
        );
    }
}
