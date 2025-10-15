package com.example.lumiapp;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;


public class DashboardPropertyManager extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard_pm);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inflate the screen layout
        setContentView(R.layout.activity_dashboard_pm);

        // 1) Setup RecyclerView
        RecyclerView rv = findViewById(R.id.rvRecent);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // 2) Temporary demo data (replace with real data later)
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

        // 3) Create adapter with optional click callback
        PMDashboardRecentItems_Adapter adapter =
                new PMDashboardRecentItems_Adapter(data, item -> {
                    // TODO: handle click (navigate or show details)
                });

        // 4) Hook adapter to RecyclerView
        rv.setAdapter(adapter);

    }
}