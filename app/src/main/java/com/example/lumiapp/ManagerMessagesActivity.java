package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ManagerMessagesActivity extends AppCompatActivity {

    private static final String TAG = "ManagerMessages";

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String managerId;

    private RecyclerView rvConversations;
    private ConversationAdapter adapter;
    private ImageButton backBtn;

    private ListenerRegistration conversationsReg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_messages);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }
        managerId = auth.getCurrentUser().getUid();

        rvConversations = findViewById(R.id.rvConversations);
        backBtn         = findViewById(R.id.back_btn);

        adapter = new ConversationAdapter(item -> {
            // Open ChatActivity with this conversation's other user
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra(ChatActivity.EXTRA_OTHER_USER_ID, item.getOtherUserId());
            i.putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, item.getOtherUserName());
            i.putExtra(ChatActivity.EXTRA_OTHER_PHOTO_URL, item.getOtherUserPhotoUrl());
            // propertyId is optional, you can also store it in ConversationItem if needed
            startActivity(i);
        });

        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());

        listenForConversations();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (conversationsReg != null) {
            conversationsReg.remove();
        }
    }

    private void listenForConversations() {
        // conversations where manager is a participant
        conversationsReg = db.collection("conversations")
                .whereArrayContains("participants", managerId)
                .addSnapshotListener((qs, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Conversations listen failed", e);
                        return;
                    }
                    if (qs == null) {
                        adapter.setItems(new ArrayList<>());
                        return;
                    }

                    // For each conversation doc, resolve the "other" user
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        handleConversationDoc(doc);
                    }
                });
    }

    private void handleConversationDoc(DocumentSnapshot doc) {
        if (!doc.exists()) return;

        String conversationId = doc.getId();

        // participants: [managerId, otherUserId]
        List<String> participants = (List<String>) doc.get("participants");
        if (participants == null || participants.size() < 2) return;

        String otherUserId = null;
        for (String p : participants) {
            if (!managerId.equals(p)) {
                otherUserId = p;
                break;
            }
        }
        if (otherUserId == null) return;

        String lastMessageText = doc.getString("lastMessageText");
        com.google.firebase.Timestamp lastMessageAt =
                (com.google.firebase.Timestamp) doc.get("lastMessageAt");

        // Initially create a ConversationItem with just IDs & lastMessage
        ConversationItem baseItem = new ConversationItem(
                conversationId,
                otherUserId,
                null,
                null,
                lastMessageText,
                lastMessageAt
        );

        // Fetch user profile for name + photo
        db.collection("users")
                .document(otherUserId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    String otherName = userDoc.getString("name");
                    String photoUrl  = userDoc.getString("profileImageUrl");

                    baseItem.setOtherUserName(
                            !TextUtils.isEmpty(otherName) ? otherName : "User"
                    );
                    baseItem.setOtherUserPhotoUrl(photoUrl);

                    adapter.upsertItem(baseItem);
                })
                .addOnFailureListener(err -> {
                    Log.e(TAG, "Failed to load user for conversation", err);
                    // still show conversation with fallback name
                    baseItem.setOtherUserName("User");
                    adapter.upsertItem(baseItem);
                });
    }
}
