package com.nexcentauri.dayanisupercenter.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.model.OrderItem;
import com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrackOrderPagerAdapter extends RecyclerView.Adapter<TrackOrderPagerAdapter.TrackItemHolder> {

    private final List<OrderItem> items = new ArrayList<>();
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
    private int totalItems;

    public void submitList(List<OrderItem> list, int totalItems) {
        this.totalItems = Math.max(1, totalItems);
        items.clear();
        if (list != null) items.addAll(list);
        if (items.isEmpty()) {
            items.add(new OrderItem("", "Order item", "", 0L, 1));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TrackItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track_order_product, parent, false);
        return new TrackItemHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackItemHolder holder, int position) {
        OrderItem item = items.get(position);
        FirebaseImageLoader.loadInto(holder.imgProduct, item.getImageUrl(), R.drawable.logo_clean);
        holder.txtName.setText(item.getName());

        int quantity = Math.max(1, item.getQuantity());
        String itemsText = totalItems == 1 ? "1 Item" : totalItems + " Items";
        String quantityText = "Qty : " + quantity;
        holder.txtQtyItems.setText(itemsText + " | " + quantityText);
        holder.txtPrice.setText("Rs. " + numberFormat.format(item.getSubtotal()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TrackItemHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView txtName, txtQtyItems, txtPrice;

        TrackItemHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgTrackProduct);
            txtName = itemView.findViewById(R.id.txtTrackProductName);
            txtQtyItems = itemView.findViewById(R.id.txtTrackQtyItems);
            txtPrice = itemView.findViewById(R.id.txtTrackPrice);
        }
    }
}
