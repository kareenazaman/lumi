package com.example.lumiapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

public class ComplaintAdapter extends RecyclerView.Adapter<ComplaintAdapter.VH> {

    public interface OnItemClick {
        void onClick(Complaint c);
    }

    private final ArrayList<Complaint> items = new ArrayList<>();
    private final OnItemClick click;

    public ComplaintAdapter(OnItemClick click) {
        this.click = click;
    }

    public void setItems(@NonNull java.util.List<Complaint> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.item_complaint, parent, false); // ✅ your item XML
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Complaint c = items.get(position);

        // Title: "Complain #ABC123"
        String shortId = c.shortId;
        if ((shortId == null || shortId.isEmpty()) && c.id != null && c.id.length() >= 6) {
            shortId = c.id.substring(0, 6).toUpperCase(Locale.US);
        }
        h.tvTitle.setText("Complain #" + (shortId != null ? shortId : "—"));

        // Date
        h.tvDate.setText(c.createdDate != null ? c.createdDate : "—");

        // Room chip
        h.tvRoomChip.setText(c.roomNumber != null ? c.roomNumber : "—");

        // Property (address or name you saved in propertyAddress)
        h.tvProperty.setText(c.propertyAddress != null ? c.propertyAddress : "—");

        // Status text + background pill
        String st = c.status != null ? c.status.toLowerCase(Locale.US) : "open";
        h.tvStatus.setText(st);
        if ("closed".equals(st)) {
            h.tvStatus.setBackgroundResource(R.drawable.bg_status_closed);
        } else if ("pending".equals(st)) {
            h.tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
        } else {
            h.tvStatus.setBackgroundResource(R.drawable.bg_status_open);
        }

        // Row click
        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(c);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvRoomChip, tvStatus, tvProperty;

        VH(@NonNull View v) {
            super(v);
            tvTitle     = v.findViewById(R.id.tvTitle);
            tvDate      = v.findViewById(R.id.tvDate);
            tvRoomChip  = v.findViewById(R.id.tvRoomChip);
            tvStatus    = v.findViewById(R.id.tvStatus);
            tvProperty  = v.findViewById(R.id.tvProperty);
        }
    }
}
