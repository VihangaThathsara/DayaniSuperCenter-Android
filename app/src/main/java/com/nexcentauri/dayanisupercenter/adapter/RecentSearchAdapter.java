package com.nexcentauri.dayanisupercenter.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nexcentauri.dayanisupercenter.R;

import java.util.ArrayList;
import java.util.List;

public class RecentSearchAdapter extends RecyclerView.Adapter<RecentSearchAdapter.ViewHolder> {

    public interface Listener {
        void onRecentClicked(String keyword);
        void onRemoveClicked(String keyword);
    }

    private final List<String> items = new ArrayList<>();
    private final Listener listener;

    public RecentSearchAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<String> keywords) {
        items.clear();
        if (keywords != null) {
            items.addAll(keywords);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_search, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String keyword = items.get(position);
        holder.txtKeyword.setText(keyword);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRecentClicked(keyword);
            }
        });
        holder.txtRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveClicked(keyword);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtKeyword;
        TextView txtRemove;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtKeyword = itemView.findViewById(R.id.txtRecentKeyword);
            txtRemove = itemView.findViewById(R.id.txtRemoveRecent);
        }
    }
}
