package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilEmail, tilPhone, tilPassword;
    private TextInputEditText etName, etEmail, etPhone, etPassword;
    private MaterialButton btnCreate;
    private TextView tvAlready;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        tilName     = findViewById(R.id.tilName);
        tilEmail    = findViewById(R.id.tilEmail);
        tilPhone    = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);

        etName     = findViewById(R.id.etName);
        etEmail    = findViewById(R.id.etEmail);
        etPhone    = findViewById(R.id.signup_Phone);
        etPassword = findViewById(R.id.signup_Pass);

        btnCreate = findViewById(R.id.btnCreate);
        tvAlready = findViewById(R.id.tvAlready);

        btnCreate.setOnClickListener(v -> tryCreateAccount());

        // 🔹 Go to LoginActivity when "Already have an account?" is pressed
        tvAlready.setOnClickListener(v -> {
            Intent i = new Intent(SignupActivity.this, LoginActivity.class);
            startActivity(i);
            finish(); // optional: remove if you want to be able to come back with Back button
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 🔹 If already logged in, route based on role + details
        if (auth.getCurrentUser() != null) {
            checkUserRole(); // already logged in → check role + details
        }
    }

    private void tryCreateAccount() {
        clearErrors();

        String name  = str(etName);
        String email = str(etEmail);
        String phone = str(etPhone);
        String pass  = str(etPassword);

        boolean ok = true;
        if (TextUtils.isEmpty(name)) {
            tilName.setError(getString(R.string.required));
            ok = false;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.invalid_email));
            ok = false;
        }
        if (TextUtils.isEmpty(phone) || phone.length() < 7) {
            tilPhone.setError(getString(R.string.invalid_phone));
            ok = false;
        }
        if (TextUtils.isEmpty(pass) || pass.length() < 6) {
            tilPassword.setError(getString(R.string.password_min_chars));
            ok = false;
        }
        if (!ok) return;

        toggleLoading(true);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        toggleLoading(false);
                        showError(task.getException());
                        return;
                    }

                    if (auth.getCurrentUser() == null) {
                        toggleLoading(false);
                        Toast.makeText(this, "User not available after signup", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Update display name
                    UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();

                    auth.getCurrentUser().updateProfile(req)
                            .addOnCompleteListener(updTask -> {
                                String uid = auth.getCurrentUser().getUid();
                                Map<String, Object> profile = new HashMap<>();
                                profile.put("uid", uid);
                                profile.put("name", name);
                                profile.put("email", email);
                                profile.put("phone", phone);
                                profile.put("createdAt", Timestamp.now());
                                profile.put("provider", "password");
                                // Note: userType not set yet → handled later in Role Select

                                db.collection("users").document(uid)
                                        .set(profile)
                                        .addOnSuccessListener(unused -> checkUserRole())
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Profile save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            checkUserRole();
                                        })
                                        .addOnCompleteListener(done -> toggleLoading(false));
                            })
                            .addOnFailureListener(e -> {
                                toggleLoading(false);
                                Toast.makeText(this, "Profile update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                checkUserRole();
                            });
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    showError(e);
                });
    }

    private void checkUserRole() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc != null && doc.exists()) {
                        String userType = doc.getString("userType");
                        if (userType == null || userType.isEmpty()) {
                            goToRoleSelect();
                        } else if (userType.equals("renter")) {
                            checkRenterDetails(uid);
                        } else if (userType.equals("manager")) {
                            checkManagerDetails(uid);
                        } else {
                            goToRoleSelect();
                        }
                    } else {
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
                            goToRenterDashboard();
                        } else {
                            goToRenterAccSetup();
                        }
                    } else {
                        goToRenterAccSetup();
                    }
                })
                .addOnFailureListener(e -> {
                    goToRenterAccSetup();
                });
    }

    private void checkManagerDetails(String uid) {
        db.collection("properties")
                .whereEqualTo("ownerUid", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        goToManagerDashboard();
                    } else {
                        goToPMAccSetup();
                    }
                })
                .addOnFailureListener(e -> {
                    goToPMAccSetup();
                });
    }

    private void goToRoleSelect() {
        Intent i = new Intent(SignupActivity.this, activity_role_select.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToRenterDashboard() {
        Intent i = new Intent(SignupActivity.this, RenterDashboardContainer.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToManagerDashboard() {
        Intent i = new Intent(SignupActivity.this, PMDashboardContainer.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToRenterAccSetup() {
        Intent i = new Intent(SignupActivity.this, RenterAccSetup.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToPMAccSetup() {
        Intent i = new Intent(SignupActivity.this, PMAccSetup.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void toggleLoading(boolean loading) {
        btnCreate.setEnabled(!loading);
        btnCreate.setText(loading
                ? getString(R.string.creating)
                : getString(R.string.create_account));
    }

    private void clearErrors() {
        tilName.setError(null);
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilPassword.setError(null);
    }

    private String str(TextInputEditText et) {
        return et.getText() == null
                ? ""
                : et.getText().toString().trim();
    }

    private void showError(Exception e) {
        String msg = (e != null && e.getMessage() != null)
                ? e.getMessage()
                : "Signup failed";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
