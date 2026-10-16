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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

    public interface Listener {
        void onIncrease(SavedProductItem item);
        void onDecrease(SavedProductItem item);
        void onRemove(SavedProductItem item);
    }

    private final List<SavedProductItem> items = new ArrayList<>();
    private final Listener listener;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    public CartAdapter(Listener listener) {
        this.listener = listener;
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavedProductItem item = items.get(position);
        FirebaseImageLoader.loadInto(holder.imgProduct, item.getImageUrl(), R.drawable.logo_clean);
        holder.txtName.setText(item.getName());
        holder.txtPrice.setText("Rs. " + numberFormat.format(item.getPrice()));
        holder.txtQuantity.setText(String.valueOf(Math.max(1, item.getQuantity())));
        holder.btnMinus.setOnClickListener(v -> listener.onDecrease(item));
        holder.btnPlus.setOnClickListener(v -> listener.onIncrease(item));
        holder.btnDelete.setOnClickListener(v -> listener.onRemove(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        ImageView btnDelete;
        TextView txtName;
        TextView txtPrice;
        TextView btnMinus;
        TextView txtQuantity;
        TextView btnPlus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            txtName = itemView.findViewById(R.id.txtName);
            txtPrice = itemView.findViewById(R.id.txtPrice);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            txtQuantity = itemView.findViewById(R.id.txtQuantity);
            btnPlus = itemView.findViewById(R.id.btnPlus);
        }
    }
}
