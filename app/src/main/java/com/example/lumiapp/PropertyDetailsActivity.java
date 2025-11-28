package com.example.lumiapp;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class PropertyDetailsActivity extends AppCompatActivity {

    private ImageView imgPropertyDetail;
    private TextView tvName, tvAddress, tvType, tvRenter, tvParking;
    private ImageButton backBtn;
    private MaterialButton btnSelectProperty;

    private FirebaseFirestore db;

    // Keep these so we can store them in the user doc
    private String propertyId;
    private String propertyName;
    private String propertyAddress;
    private String propertyType;
    private String propertyImageUrl;
    private Long renterCap;
    private Long parkingCap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_property_details);

        db = FirebaseFirestore.getInstance();

        imgPropertyDetail   = findViewById(R.id.imgPropertyDetail);
        tvName              = findViewById(R.id.tvPropertyNameDetail);
        tvAddress           = findViewById(R.id.tvPropertyAddressDetail);
        tvType              = findViewById(R.id.tvPropertyTypeDetail);
        tvRenter            = findViewById(R.id.tvRenterCapacityDetail);
        tvParking           = findViewById(R.id.tvParkingCapacityDetail);
        backBtn             = findViewById(R.id.back_btn);
        btnSelectProperty   = findViewById(R.id.btnSelectProperty);

        backBtn.setOnClickListener(v -> onBackPressed());

        propertyId = getIntent().getStringExtra("propertyId");
        if (propertyId == null || propertyId.isEmpty()) {
            Toast.makeText(this, "No property ID provided", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadProperty(propertyId);

        btnSelectProperty.setOnClickListener(v -> setCurrentProperty());
    }

    private void loadProperty(String propertyId) {
        db.collection("properties").document(propertyId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Property not found", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    propertyName      = doc.getString("name");
                    propertyAddress   = doc.getString("address");
                    propertyType      = doc.getString("type");
                    renterCap         = doc.getLong("renterCapacity");
                    parkingCap        = doc.getLong("parkingCapacity");
                    propertyImageUrl  = doc.getString("imageUrl");

                    tvName.setText(propertyName != null ? propertyName : "Unnamed property");
                    tvAddress.setText(propertyAddress != null ? propertyAddress : "No address");
                    tvType.setText(propertyType != null ? propertyType : "N/A");
                    tvRenter.setText("Renter capacity: " + (renterCap != null ? renterCap : 0));
                    tvParking.setText("Parking capacity: " + (parkingCap != null ? parkingCap : 0));

                    if (propertyImageUrl != null && !propertyImageUrl.isEmpty()) {
                        Glide.with(this)
                                .load(propertyImageUrl)
                                .centerCrop()
                                .placeholder(R.drawable.ic_property_placeholder)
                                .into(imgPropertyDetail);
                    } else {
                        imgPropertyDetail.setImageResource(R.drawable.ic_property_placeholder);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load property: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void setCurrentProperty() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (propertyId == null) {
            Toast.makeText(this, "Property not loaded yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("activePropertyId", propertyId);
        updates.put("activePropertyName", propertyName);
        updates.put("activePropertyAddress", propertyAddress);
        updates.put("activePropertyType", propertyType);
        updates.put("activePropertyImageUrl", propertyImageUrl);

        db.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Property selected.", Toast.LENGTH_SHORT).show();
                    finish(); // go back to previous screen (dashboard or list)
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to set property: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}
