package com.nexcentauri.dayanisupercenter.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;
import com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WishlistAdapter extends RecyclerView.Adapter<WishlistAdapter.ViewHolder> {

    public interface Listener {
        void onRemove(SavedProductItem item);
    }

    public interface ItemClickListener {
        void onOpen(SavedProductItem item);
    }

    private final List<SavedProductItem> items = new ArrayList<>();
    private final Listener listener;
    private final ItemClickListener itemClickListener;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
    private final DecimalFormat ratingFormat = new DecimalFormat("0.0");

    public WishlistAdapter(Listener listener, ItemClickListener itemClickListener) {
        this.listener = listener;
        this.itemClickListener = itemClickListener;
    }

    public void submitList(List<SavedProductItem> updatedItems) {
        items.clear();
        if (updatedItems != null) {
            items.addAll(updatedItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wishlist_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavedProductItem item = items.get(position);
        FirebaseImageLoader.loadInto(holder.imgProduct, item.getImageUrl(), R.drawable.logo_clean);
        holder.txtName.setText(item.getName());
        holder.txtPrice.setText("Rs. " + numberFormat.format(item.getPrice()));
        holder.txtRating.setText(ratingFormat.format(item.getRating()));
        holder.txtSold.setText(numberFormat.format(item.getSoldCount()) + " sold");
        holder.btnDelete.setOnClickListener(v -> listener.onRemove(item));
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onOpen(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        ImageView btnDelete;
        TextView txtName;
        TextView txtRating;
        TextView txtSold;
        TextView txtPrice;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            txtName = itemView.findViewById(R.id.txtName);
            txtRating = itemView.findViewById(R.id.txtRating);
            txtSold = itemView.findViewById(R.id.txtSold);
            txtPrice = itemView.findViewById(R.id.txtPrice);
        }
    }
}
