package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class activity_role_select extends AppCompatActivity {

    private ImageButton renterRoleBTN, managerRoleBTN;
    private Button roleSubmitBTN;
    private LinearLayout renterLayout, managerLayout;
    private String selectedRole = "";

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_select);

        renterRoleBTN = findViewById(R.id.renterRoleBTN);
        managerRoleBTN = findViewById(R.id.managerRoleBTN);
        roleSubmitBTN = findViewById(R.id.roleSubmitBTN);
        renterLayout = findViewById(R.id.renterLayout);
        managerLayout = findViewById(R.id.managerLayout);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        renterRoleBTN.setOnClickListener(v -> selectRole("renter"));
        managerRoleBTN.setOnClickListener(v -> selectRole("manager"));

        roleSubmitBTN.setOnClickListener(v -> {
            if (selectedRole.isEmpty()) {
                Toast.makeText(this, "Please select a role.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (auth.getCurrentUser() == null) {
                Toast.makeText(this, "No logged-in user found.", Toast.LENGTH_SHORT).show();
                return;
            }

            String uid = auth.getCurrentUser().getUid();
            Map<String, Object> roleData = new HashMap<>();
            roleData.put("userType", selectedRole);

            // 🔹 Save role selection to Firestore
            db.collection("users").document(uid)
                    .update(roleData)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, selectedRole + " selected!", Toast.LENGTH_SHORT).show();
                        checkPropertyAssociation(selectedRole, uid);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to save role: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }

    /** Highlights selected button visually */
    private void selectRole(String role) {
        selectedRole = role;

        if (role.equals("renter")) {
            renterRoleBTN.setBackgroundColor(0xFFFF7420);
            managerRoleBTN.setBackground(getDrawable(R.drawable.role_gradient_bg));
        } else {
            managerRoleBTN.setBackgroundColor(0xFFFF7420);
            renterRoleBTN.setBackground(getDrawable(R.drawable.role_gradient_bg));
        }
    }

    /** Checks Firestore for property association after role selection */
    private void checkPropertyAssociation(String role, String uid) {
        if (role.equals("manager")) {
            // 🔸 Check if any property is managed by this user
            db.collection("property")
                    .whereEqualTo("managerID", uid)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(query -> {
                        if (query.isEmpty()) {
                            // 🚪 No property found → send to property manager setup
                            Intent i = new Intent(this, PMAccSetup.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                        } else {
                            // ✅ Property found → open manager dashboard
                            Intent i = new Intent(this, PMDashboardFragment.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                        }
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error checking property: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } else if (role.equals("renter")) {
            // 🔹 Check if renter is registered to any property
            db.collection("property")
                    .whereEqualTo("renterID", uid)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(query -> {
                        if (query.isEmpty()) {
                            //  No property found → renter setup
                            Intent i = new Intent(this, RenterAccSetup.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                        } else {
                            //  Property exists → renter dashboard
                            Intent i = new Intent(this, DashboardRenter.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                        }
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error checking property: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
