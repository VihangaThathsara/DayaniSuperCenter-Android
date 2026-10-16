package com.nexcentauri.dayanisupercenter.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader;
import com.nexcentauri.dayanisupercenter.model.HomeCategory;

import java.util.List;

public class HomeCategoryAdapter extends RecyclerView.Adapter<HomeCategoryAdapter.ViewHolder> {
    public interface OnCategoryClickListener {
        void onCategoryClick(HomeCategory category);
    }

    private final List<HomeCategory> categories;
    private final OnCategoryClickListener listener;

    public HomeCategoryAdapter(List<HomeCategory> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HomeCategory category = categories.get(position);
        holder.txtCategory.setText(category.getName());
        FirebaseImageLoader.loadThumbnail(holder.imgCategory, category.getImageUrl(), R.drawable.logo_clean, FirebaseImageLoader.CATEGORY_THUMB_SIZE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgCategory;
        TextView txtCategory;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgCategory = itemView.findViewById(R.id.imgCategory);
            txtCategory = itemView.findViewById(R.id.txtCategory);
        }
    }
}
