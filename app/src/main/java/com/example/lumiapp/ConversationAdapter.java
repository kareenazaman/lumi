package com.example.lumiapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationVH> {

    public interface OnConversationClickListener {
        void onConversationClick(ConversationItem item);
    }

    private final List<ConversationItem> items = new ArrayList<>();
    private final OnConversationClickListener listener;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ConversationItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    // Upsert (update if exists, else add)
    public void upsertItem(ConversationItem item) {
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getConversationId()
                    .equals(item.getConversationId())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            items.set(index, item);
            notifyItemChanged(index);
        } else {
            items.add(item);
            notifyItemInserted(items.size() - 1);
        }
    }

    @NonNull
    @Override
    public ConversationVH onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationVH(v);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ConversationVH holder,
            int position
    ) {
        ConversationItem item = items.get(position);
        holder.bind(item, listener, timeFormat);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ConversationVH extends RecyclerView.ViewHolder {

        ImageView imgAvatar;
        TextView tvName, tvLastMessage, tvTime;

        ConversationVH(@NonNull View itemView) {
            super(itemView);
            imgAvatar     = itemView.findViewById(R.id.imgAvatar);
            tvName        = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime        = itemView.findViewById(R.id.tvTime);
        }

        void bind(ConversationItem item,
                  OnConversationClickListener listener,
                  SimpleDateFormat timeFormat) {

            tvName.setText(
                    item.getOtherUserName() != null
                            ? item.getOtherUserName()
                            : "Conversation"
            );

            tvLastMessage.setText(
                    item.getLastMessageText() != null
                            ? item.getLastMessageText()
                            : ""
            );

            if (item.getLastMessageAt() != null) {
                String t = timeFormat.format(item.getLastMessageAt().toDate());
                tvTime.setText(t);
            } else {
                tvTime.setText("");
            }

            if (item.getOtherUserPhotoUrl() != null &&
                    !item.getOtherUserPhotoUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(item.getOtherUserPhotoUrl())
                        .centerCrop()
                        .placeholder(R.drawable.ic_profile)
                        .into(imgAvatar);
            } else {
                imgAvatar.setImageResource(R.drawable.ic_profile);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onConversationClick(item);
            });
        }
    }
}
