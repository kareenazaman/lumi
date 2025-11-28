// app/src/main/java/com/example/lumiapp/FixRequestAdapter.java
package com.example.lumiapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Locale;

public class FixRequestAdapter extends RecyclerView.Adapter<FixRequestAdapter.VH> {

    public interface OnItemClick {
        void onClick(FixRequest f);
    }

    private final ArrayList<FixRequest> items = new ArrayList<>();
    private final OnItemClick click;

    public FixRequestAdapter(OnItemClick click) {
        this.click = click;
    }

    public void setItems(@NonNull java.util.List<FixRequest> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.item_complaint, parent, false); // reuse same row layout
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FixRequest f = items.get(position);

        // Title: "Fix Request #ABC123"
        String shortId = f.shortId;
        if ((shortId == null || shortId.isEmpty()) && f.id != null && f.id.length() >= 6) {
            shortId = f.id.substring(0, 6).toUpperCase(Locale.US);
        }
        h.tvTitle.setText("Fix Request #" + (shortId != null ? shortId : "—"));

        // Date
        h.tvDate.setText(f.createdDate != null ? f.createdDate : "—");

        // Room chip
        h.tvRoomChip.setText(f.roomNumber != null ? f.roomNumber : "—");

        // Property
        h.tvProperty.setText(f.propertyAddress != null ? f.propertyAddress : "—");

        // Status text + background pill
        String st = f.status != null ? f.status.toLowerCase(Locale.US) : "open";
        h.tvStatus.setText(st);
        if ("closed".equals(st)) {
            h.tvStatus.setBackgroundResource(R.drawable.bg_status_closed);
        } else if ("pending".equals(st)) {
            h.tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
        } else {
            h.tvStatus.setBackgroundResource(R.drawable.bg_status_open);
        }

        // Thumbnail image
        if (f.imageUrl != null && !f.imageUrl.isEmpty()) {
            h.ivComplaintImage.setVisibility(View.VISIBLE);
            Glide.with(h.itemView.getContext())
                    .load(f.imageUrl)
                    .centerCrop()
                    .into(h.ivComplaintImage);
        } else {
            h.ivComplaintImage.setVisibility(View.GONE);
        }

        // Row click
        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(f);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvRoomChip, tvStatus, tvProperty;
        ImageView ivComplaintImage;

        VH(@NonNull View v) {
            super(v);
            tvTitle          = v.findViewById(R.id.tvTitle);
            tvDate           = v.findViewById(R.id.tvDate);
            tvRoomChip       = v.findViewById(R.id.tvRoomChip);
            tvStatus         = v.findViewById(R.id.tvStatus);
            tvProperty       = v.findViewById(R.id.tvProperty);
            ivComplaintImage = v.findViewById(R.id.ivComplaintImage);
        }
    }
}
