package com.example.lumiapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class PMProfileFragment extends Fragment {

    private ImageView imgProfile;
    private ImageButton btnChangePhoto;
    private MaterialButton btnLogout;

    private TextView tvName, tvEmail, tvPhone, tvRole, tvBuilding, tvUnit;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pm_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Views
        imgProfile = view.findViewById(R.id.imgProfile);
        btnChangePhoto = view.findViewById(R.id.btnChangePhoto);
        tvName = view.findViewById(R.id.tvName);
        tvEmail = view.findViewById(R.id.tvEmail);
        tvPhone = view.findViewById(R.id.tvPhone);
        tvRole = view.findViewById(R.id.tvRole);
        tvBuilding = view.findViewById(R.id.tvBuilding);
        tvUnit = view.findViewById(R.id.tvUnit);
        progressBar = view.findViewById(R.id.profileProgressBar);
        btnLogout = view.findViewById(R.id.btnLogout);


        // Register image picker
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadProfileImage(uri);
                    }
                }
        );

        // Click to change photo
        View.OnClickListener changePhotoClick = v -> {
            if (auth.getCurrentUser() == null) {
                Toast.makeText(requireContext(),
                        "You must be logged in", Toast.LENGTH_SHORT).show();
                return;
            }
            imagePickerLauncher.launch("image/*");
        };

        btnLogout.setOnClickListener(v -> {
            // Sign out from Firebase
            auth.signOut();

            // Go back to Login screen (replace LoginActivity with your login activity class)
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        imgProfile.setOnClickListener(changePhotoClick);
        btnChangePhoto.setOnClickListener(changePhotoClick);

        // Load profile data
        loadUserProfile();
    }

    private void loadUserProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(),
                    "No logged in user", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        showLoading(true);

        // Assuming collection name is "users" and doc id = uid
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(this::bindUserData)
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(requireContext(),
                            "Failed to load profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void bindUserData(DocumentSnapshot doc) {
        showLoading(false);

        if (!doc.exists()) {
            Toast.makeText(requireContext(),
                    "Profile document not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Adjust the field names below to match your Firestore structure
        String name = doc.getString("name");
        String phone = doc.getString("phone");
        String role = doc.getString("role");          // e.g., "manager"
        String building = doc.getString("building");  // e.g., building address/name
        String unit = doc.getString("unit");          // e.g., "305"
        String imageUrl = doc.getString("profileImageUrl");

        FirebaseUser user = auth.getCurrentUser();

        tvName.setText(name != null ? name : "No name");
        tvEmail.setText(user != null && user.getEmail() != null
                ? user.getEmail()
                : "No email");
        tvPhone.setText(phone != null ? phone : "No phone");
        tvRole.setText(role != null ? role : "Property Manager");
        tvBuilding.setText(building != null ? building : "Not set");
        tvUnit.setText(unit != null ? unit : "Not set");

        if (imageUrl != null && !imageUrl.isEmpty() && isAdded()) {
            Glide.with(PMProfileFragment.this)
                    .load(imageUrl)
                    .placeholder(android.R.drawable.sym_def_app_icon)
                    .into(imgProfile);
        }
    }

    private void uploadProfileImage(@NonNull Uri imageUri) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        showLoading(true);

        String uid = user.getUid();
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference()
                .child("profilePictures/" + uid + ".jpg");

        ref.putFile(imageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(this::saveProfileImageUrl)
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(requireContext(),
                            "Upload failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void saveProfileImageUrl(Uri downloadUri) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            return;
        }

        String uid = user.getUid();
        String url = downloadUri.toString();

        Task<Void> updateTask = db.collection("users")
                .document(uid)
                .update("profileImageUrl", url);

        updateTask
                .addOnSuccessListener(unused -> {
                    showLoading(false);
                    if (isAdded()) {
                        Glide.with(PMProfileFragment.this)
                                .load(url)
                                .placeholder(android.R.drawable.sym_def_app_icon)
                                .into(imgProfile);
                    }
                    Toast.makeText(requireContext(),
                            "Profile picture updated",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(requireContext(),
                            "Failed to save image URL: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean loading) {
        if (progressBar == null) return;
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
