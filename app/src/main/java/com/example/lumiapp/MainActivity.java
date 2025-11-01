package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main); // simple splash/progress layout
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (auth.getCurrentUser() == null) {
            // Not signed in → back to signup
            Intent i = new Intent(this, SignupActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        // Signed in → fetch user doc and decide
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(this::routeByUserDoc)
                .addOnFailureListener(e -> {
                    // If we can’t read, be safe and send to PM setup
                    Toast.makeText(this, "Loading profile failed, opening setup.", Toast.LENGTH_SHORT).show();
                    goToPMSetup();
                });
    }

    private void routeByUserDoc(DocumentSnapshot doc) {
        // For now: treat all as Property Manager
        boolean pmCompleted = doc != null && Boolean.TRUE.equals(doc.getBoolean("pmCompleted"));
        if (pmCompleted) {
            goToPMDashboard();
        } else {
            goToPMSetup();
        }
    }

    private void goToPMDashboard() {
        Intent i = new Intent(this, PMDashboardContainer.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToPMSetup() {
        Intent i = new Intent(this, PMAccSetup.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
