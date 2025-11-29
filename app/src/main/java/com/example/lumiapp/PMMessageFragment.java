package com.example.lumiapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PMMessageFragment extends Fragment {

    private RecyclerView rvConversations;
    private MaterialButton btnOpenContacts;
    private TextView tvEmptyState;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String currentUserId;
    private ListenerRegistration convoReg;

    private ConversationAdapter adapter;

    public PMMessageFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_pm_message, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        rvConversations = view.findViewById(R.id.rvConversations);
        btnOpenContacts = view.findViewById(R.id.btnOpenContacts);
        tvEmptyState    = view.findViewById(R.id.tvEmptyState);

        rvConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ConversationAdapter();
        rvConversations.setAdapter(adapter);

        btnOpenContacts.setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), ContactList.class);
            i.putExtra("role", "manager");
            startActivity(i);
        });

        if (auth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUserId = auth.getCurrentUser().getUid();

        listenForConversations();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (convoReg != null) {
            convoReg.remove();
            convoReg = null;
        }
    }

    /**
     * Realtime listener for conversations where this manager is a participant.
     * Only shows tiles once there is at least 1 message.
     */
    private void listenForConversations() {
        convoReg = db.collection("conversations")
                .whereArrayContains("participants", currentUserId)
                .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                .addSnapshotListener((qs, e) -> {
                    if (!isAdded()) return;
                    if (e != null) {
                        Toast.makeText(requireContext(),
                                "Failed to load messages", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (qs == null || qs.isEmpty()) {
                        adapter.submitList(new ArrayList<>());
                        tvEmptyState.setVisibility(View.VISIBLE);
                        return;
                    }

                    List<ConversationTile> tiles = new ArrayList<>();
                    Set<String> otherUserIds = new HashSet<>();

                    for (QueryDocumentSnapshot d : qs) {
                        List<String> participants =
                                (List<String>) d.get("participants");

                        if (participants == null || participants.size() < 2) continue;

                        String otherId = null;
                        for (String p : participants) {
                            if (!TextUtils.equals(p, currentUserId)) {
                                otherId = p;
                                break;
                            }
                        }
                        if (TextUtils.isEmpty(otherId)) continue;

                        String convoId      = d.getId();
                        String lastText     = d.getString("lastMessageText");
                        Timestamp lastAt    = d.getTimestamp("lastMessageAt");

                        ConversationTile tile = new ConversationTile();
                        tile.conversationId   = convoId;
                        tile.otherUserId      = otherId;
                        tile.lastMessageText  = lastText;
                        tile.lastMessageAt    = lastAt;

                        tiles.add(tile);
                        otherUserIds.add(otherId);
                    }

                    if (tiles.isEmpty()) {
                        adapter.submitList(new ArrayList<>());
                        tvEmptyState.setVisibility(View.VISIBLE);
                        return;
                    }

                    // Fetch user docs for all "other" participants to get name + avatar
                    fetchOtherUsersAndBind(tiles, otherUserIds);
                });
    }

    private void fetchOtherUsersAndBind(List<ConversationTile> tiles, Set<String> otherUserIds) {
        if (otherUserIds.isEmpty()) {
            // nothing to enrich
            adapter.submitList(tiles);
            tvEmptyState.setVisibility(tiles.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }

        // NOTE: whereIn max 10 ids → in real app, batch if needed. For now we assume <= 10.
        db.collection("users")
                .whereIn(FieldPath.documentId(), new ArrayList<>(otherUserIds))
                .get()
                .addOnSuccessListener(qs -> {
                    Map<String, DocumentSnapshot> map = new HashMap<>();
                    for (DocumentSnapshot doc : qs) {
                        map.put(doc.getId(), doc);
                    }

                    for (ConversationTile tile : tiles) {
                        DocumentSnapshot userDoc = map.get(tile.otherUserId);
                        if (userDoc != null && userDoc.exists()) {
                            tile.otherUserName     = userDoc.getString("name");
                            tile.otherUserPhotoUrl = userDoc.getString("profileImageUrl");
                        }
                    }

                    adapter.submitList(tiles);
                    tvEmptyState.setVisibility(tiles.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    // Even if this fails, show tiles with unknown names
                    adapter.submitList(tiles);
                    tvEmptyState.setVisibility(tiles.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    // ─────────────────────────────────────────────
    //  Model for one conversation tile
    // ─────────────────────────────────────────────
    static class ConversationTile {
        String conversationId;
        String otherUserId;
        String otherUserName;
        String otherUserPhotoUrl;
        String lastMessageText;
        Timestamp lastMessageAt;
    }

    // ─────────────────────────────────────────────
    //  RecyclerView Adapter for conversation tiles
    // ─────────────────────────────────────────────
    class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConvoVH> {

        private final List<ConversationTile> items = new ArrayList<>();
        private final DateFormat timeFormat =
                android.text.format.DateFormat.getTimeFormat(getContext());

        void submitList(List<ConversationTile> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ConvoVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_conversation_tile, parent, false);
            return new ConvoVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ConvoVH holder, int position) {
            ConversationTile tile = items.get(position);
            holder.bind(tile);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ConvoVH extends RecyclerView.ViewHolder {
            ImageView imgAvatar;
            TextView tvName;
            TextView tvLastMessage;
            TextView tvTime;

            ConvoVH(@NonNull View itemView) {
                super(itemView);
                imgAvatar     = itemView.findViewById(R.id.imgAvatar);
                tvName        = itemView.findViewById(R.id.tvName);
                tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
                tvTime        = itemView.findViewById(R.id.tvTime);
            }

            void bind(ConversationTile tile) {
                String name = !TextUtils.isEmpty(tile.otherUserName)
                        ? tile.otherUserName
                        : "Unknown user";
                tvName.setText(name);

                String lastText = !TextUtils.isEmpty(tile.lastMessageText)
                        ? tile.lastMessageText
                        : "(no messages yet)";
                tvLastMessage.setText(lastText);

                if (tile.lastMessageAt != null) {
                    tvTime.setText(timeFormat.format(tile.lastMessageAt.toDate()));
                } else {
                    tvTime.setText("");
                }

                if (!TextUtils.isEmpty(tile.otherUserPhotoUrl)) {
                    Glide.with(itemView.getContext())
                            .load(tile.otherUserPhotoUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_profile)
                            .into(imgAvatar);
                } else {
                    imgAvatar.setImageResource(R.drawable.ic_profile);
                }

                itemView.setOnClickListener(v -> {
                    // Open ChatActivity with the other user
                    Intent i = new Intent(requireContext(), ChatActivity.class);
                    i.putExtra(ChatActivity.EXTRA_OTHER_USER_ID,   tile.otherUserId);
                    i.putExtra(ChatActivity.EXTRA_OTHER_USER_NAME, name);
                    i.putExtra(ChatActivity.EXTRA_OTHER_PHOTO_URL, tile.otherUserPhotoUrl);
                    // propertyId is optional here → chat can still work without it
                    startActivity(i);
                });
            }
        }
    }
}
