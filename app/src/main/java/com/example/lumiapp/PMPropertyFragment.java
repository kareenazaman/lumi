package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    private PMPropertyAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration propertyReg;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pm_property, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvProperties = view.findViewById(R.id.rvProperties);
        btnCreateProperty = view.findViewById(R.id.btnCreateProperty);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        ImageButton backBtn = view.findViewById(R.id.back_btn);
        backBtn.setOnClickListener(v -> requireActivity().onBackPressed());


        adapter = new PMPropertyAdapter(requireContext(), property -> {
            Intent intent = new Intent(requireContext(), PropertyDetailsActivity.class);
            intent.putExtra("propertyId", property.getId());
            startActivity(intent);
        });

        rvProperties.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProperties.setAdapter(adapter);

        btnCreateProperty.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PMAccSetup.class);
            startActivity(intent);
        });

        loadManagerProperties();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (propertyReg != null) {
            propertyReg.remove();
            propertyReg = null;
        }
    }
}
