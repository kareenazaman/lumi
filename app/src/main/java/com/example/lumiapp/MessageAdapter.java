package com.example.lumiapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private final List<Message> messages = new ArrayList<>();
    private final String currentUserId;

    public MessageAdapter() {
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
    }

    public void setMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Message m = messages.get(position);
        if (m.senderId != null && m.senderId.equals(currentUserId)) {
            return TYPE_SENT;
        }
        return TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SENT) {
            View v = inflater.inflate(R.layout.item_message_sent, parent, false);
            return new SentVH(v);
        } else {
            View v = inflater.inflate(R.layout.item_message_received, parent, false);
            return new ReceivedVH(v);
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {
        Message m = messages.get(position);
        if (holder instanceof SentVH) {
            ((SentVH) holder).bind(m);
        } else if (holder instanceof ReceivedVH) {
            ((ReceivedVH) holder).bind(m);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentVH extends RecyclerView.ViewHolder {
        TextView tvText;

        SentVH(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvMessageText);
        }

        void bind(Message m) {
            tvText.setText(m.text);
        }
    }

    static class ReceivedVH extends RecyclerView.ViewHolder {
        TextView tvText;

        ReceivedVH(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvMessageText);
        }

        void bind(Message m) {
            tvText.setText(m.text);
        }
    }
}
