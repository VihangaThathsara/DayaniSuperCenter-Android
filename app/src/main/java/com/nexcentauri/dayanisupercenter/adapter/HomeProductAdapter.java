package com.nexcentauri.dayanisupercenter.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.model.HomeProduct;
import com.nexcentauri.dayanisupercenter.model.SavedProductItem;
import com.nexcentauri.dayanisupercenter.util.CartWishlistStorage;
import com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class HomeProductAdapter extends RecyclerView.Adapter<HomeProductAdapter.ViewHolder> {
    public interface OnProductClickListener {
        void onProductClick(HomeProduct product);
    }

    private final List<HomeProduct> products = new ArrayList<>();
    private final DecimalFormat ratingFormat = new DecimalFormat("0.0");
    private final NumberFormat soldFormat = NumberFormat.getNumberInstance(Locale.US);
    private final OnProductClickListener listener;
    private static final String PAYLOAD_WISHLIST = "wishlist";
    private Set<String> wishlistIds;

    public HomeProductAdapter(List<HomeProduct> initialProducts) {
        this(initialProducts, null);
    }

    public HomeProductAdapter(List<HomeProduct> initialProducts, OnProductClickListener listener) {
        this.listener = listener;
        updateProducts(initialProducts);
    }

    public void updateProducts(List<HomeProduct> updatedProducts) {
        List<HomeProduct> next = updatedProducts == null ? new ArrayList<>() : new ArrayList<>(updatedProducts);
        boolean sameOrder = products.size() == next.size();
        for (int i = 0; sameOrder && i < next.size(); i++) {
            sameOrder = Objects.equals(products.get(i).getId(), next.get(i).getId());
        }
        List<Integer> changed = new ArrayList<>();
        if (sameOrder) {
            for (int i = 0; i < next.size(); i++) {
                if (!sameProduct(products.get(i), next.get(i))) changed.add(i);
            }
        }
        products.clear();
        products.addAll(next);
        if (sameOrder) {
            for (int index : changed) notifyItemChanged(index);
        } else {
            notifyDataSetChanged();
        }
    }

    private boolean sameProduct(HomeProduct first, HomeProduct second) {
        return Objects.equals(first.getName(), second.getName())
                && Objects.equals(first.getImageUrl(), second.getImageUrl())
                && Objects.equals(first.getCategoryId(), second.getCategoryId())
                && Objects.equals(first.getCategoryName(), second.getCategoryName())
                && Double.compare(first.getRating(), second.getRating()) == 0
                && first.getSoldCount() == second.getSoldCount()
                && first.getPrice() == second.getPrice()
                && first.isMostPopular() == second.isMostPopular()
                && first.isActive() == second.isActive();
    }

    public void refreshWishlist(Context context) {
        Set<String> previous = wishlistIds;
        wishlistIds = readWishlistIds(context);
        for (int i = 0; i < products.size(); i++) {
            String id = wishlistKey(products.get(i).getId());
            if (previous == null || previous.contains(id) != wishlistIds.contains(id)) {
                notifyItemChanged(i, PAYLOAD_WISHLIST);
            }
        }
    }

    private Set<String> readWishlistIds(Context context) {
        Set<String> ids = new HashSet<>();
        for (SavedProductItem item : CartWishlistStorage.getWishlistItems(context)) {
            ids.add(wishlistKey(item.getProductId()));
        }
        return ids;
    }

    private String wishlistKey(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    private void bindWishlist(ViewHolder holder, HomeProduct product) {
        if (wishlistIds == null) wishlistIds = readWishlistIds(holder.itemView.getContext());
        boolean inWishlist = wishlistIds.contains(wishlistKey(product.getId()));
        holder.imgWish.setImageResource(inWishlist ? R.drawable.ic_heart_fill : R.drawable.ic_heart);
        holder.imgWish.setBackgroundResource(inWishlist ? R.drawable.bg_product_wish_circle_selected : R.drawable.bg_product_wish_circle_unselected);
        holder.imgWish.setColorFilter(holder.itemView.getContext().getColor(android.R.color.white));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HomeProduct product = products.get(position);
        FirebaseImageLoader.loadThumbnail(holder.imgProduct, product.getImageUrl(), R.drawable.logo_clean, FirebaseImageLoader.PRODUCT_THUMB_SIZE);
        holder.txtProductName.setText(product.getName());
        bindWishlist(holder, product);
        holder.txtRating.setText(product.getRating() > 0 ? ratingFormat.format(product.getRating()) : "—");
        holder.txtSold.setText(soldFormat.format(product.getSoldCount()) + " sold");
        holder.txtPrice.setText("Rs. " + soldFormat.format(product.getPrice()));
        holder.imgWish.setOnClickListener(v -> {
            boolean wasSaved = CartWishlistStorage.isInWishlist(holder.itemView.getContext(), product.getId());
            CartWishlistStorage.toggleWishlist(holder.itemView.getContext(),
                    new com.nexcentauri.dayanisupercenter.model.SavedProductItem(
                            product.getId(),
                            product.getName(),
                            product.getImageUrl(),
                            product.getPrice(),
                            1,
                            product.getRating(),
                            product.getSoldCount()));
            refreshWishlist(holder.itemView.getContext());
            if (!wasSaved) com.nexcentauri.dayanisupercenter.util.AppMessageUtil.showFromContext(holder.itemView.getContext(),
                    "Product added to wishlist successfully.", com.nexcentauri.dayanisupercenter.util.AppMessageUtil.TYPE_SUCCESS);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductClick(product);
            }
        });
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.stream().allMatch(PAYLOAD_WISHLIST::equals)) {
            bindWishlist(holder, products.get(position));
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView txtProductName;
        ImageView imgWish;
        TextView txtRating;
        TextView txtSold;
        TextView txtPrice;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProduct = itemView.findViewById(R.id.imgProduct);
            txtProductName = itemView.findViewById(R.id.txtProductName);
            imgWish = itemView.findViewById(R.id.imgWish);
            txtRating = itemView.findViewById(R.id.txtRating);
            txtSold = itemView.findViewById(R.id.txtSold);
            txtPrice = itemView.findViewById(R.id.txtPrice);
        }
    }
}
