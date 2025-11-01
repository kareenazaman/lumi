package com.example.lumiapp;

import com.google.firebase.Timestamp;

public class Complaint {
    public String id;             // Firestore doc id
    public String shortId;        // first 6 chars (computed)
    public String createdById;
    public String createdByName;
    public String createdByRole;  // "renter" | "manager"
    public String roomNumber;     // "Property Manager" for manager-created
    public String propertyId;
    public String propertyAddress;
    public String status;         // "open" | "pending" | "closed"
    public String createdDate;    // "dd-MM-yyyy"
    public Timestamp createdAt;
    public String description;

    public Complaint() {} // Firestore needs empty ctor
}
