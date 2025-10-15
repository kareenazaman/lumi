package com.example.lumiapp;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView Adapter for the "Recent" section on the PM dashboard.
 * Expects a row layout named item_recent.xml with TextViews: tvName, tvAddress, tvAmount, tvStatus.
 */
public class PMDashboardRecentItems_Adapter
        extends RecyclerView.Adapter<PMDashboardRecentItems_Adapter.VH> {

    // Optional click listener for rows.
    public interface OnItemClickListener {
        void onItemClick(PMDashboardRecentItems item);
    }

    private final List<PMDashboardRecentItems> items;
    private final OnItemClickListener listener;

    public PMDashboardRecentItems_Adapter(List<PMDashboardRecentItems> items,
                                          OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    /** Simple ViewHolder caches view references for performance. */
    static class VH extends RecyclerView.ViewHolder {
        final View root;
        final TextView tvName, tvAddress, tvAmount, tvStatus;

        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView;
            tvName = itemView.findViewById(R.id.tvName);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate one row from item_recent.xml
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PMDashboardRecentItems it = items.get(position);

        // Bind basic fields
        h.tvName.setText(it.name);
        h.tvAddress.setText(it.address);
        h.tvStatus.setText(it.status);

        // Amount is optional. Hide it if null/blank to match non-payment rows.
        if (it.amount == null || it.amount.trim().isEmpty()) {
            h.tvAmount.setVisibility(View.GONE);
        } else {
            h.tvAmount.setVisibility(View.VISIBLE);
            h.tvAmount.setText(it.amount);
        }

        // Row click handler (optional)
        h.root.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(it);
        });
    }

    @Override
    public int getItemCount() {
        return (items == null) ? 0 : items.size();
    }
}
