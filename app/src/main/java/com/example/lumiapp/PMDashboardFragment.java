package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

public class PMDashboardFragment extends Fragment {

    public PMDashboardFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_pm_dashboard, container, false);

        // Setup RecyclerView
        RecyclerView rv = view.findViewById(R.id.rvRecent);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Temporary demo data (replace with Firestore or DB data later)
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

        // Create adapter
        PMDashboardRecentItems_Adapter adapter = new PMDashboardRecentItems_Adapter(
                data,
                item -> {
                    // later you can hook this to open complaint/fix detail if needed
                }
        );

        // Attach adapter to RecyclerView
        rv.setAdapter(adapter);

        // 🔹 Complaints list button → open ComplaintList
        ImageView complaintsBtn = view.findViewById(R.id.complaint_page_btn);
        if (complaintsBtn != null) {
            complaintsBtn.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), ComplaintList.class);
                startActivity(i);
            });
        }

        // 🔹 Fix requests list button → open FixRequestList
        ImageView fixBtn = view.findViewById(R.id.fix_request_page_btn);
        if (fixBtn != null) {
            fixBtn.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), FixRequestList.class);
                startActivity(i);
            });
        }
        // 🔹 Fix requests list button → open ContactPage
        ImageView contactsBtn = view.findViewById(R.id.contacts_page_btn);
        if (contactsBtn != null) {
            contactsBtn.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), ContactList.class);
                i.putExtra("role", "admin"); // so FAB + edit works
                startActivity(i);
            });
        }


        return view;
    }
}
