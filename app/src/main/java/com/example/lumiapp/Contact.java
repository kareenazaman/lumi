package com.example.lumiapp;

public class Contact {
    private String id;
    private String name;
    private String phone;
    private String email;
    private String propertyName; // for grouping
    private boolean isCustom;    // true = admin-created contact

    public Contact() {
        // Firestore needs empty constructor
    }

    public Contact(String id, String name, String phone, String email,
                   String propertyName, boolean isCustom) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.propertyName = propertyName;
        this.isCustom = isCustom;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getPropertyName() { return propertyName; }
    public boolean isCustom() { return isCustom; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }
    public void setCustom(boolean custom) { isCustom = custom; }
}
