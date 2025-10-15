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

        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPhone = findViewById(R.id.tilPhone);
        tilPassword = findViewById(R.id.tilPassword);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.signup_Phone);
        etPassword = findViewById(R.id.signup_Pass);

        btnCreate = findViewById(R.id.btnCreate);
        tvAlready = findViewById(R.id.tvAlready);

        btnCreate.setOnClickListener(v -> tryCreateAccount());
        tvAlready.setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (auth.getCurrentUser() != null) {
            routeAfterAuth(); // already logged in → check profile completeness
        }
    }

    private void tryCreateAccount() {
        clearErrors();

        String name = str(etName);
        String email = str(etEmail);
        String phone = str(etPhone);
        String pass = str(etPassword);

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

                    Toast.makeText(this, "User created!", Toast.LENGTH_SHORT).show();

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
                                // Create/merge base user doc
                                String uid = auth.getCurrentUser().getUid();
                                Map<String, Object> profile = new HashMap<>();
                                profile.put("uid", uid);
                                profile.put("name", name);
                                profile.put("email", email);
                                profile.put("phone", phone);
                                profile.put("createdAt", Timestamp.now());
                                profile.put("provider", "password");
                                // mark role (if you want explicit)
                                profile.put("userType", "manager");
                                // ensure pmCompleted exists (false until setup finishes)
                                profile.put("pmCompleted", false);

                                db.collection("users").document(uid)
                                        .set(profile)
                                        .addOnSuccessListener(unused -> routeAfterAuth())
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Profile save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            // Even if user doc failed, still try routing (it will fall back to PMAccSetup)
                                            routeAfterAuth();
                                        })
                                        .addOnCompleteListener(done -> toggleLoading(false));
                            })
                            .addOnFailureListener(e -> {
                                toggleLoading(false);
                                Toast.makeText(this, "Profile update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                routeAfterAuth();
                            });
                })
                .addOnFailureListener(e -> {
                    toggleLoading(false);
                    showError(e);
                });
    }

    /** Decides where to go next: PM setup (if incomplete) or Main dashboard */
    private void routeAfterAuth() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    boolean pmCompleted = doc != null && Boolean.TRUE.equals(doc.getBoolean("pmCompleted"));
                    if (pmCompleted) {
                        goToDashboard();
                    } else {
                        goToPMSetup();
                    }
                })
                .addOnFailureListener(e -> {
                    // If we can’t read the user doc, default to setup to be safe
                    goToPMSetup();
                });
    }

    private void goToPMSetup() {
        Intent i = new Intent(SignupActivity.this, PMAccSetup.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToDashboard() {
        Intent i = new Intent(SignupActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void toggleLoading(boolean loading) {
        btnCreate.setEnabled(!loading);
        btnCreate.setText(loading ? getString(R.string.creating) : getString(R.string.create_account));
    }

    private void clearErrors() {
        tilName.setError(null);
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilPassword.setError(null);
    }

    private String str(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void showError(Exception e) {
        String msg = (e != null && e.getMessage() != null) ? e.getMessage() : "Signup failed";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
