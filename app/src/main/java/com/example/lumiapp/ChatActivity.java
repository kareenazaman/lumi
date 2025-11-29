package com.example.lumiapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_OTHER_USER_ID   = "otherUserId";
    public static final String EXTRA_OTHER_USER_NAME = "otherUserName";
    public static final String EXTRA_OTHER_PHOTO_URL = "otherPhotoUrl";
    public static final String EXTRA_PROPERTY_ID     = "propertyId"; // optional

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String currentUserId;
    private String otherUserId;
    private String otherUserName;
    private String otherPhotoUrl;
    private String propertyId;   // for renter/manager binding
    private String conversationId;

    private ImageButton btnBack, btnSend;
    private ImageView imgOtherProfile;
    private TextView tvOtherName;
    private EditText etMessage;
    private RecyclerView rvMessages;
    private MessageAdapter adapter;

    private ListenerRegistration messageListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }
        currentUserId = auth.getCurrentUser().getUid();

        // Read extras
        otherUserId   = getIntent().getStringExtra(EXTRA_OTHER_USER_ID);
        otherUserName = getIntent().getStringExtra(EXTRA_OTHER_USER_NAME);
        otherPhotoUrl = getIntent().getStringExtra(EXTRA_OTHER_PHOTO_URL);
        propertyId    = getIntent().getStringExtra(EXTRA_PROPERTY_ID);

        if (TextUtils.isEmpty(otherUserId)) {
            finish();
            return;
        }

        conversationId = buildConversationId(currentUserId, otherUserId);

        bindViews();
        setupHeader();
        setupRecycler();
        setupSend();

        listenForMessages();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener != null) {
            messageListener.remove();
        }
    }

    private void bindViews() {
        btnBack         = findViewById(R.id.btnBack);
        btnSend         = findViewById(R.id.btnSend);
        imgOtherProfile = findViewById(R.id.imgOtherProfile);
        tvOtherName     = findViewById(R.id.tvOtherName);
        etMessage       = findViewById(R.id.etMessage);
        rvMessages      = findViewById(R.id.rvMessages);

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupHeader() {
        tvOtherName.setText(
                !TextUtils.isEmpty(otherUserName)
                        ? otherUserName
                        : "Conversation"
        );

        if (!TextUtils.isEmpty(otherPhotoUrl)) {
            Glide.with(this)
                    .load(otherPhotoUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(imgOtherProfile);
        } else {
            imgOtherProfile.setImageResource(R.drawable.ic_profile);
        }
    }

    private void setupRecycler() {
        adapter = new MessageAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true); // start list at bottom like real chat apps
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(adapter);
    }

    private void setupSend() {
        // Send button
        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            sendMessage(text);
        });

        // Enter key (IME action Send)
        etMessage.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            boolean handled = false;

            if (actionId == EditorInfo.IME_ACTION_SEND) {
                String text = etMessage.getText().toString().trim();
                if (!TextUtils.isEmpty(text)) {
                    sendMessage(text);
                }
                handled = true;
            } else if (event != null &&
                    event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                    event.getAction() == KeyEvent.ACTION_DOWN) {
                String text = etMessage.getText().toString().trim();
                if (!TextUtils.isEmpty(text)) {
                    sendMessage(text);
                }
                handled = true;
            }

            return handled;
        });
    }

    private void listenForMessages() {
        DocumentReference convoRef = db.collection("conversations")
                .document(conversationId);

        messageListener = convoRef
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((qs, e) -> {
                    if (e != null || qs == null) return;

                    List<Message> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : qs) {
                        Message m = d.toObject(Message.class);
                        if (m == null) continue;
                        m.id = d.getId();
                        list.add(m);
                    }
                    adapter.setMessages(list);
                    rvMessages.scrollToPosition(Math.max(list.size() - 1, 0));
                });
    }

    private void sendMessage(String text) {
        // Clear input immediately for snappy UX
        etMessage.setText("");

        DocumentReference convoRef = db.collection("conversations")
                .document(conversationId);

        // Build message object
        Message msg = new Message(
                text,
                currentUserId,
                otherUserId,
                Timestamp.now()
        );

        // Ensure conversation doc exists + update lastMessage
        HashMap<String, Object> convoData = new HashMap<>();
        convoData.put("participants", Arrays.asList(currentUserId, otherUserId));
        convoData.put("lastMessageText", text);
        convoData.put("lastMessageAt", msg.createdAt);
        convoData.put("lastSenderId", currentUserId);
        convoData.put("propertyId", propertyId); // can be null, that's okay

        convoRef.set(convoData, SetOptions.merge());

        // Add message in subcollection
        convoRef.collection("messages")
                .add(msg)
                .addOnFailureListener(e -> {
                    // Optional: show a Toast if you want to notify user of failure
                    // Toast.makeText(this, "Failed to send: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Stable conversation id so both sides use same doc:
     * sort the two ids and join with "_"
     */
    public static String buildConversationId(String uid1, String uid2) {
        List<String> list = new ArrayList<>();
        list.add(uid1);
        list.add(uid2);
        Collections.sort(list);
        return list.get(0) + "_" + list.get(1);
    }
}
