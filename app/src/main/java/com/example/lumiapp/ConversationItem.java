package com.example.lumiapp;

import com.google.firebase.Timestamp;

public class ConversationItem {

    private String conversationId;
    private String otherUserId;
    private String otherUserName;
    private String otherUserPhotoUrl;

    private String lastMessageText;
    private Timestamp lastMessageAt;

    public ConversationItem() {
    }

    public ConversationItem(String conversationId,
                            String otherUserId,
                            String otherUserName,
                            String otherUserPhotoUrl,
                            String lastMessageText,
                            Timestamp lastMessageAt) {
        this.conversationId = conversationId;
        this.otherUserId = otherUserId;
        this.otherUserName = otherUserName;
        this.otherUserPhotoUrl = otherUserPhotoUrl;
        this.lastMessageText = lastMessageText;
        this.lastMessageAt = lastMessageAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getOtherUserId() {
        return otherUserId;
    }

    public String getOtherUserName() {
        return otherUserName;
    }

    public String getOtherUserPhotoUrl() {
        return otherUserPhotoUrl;
    }

    public String getLastMessageText() {
        return lastMessageText;
    }

    public Timestamp getLastMessageAt() {
        return lastMessageAt;
    }

    public void setOtherUserName(String otherUserName) {
        this.otherUserName = otherUserName;
    }

    public void setOtherUserPhotoUrl(String otherUserPhotoUrl) {
        this.otherUserPhotoUrl = otherUserPhotoUrl;
    }

    public void setLastMessageText(String lastMessageText) {
        this.lastMessageText = lastMessageText;
    }

    public void setLastMessageAt(Timestamp lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}
