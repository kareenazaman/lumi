package com.example.lumiapp;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

public class PropertyDetailsActivity extends AppCompatActivity {

    private ImageView imgPropertyDetail;
    private TextView tvName, tvAddress, tvType, tvRenter, tvParking;
    private ImageButton backBtn;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_property_details);

        db = FirebaseFirestore.getInstance();

        imgPropertyDetail = findViewById(R.id.imgPropertyDetail);
        tvName            = findViewById(R.id.tvPropertyNameDetail);
        tvAddress         = findViewById(R.id.tvPropertyAddressDetail);
        tvType            = findViewById(R.id.tvPropertyTypeDetail);
        tvRenter          = findViewById(R.id.tvRenterCapacityDetail);
        tvParking         = findViewById(R.id.tvParkingCapacityDetail);
        backBtn           = findViewById(R.id.back_btn);

        backBtn.setOnClickListener(v -> onBackPressed());

        String propertyId = getIntent().getStringExtra("propertyId");
        if (propertyId == null || propertyId.isEmpty()) {
            Toast.makeText(this, "No property ID provided", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadProperty(propertyId);
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

                    String name      = doc.getString("name");
                    String address   = doc.getString("address");
                    String type      = doc.getString("type");
                    Long renterCap   = doc.getLong("renterCapacity");
                    Long parkingCap  = doc.getLong("parkingCapacity");
                    String imageUrl  = doc.getString("imageUrl");

                    tvName.setText(name != null ? name : "Unnamed property");
                    tvAddress.setText(address != null ? address : "No address");
                    tvType.setText(type != null ? "Type: " + type : "Type: N/A");
                    tvRenter.setText("Renter capacity: " + (renterCap != null ? renterCap : 0));
                    tvParking.setText("Parking capacity: " + (parkingCap != null ? parkingCap : 0));

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(this)
                                .load(imageUrl)
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
}
