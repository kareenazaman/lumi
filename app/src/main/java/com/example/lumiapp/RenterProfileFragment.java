package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class RenterProfileFragment extends Fragment {

    private ImageView imgProfile;
    private ImageButton btnChangePhoto;
    private TextView tvName, tvEmail, tvPhone, tvRoom, tvProperty;
    private ProgressBar progressBar;
    private Button btnLogout;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // Image picker launcher (same pattern as PMProfileFragment)
    private ActivityResultLauncher<String> imagePickerLauncher;

    public RenterProfileFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_renter_profile, container, false);

        imgProfile = view.findViewById(R.id.imgProfile);
        btnChangePhoto = view.findViewById(R.id.btnChangePhoto);
        tvName     = view.findViewById(R.id.tvName);
        tvEmail    = view.findViewById(R.id.tvEmail);
        tvPhone    = view.findViewById(R.id.tvPhone);
        tvRoom     = view.findViewById(R.id.tvRoom);
        tvProperty = view.findViewById(R.id.tvProperty);
        progressBar= view.findViewById(R.id.progressBar);
        btnLogout  = view.findViewById(R.id.btnLogout);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        // Register image picker (same style as PMProfileFragment)
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadProfileImage(uri);
                    }
                }
        );

        // Click handlers to change photo
        View.OnClickListener changePhotoClick = v -> {
            if (auth.getCurrentUser() == null) return;
            imagePickerLauncher.launch("image/*");
        };

        imgProfile.setOnClickListener(changePhotoClick);
        btnChangePhoto.setOnClickListener(changePhotoClick);

        loadUserInfo();
        setupLogout();

        return view;
    }

    private void loadUserInfo() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            tvName.setText("Not signed in");
            tvEmail.setText("");
            tvPhone.setText("");
            progressBar.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        final String uid = firebaseUser.getUid();

        // 1) Load base info from /users/{uid}
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    applyUserDoc(firebaseUser, doc);
                    // 2) Load renter details from /renters/{uid}
                    loadRenterDetails(uid);
                })
                .addOnFailureListener(e -> {
                    applyUserDoc(firebaseUser, null);
                    loadRenterDetails(uid);
                });
    }

    private void applyUserDoc(FirebaseUser firebaseUser, @Nullable DocumentSnapshot doc) {
        String name = null;
        String email = null;
        String phone = null;

        if (doc != null && doc.exists()) {
            name  = doc.getString("name");
            email = doc.getString("email");
            phone = doc.getString("phone");
        }

        if (TextUtils.isEmpty(name)) {
            name = firebaseUser.getDisplayName();
        }
        if (TextUtils.isEmpty(email)) {
            email = firebaseUser.getEmail();
        }

        tvName.setText(!TextUtils.isEmpty(name) ? name : "Renter");
        tvEmail.setText(!TextUtils.isEmpty(email) ? email : "Email not set");
        tvPhone.setText(!TextUtils.isEmpty(phone) ? phone : "Phone not set");
    }

    private void loadRenterDetails(String uid) {
        db.collection("renters")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    progressBar.setVisibility(View.GONE);

                    if (doc != null && doc.exists()) {
                        String propertyName = doc.getString("propertyName");
                        String roomNumber   = doc.getString("roomNumber");
                        String profileUrl   = doc.getString("profilePhotoUrl");

                        tvProperty.setText(
                                !TextUtils.isEmpty(propertyName)
                                        ? propertyName
                                        : "Property not assigned"
                        );

                        tvRoom.setText(
                                !TextUtils.isEmpty(roomNumber)
                                        ? "Room: " + roomNumber
                                        : "Room not set"
                        );

                        if (!TextUtils.isEmpty(profileUrl)) {
                            if (isAdded()) {
                                Glide.with(this)
                                        .load(profileUrl)
                                        .centerCrop()
                                        .placeholder(R.drawable.ic_profile)
                                        .into(imgProfile);
                            }
                        } else {
                            imgProfile.setImageResource(R.drawable.ic_profile);
                        }
                    } else {
                        tvProperty.setText("Property not assigned");
                        tvRoom.setText("Room not set");
                        imgProfile.setImageResource(R.drawable.ic_profile);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    tvProperty.setText("Failed to load renter details");
                });
    }

    private void uploadProfileImage(@NonNull android.net.Uri imageUri) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !isAdded()) return;

        progressBar.setVisibility(View.VISIBLE);

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
                .addOnSuccessListener(downloadUri -> saveProfileImageUrl(uid, downloadUri.toString()))
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    // You can show a Toast but keep it quiet if you want
                });
    }

    private void saveProfileImageUrl(String uid, String url) {
        // Update both users/{uid} and renters/{uid} so all screens stay in sync
        Tasks.whenAllComplete(
                db.collection("users").document(uid).update("profileImageUrl", url),
                db.collection("renters").document(uid).update("profilePhotoUrl", url)
        ).addOnCompleteListener(task -> {
            progressBar.setVisibility(View.GONE);
            if (!isAdded()) return;

            Glide.with(this)
                    .load(url)
                    .centerCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(imgProfile);
        });
    }

    private void setupLogout() {
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            // After logout, send back to Signup/Login flow
            Intent i = new Intent(requireContext(), SignupActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            requireActivity().finish();
        });
    }
}
