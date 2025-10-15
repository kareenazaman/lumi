package com.example.lumiapp;

public class PMDashboardRecentItems {
    public final String name;
    public final String address;
    public final String amount; // null or "" if not a payment row
    public final String status;

    public PMDashboardRecentItems(String name, String address, String amount, String status) {
        this.name = name;
        this.address = address;
        this.amount = amount;
        this.status = status;
    }
}