package com.example.lumiapp;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class PMPropertyAdapter extends RecyclerView.Adapter<PMPropertyAdapter.PropertyViewHolder> {

    public interface OnPropertyClickListener {
        void onPropertyClick(Property property);
    }

    private final Context context;
    private final OnPropertyClickListener listener;
    private final List<Property> propertyList = new ArrayList<>();

    public PMPropertyAdapter(Context context, OnPropertyClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(List<Property> newList) {
        propertyList.clear();
        propertyList.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PropertyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pm_property, parent, false);
        return new PropertyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PropertyViewHolder holder, int position) {
        Property property = propertyList.get(position);

        holder.tvName.setText(property.getName());
        holder.tvAddress.setText(property.getAddress());

        if (!TextUtils.isEmpty(property.getImageUrl())) {
            Glide.with(context)
                    .load(property.getImageUrl())
                    .centerCrop()
                    .placeholder(R.drawable.ic_property_placeholder)
                    .into(holder.imgProperty);
        } else {
            holder.imgProperty.setImageResource(R.drawable.ic_property_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPropertyClick(property);
            }
        });
    }

    @Override
    public int getItemCount() {
        return propertyList.size();
    }

    static class PropertyViewHolder extends RecyclerView.ViewHolder {

        ImageView imgProperty;
        TextView tvName, tvAddress;

        public PropertyViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProperty = itemView.findViewById(R.id.imgProperty);
            tvName = itemView.findViewById(R.id.tvPropertyName);
            tvAddress = itemView.findViewById(R.id.tvPropertyAddress);
        }
    }
}
