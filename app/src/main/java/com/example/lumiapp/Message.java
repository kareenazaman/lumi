package com.example.lumiapp;

import com.google.firebase.Timestamp;

public class Message {
    public String id;
    public String text;
    public String senderId;
    public String receiverId;
    public Timestamp createdAt;
    public boolean seen;

    public Message() {
        // Firestore needs empty constructor
    }

    public Message(String text, String senderId, String receiverId, Timestamp createdAt) {
        this.text = text;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.createdAt = createdAt;
        this.seen = false;
    }
}
