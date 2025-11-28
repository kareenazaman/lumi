package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class RenterMessageFragment extends Fragment {

    public RenterMessageFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_renter_message, container, false);

        Button btnOpenContacts = view.findViewById(R.id.btnOpenContacts);
        Button btnOpenComplaints = view.findViewById(R.id.btnOpenComplaints);

        // Open Contacts screen but with renter role
        btnOpenContacts.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), ContactList.class);
            i.putExtra("role", "renter");
            startActivity(i);
        });

        // Shortcut to complaint list (renter side)
        btnOpenComplaints.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), ComplaintList.class);
            startActivity(i);
        });

        return view;
    }
}
