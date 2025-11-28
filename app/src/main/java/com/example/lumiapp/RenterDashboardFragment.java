package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.List;

public class RenterDashboardFragment extends Fragment {

    private ImageView headerImage;
    private TextView propertyNameText, addressText;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public RenterDashboardFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_renter_dashboard, container, false);

        headerImage      = view.findViewById(R.id.headerImage);
        propertyNameText = view.findViewById(R.id.propertyNameText);
        addressText      = view.findViewById(R.id.addressText);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // --- Recent list (demo for now, same as PM) ---
        RecyclerView rv = view.findViewById(R.id.rvRecent);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<PMDashboardRecentItems> data = Arrays.asList(
                new PMDashboardRecentItems(
                        "You",
                        "Your current property",
                        "900", getString(R.string.rent_paid)
                ),
                new PMDashboardRecentItems(
                        "You",
                        "Your current property",
                        null, getString(R.string.complain_generated)
                ),
                new PMDashboardRecentItems(
                        "You",
                        "Your current property",
                        null, getString(R.string.maintenance_requested)
                )
        );

        PMDashboardRecentItems_Adapter adapter = new PMDashboardRecentItems_Adapter(
                data,
                item -> {
                    // later: open rent / complaint details
                }
        );
        rv.setAdapter(adapter);

        // --- Shortcuts ---
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
                i.putExtra("role", "renter");
                startActivity(i);
            });
        }

        ImageView rentBtn = view.findViewById(R.id.rent_page_btn);
        if (rentBtn != null) {
            rentBtn.setOnClickListener(v -> {
                // TODO: later → open renter rent history / pay rent page
            });
        }

        // --- Load renter’s property into header ---
        loadRenterPropertyHeader();

        return view;
    }

    private void loadRenterPropertyHeader() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            applyPropertyToHeader(null, null, null);
            return;
        }

        String uid = user.getUid();

        db.collection("renters")
                .document(uid)
                .get()
                .addOnSuccessListener(renterDoc -> {
                    if (!isAdded()) return; // fragment might be detached

                    if (renterDoc == null || !renterDoc.exists()) {
                        applyPropertyToHeader(null, null, null);
                        return;
                    }

                    String propertyName = renterDoc.getString("propertyName");
                    String propertyId   = renterDoc.getString("propertyId");
                    String prevAddress  = renterDoc.getString("previousAddress");

                    // If we have propertyId, get full details from /properties
                    if (!TextUtils.isEmpty(propertyId)) {
                        db.collection("properties")
                                .document(propertyId)
                                .get()
                                .addOnSuccessListener(propDoc -> {
                                    if (!isAdded()) return;

                                    if (propDoc != null && propDoc.exists()) {
                                        String name     = propDoc.getString("name");
                                        String address  = propDoc.getString("address");
                                        String imageUrl = propDoc.getString("imageUrl");

                                        if (TextUtils.isEmpty(name)) {
                                            name = propertyName;
                                        }
                                        if (TextUtils.isEmpty(address)) {
                                            address = prevAddress;
                                        }

                                        applyPropertyToHeader(name, address, imageUrl);
                                    } else {
                                        applyPropertyToHeader(propertyName, prevAddress, null);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (!isAdded()) return;
                                    applyPropertyToHeader(propertyName, prevAddress, null);
                                });
                    } else {
                        // No propertyId yet → just use renter doc fields
                        applyPropertyToHeader(propertyName, prevAddress, null);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    applyPropertyToHeader(null, null, null);
                });
    }

    private void applyPropertyToHeader(String name,
                                       String address,
                                       String imageUrl) {
        if (!isAdded()) return; // fragment not attached → avoid crash

        if (propertyNameText != null) {
            propertyNameText.setText(
                    !TextUtils.isEmpty(name)
                            ? name
                            : getString(R.string.app_name)
            );
        }

        if (addressText != null) {
            addressText.setText(
                    !TextUtils.isEmpty(address)
                            ? address
                            : getString(R.string.addr_line)
            );
        }

        if (headerImage != null) {
            if (!TextUtils.isEmpty(imageUrl)) {
                Glide.with(requireContext())
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.img_dashboard_bg)
                        .into(headerImage);
            } else {
                headerImage.setImageResource(R.drawable.img_dashboard_bg);
            }
        }
    }
}
