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
import com.nexcentauri.dayanisupercenter.model.UserOrder;
import com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.OrderViewHolder> {

    public interface Listener {
        void onOrderClick(UserOrder order);
    }

    private final List<UserOrder> items = new ArrayList<>();
    private final Listener listener;
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    public OrdersAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<UserOrder> orders) {
        items.clear();
        if (orders != null) items.addAll(orders);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_card, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        UserOrder order = items.get(position);
        OrderItem preview = order.getPreviewItem();
        FirebaseImageLoader.loadInto(holder.imgProduct, preview.getImageUrl(), R.drawable.logo_clean);
        holder.txtName.setText(preview.getName());

        int productTypeCount = order.getProductTypeCount();
        int quantityCount = order.getItemCount();
        String itemsText = productTypeCount == 1 ? "1 Item" : productTypeCount + " Items";
        String qtyText = quantityCount == 1 ? "Qty : 1" : "Qty : " + quantityCount;
        holder.txtCount.setText(itemsText + " | " + qtyText);

        holder.txtPrice.setText("Rs. " + numberFormat.format(order.getTotal()));
        holder.txtStatus.setText(UserOrder.getDisplayStatus(order.getStatus()));
        holder.btnTrackOrder.setText(order.isCompleted() ? "View Order" : "Track Order");
        holder.btnTrackOrder.setOnClickListener(v -> {
            if (listener != null) listener.onOrderClick(order);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView txtName, txtCount, txtPrice, txtStatus;
        com.google.android.material.button.MaterialButton btnTrackOrder;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgOrderProduct);
            txtName = itemView.findViewById(R.id.txtOrderProductName);
            txtCount = itemView.findViewById(R.id.txtOrderItemCount);
            txtPrice = itemView.findViewById(R.id.txtOrderPrice);
            txtStatus = itemView.findViewById(R.id.txtOrderDeliveryStatus);
            btnTrackOrder = itemView.findViewById(R.id.btnTrackOrder);
        }
    }
}
