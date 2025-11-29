package com.example.lumiapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    private TextView tvForgot;
    private TextView tvCreateAccount;   // 🔹 "No account? Create one"

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Logging in…");
        progressDialog.setCancelable(false);

        bindViews();
        setupListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If user is already logged in, route them correctly
        if (auth.getCurrentUser() != null) {
            checkUserRole();
        }
    }

    private void bindViews() {
        tilEmail        = findViewById(R.id.tilEmail);
        tilPassword     = findViewById(R.id.tilPassword);
        etEmail         = findViewById(R.id.etEmail);
        etPassword      = findViewById(R.id.signup_Pass); // id from your XML
        btnLogin        = findViewById(R.id.btnLogin);
        tvForgot        = findViewById(R.id.tvForgot);
        tvCreateAccount = findViewById(R.id.tvCreateAccount); // from XML
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        tvForgot.setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

            if (TextUtils.isEmpty(email)) {
                tilEmail.setError("Enter your email first");
                etEmail.requestFocus();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Enter a valid email");
                etEmail.requestFocus();
                return;
            }

            sendPasswordReset(email);
        });

        // 🔹 "No account? Create one" → go to SignupActivity
        tvCreateAccount.setOnClickListener(v -> {
            Intent i = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(i);
            finish(); // optional, remove if you want back button to return to login
        });
    }

    private void attemptLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        tilEmail.setError(null);
        tilPassword.setError(null);

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        doFirebaseLogin(email, password);
    }

    private void doFirebaseLogin(@NonNull String email, @NonNull String password) {
        progressDialog.setMessage("Logging in…");
        progressDialog.show();
        btnLogin.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    btnLogin.setEnabled(true);

                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
                        // ✅ Instead of going to MainActivity directly, route by role
                        checkUserRole();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendPasswordReset(@NonNull String email) {
        progressDialog.setMessage("Sending reset email…");
        progressDialog.show();

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    progressDialog.setMessage("Logging in…");

                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Password reset email sent.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed to send reset email";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ================== ROLE / ROUTING LOGIC (same idea as SignupActivity) ==================

    private void checkUserRole() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc != null && doc.exists()) {
                        String userType = doc.getString("userType");
                        if (userType == null || userType.isEmpty()) {
                            // No role chosen yet
                            goToRoleSelect();
                        } else if ("renter".equals(userType)) {
                            checkRenterDetails(uid);
                        } else if ("manager".equals(userType)) {
                            checkManagerDetails(uid);
                        } else {
                            // Unknown role → force role selection
                            goToRoleSelect();
                        }
                    } else {
                        // No user doc → force role selection / profile setup
                        goToRoleSelect();
                    }
                })
                .addOnFailureListener(e -> goToRoleSelect());
    }

    private void checkRenterDetails(String uid) {
        db.collection("renters")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String propertyId = doc.getString("propertyId");
                        if (!TextUtils.isEmpty(propertyId)) {
                            // Renter fully set up → renter dashboard
                            goToRenterDashboard();
                        } else {
                            // Renter has no property yet → renter setup
                            goToRenterAccSetup();
                        }
                    } else {
                        // No renter doc → renter setup
                        goToRenterAccSetup();
                    }
                })
                .addOnFailureListener(e -> goToRenterAccSetup());
    }

    private void checkManagerDetails(String uid) {
        db.collection("properties")
                .whereEqualTo("ownerUid", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        // Manager already has at least one property
                        goToManagerDashboard();
                    } else {
                        // Manager but no property → go to PM setup
                        goToPMAccSetup();
                    }
                })
                .addOnFailureListener(e -> goToPMAccSetup());
    }

    private void goToRoleSelect() {
        Intent i = new Intent(LoginActivity.this, activity_role_select.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToRenterDashboard() {
        Intent i = new Intent(LoginActivity.this, RenterDashboardContainer.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToManagerDashboard() {
        Intent i = new Intent(LoginActivity.this, PMDashboardContainer.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToRenterAccSetup() {
        Intent i = new Intent(LoginActivity.this, RenterAccSetup.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToPMAccSetup() {
        Intent i = new Intent(LoginActivity.this, PMAccSetup.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
