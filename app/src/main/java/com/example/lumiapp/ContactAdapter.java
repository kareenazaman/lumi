package com.example.lumiapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnContactClickListener {
        void onContactClick(Contact contact);
    }

    private static final int VIEW_TYPE_HEADER  = ContactListItem.TYPE_HEADER;
    private static final int VIEW_TYPE_CONTACT = ContactListItem.TYPE_CONTACT;

    private final List<ContactListItem> items = new ArrayList<>();
    private final OnContactClickListener listener;

    public ContactAdapter(OnContactClickListener listener) {
        this.listener = listener;
    }

    // Update the list shown in the RecyclerView
    public void submitList(List<ContactListItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_HEADER) {
            // Header row → uses item_contact_header.xml
            View v = inflater.inflate(R.layout.item_contact_header, parent, false);
            return new HeaderVH(v);
        } else {
            // Contact row → uses item_contact.xml ✅
            View v = inflater.inflate(R.layout.item_contact, parent, false);
            return new ContactVH(v);
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position
    ) {
        ContactListItem item = items.get(position);

        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).bind(item.getHeaderTitle());
        } else if (holder instanceof ContactVH) {
            ((ContactVH) holder).bind(item.getContact(), listener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ===================== ViewHolders =====================

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvHeader;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvHeader);
        }

        void bind(String title) {
            tvHeader.setText(title);
        }
    }

    static class ContactVH extends RecyclerView.ViewHolder {
        TextView tvName, tvProperty, tvPhone;
        ImageView imgType;

        ContactVH(@NonNull View itemView) {
            super(itemView);
            tvName     = itemView.findViewById(R.id.tvName);
            tvProperty = itemView.findViewById(R.id.tvProperty);
            tvPhone    = itemView.findViewById(R.id.tvPhone);
            imgType    = itemView.findViewById(R.id.imgType);
        }

        void bind(final Contact contact, final OnContactClickListener listener) {
            if (contact == null) return;

            tvName.setText(contact.getName());
            tvProperty.setText(
                    contact.getPropertyName() == null
                            ? ""
                            : contact.getPropertyName()
            );
            tvPhone.setText(
                    contact.getPhone() == null
                            ? ""
                            : contact.getPhone()
            );

            // Show badge only for custom/admin-created contacts
            if (contact.isCustom()) {
                imgType.setVisibility(View.VISIBLE);
            } else {
                imgType.setVisibility(View.INVISIBLE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactClick(contact);
                }
            });
        }
    }
}
