package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class RenterMessageFragment extends Fragment {

    private Button btnMessageManager;
    private Button btnOpenContacts;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // resolved from Firestore
    private String renterId;
    private String propertyId;
    private String propertyName;
    private String managerUserId;
    private String managerName;
    private String managerPhotoUrl;

    public RenterMessageFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_renter_message, container, false);

        btnMessageManager  = view.findViewById(R.id.btnMessageManager);
        btnOpenContacts    = view.findViewById(R.id.btnOpenContacts);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        setupButtons();
        resolveManagerForRenter();   // 🔹 main messaging logic

        return view;
    }

    private void setupButtons() {
        // Initially disable until we resolve manager
        btnMessageManager.setEnabled(false);

        // Open Contacts screen but with renter role
        btnOpenContacts.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), ContactList.class);
            i.putExtra("role", "renter");
            startActivity(i);
        });

        // Message Property Manager
        btnMessageManager.setOnClickListener(v -> {
            if (managerUserId == null) {
                Toast.makeText(requireContext(),
                        "No property manager assigned yet.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Intent i = new Intent(requireContext(), ChatActivity.class);
            i.putExtra(ChatActivity.EXTRA_OTHER_USER_ID,   managerUserId);
            i.putExtra(ChatActivity.EXTRA_OTHER_USER_NAME,
                    !TextUtils.isEmpty(managerName) ? managerName : "Property Manager");
            i.putExtra(ChatActivity.EXTRA_OTHER_PHOTO_URL, managerPhotoUrl);
            // tie conversation to property the renter is staying in
            i.putExtra(ChatActivity.EXTRA_PROPERTY_ID,     propertyId);
            startActivity(i);
        });
    }

    /**
     * Resolve which manager this renter should chat with:
     * 1) Get renter's propertyId from /renters/{uid}
     * 2) Get property doc: /properties/{propertyId}, read ownerUid/managerId
     * 3) Get manager user doc: /users/{managerUserId} for name + avatar
     */
    private void resolveManagerForRenter() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            btnMessageManager.setEnabled(false);
            return;
        }

        renterId = user.getUid();

        // Step 1: /renters/{uid}
        db.collection("renters")
                .document(renterId)
                .get()
                .addOnSuccessListener(renterDoc -> {
                    if (renterDoc == null || !renterDoc.exists()) {
                        btnMessageManager.setEnabled(false);
                        Toast.makeText(requireContext(),
                                "Renter profile not set up yet.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    propertyId   = renterDoc.getString("propertyId");
                    propertyName = renterDoc.getString("propertyName");

                    if (propertyId == null || propertyId.isEmpty()) {
                        btnMessageManager.setEnabled(false);
                        Toast.makeText(requireContext(),
                                "No property assigned yet.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Step 2: /properties/{propertyId} → manager user id
                    db.collection("properties")
                            .document(propertyId)
                            .get()
                            .addOnSuccessListener(this::handlePropertyDoc)
                            .addOnFailureListener(e -> {
                                btnMessageManager.setEnabled(false);
                                Toast.makeText(requireContext(),
                                        "Failed to load property info.",
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnMessageManager.setEnabled(false);
                    Toast.makeText(requireContext(),
                            "Failed to load renter info.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void handlePropertyDoc(DocumentSnapshot propDoc) {
        if (propDoc == null || !propDoc.exists()) {
            btnMessageManager.setEnabled(false);
            Toast.makeText(requireContext(),
                    "Property not found.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Try ownerUid first (as we used in other parts), fallback to managerId
        String ownerUid = propDoc.getString("ownerUid");
        String mgrId    = propDoc.getString("managerId");

        managerUserId = !TextUtils.isEmpty(ownerUid) ? ownerUid : mgrId;

        if (TextUtils.isEmpty(managerUserId)) {
            btnMessageManager.setEnabled(false);
            Toast.makeText(requireContext(),
                    "No manager linked to this property.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Step 3: load manager profile from /users/{managerUserId}
        db.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc != null && userDoc.exists()) {
                        managerName     = userDoc.getString("name");
                        managerPhotoUrl = userDoc.getString("profileImageUrl");
                    }

                    // Update button text to include manager name if available
                    if (!TextUtils.isEmpty(managerName)) {
                        btnMessageManager.setText("Message Manager: " + managerName);
                    } else {
                        btnMessageManager.setText("Message Property Manager");
                    }

                    btnMessageManager.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    btnMessageManager.setEnabled(false);
                    Toast.makeText(requireContext(),
                            "Failed to load manager profile.",
                            Toast.LENGTH_SHORT).show();
                });
    }
}
