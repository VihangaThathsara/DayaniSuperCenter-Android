package com.nexcentauri.dayanisupercenter.util;

import android.content.Context;
import android.app.Activity;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.ImageView;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.model.HomeProduct;
import com.nexcentauri.dayanisupercenter.model.HomeCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class FirebaseImageLoader {
    public interface UrlCallback {
        void onResolved(@NonNull String resolvedUrl);
        void onFailed();
    }
    private static final Map<String, String> RESOLVED_URL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<UrlCallback>> PENDING = new HashMap<>();
    private static Context applicationContext;
    public static final int PRODUCT_THUMB_SIZE = 360;
    public static final int CATEGORY_THUMB_SIZE = 144;
    private static final class ImageRequest {
        final String key;
        final int size;
        boolean failed;
        boolean ready;
        ImageRequest(String key, int size) { this.key = key; this.size = size; }
    }
    private FirebaseImageLoader() {}

    public static void loadInto(@NonNull ImageView imageView, String source, @DrawableRes int placeholderRes) {
        loadThumbnail(imageView, source, placeholderRes, 0);
    }

    public static void loadThumbnail(@NonNull ImageView imageView, String source, @DrawableRes int placeholderRes, int size) {
        if (isClosed(imageView.getContext())) return;
        applicationContext = imageView.getContext().getApplicationContext();
        String key = FirebaseAuth.getInstance().getUid() + ":" + source + ":" + size + ":" + placeholderRes;
        Object previous = imageView.getTag(R.id.tag_image_request);
        if (previous instanceof ImageRequest && key.equals(((ImageRequest) previous).key)
                && !((ImageRequest) previous).failed) return;
        ImageRequest request = new ImageRequest(key, size);
        imageView.setTag(R.id.tag_image_request, request);
        Glide.with(imageView).clear(imageView);
        imageView.setImageResource(placeholderRes);
        resolve(source, false, new UrlCallback() {
            @Override public void onResolved(@NonNull String url) {
                if (imageView.getTag(R.id.tag_image_request) == request) display(imageView, url, placeholderRes, request, true);
            }
            @Override public void onFailed() { request.failed = true; request.ready = true; }
        });
    }

    private static void display(ImageView view, String url, int placeholder, ImageRequest request, boolean mayRefresh) {
        if (view.getTag(R.id.tag_image_request) != request || isClosed(view.getContext())) return;
        request.failed = false;
        request.ready = false;
        RequestBuilder<Drawable> builder = Glide.with(view).load(url).diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate();
        if (request.size > 0) builder.override(request.size, request.size).fitCenter();
        builder
                .placeholder(placeholder).error(placeholder)
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(@Nullable GlideException error, Object model,
                                                          Target<Drawable> target, boolean first) {
                        request.failed = true;
                        if (mayRefresh && isFirebaseUrl(url)) view.post(() -> resolve(url, true, new UrlCallback() {
                            @Override public void onResolved(@NonNull String fresh) {
                                if (view.getTag(R.id.tag_image_request) != request) return;
                                if (!fresh.equals(url)) display(view, fresh, placeholder, request, false);
                                else request.ready = true;
                            }
                            @Override public void onFailed() { request.ready = true; }
                        }));
                        else request.ready = true;
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                                             DataSource source, boolean first) { request.ready = true; return false; }
                }).into(view);
    }

    public static boolean isReady(ImageView view) {
        Object request = view.getTag(R.id.tag_image_request);
        return request instanceof ImageRequest && ((ImageRequest) request).ready;
    }

    public static Runnable prepareCatalog(Context context, List<HomeCategory> categories, List<HomeProduct> products, Runnable ready) {
        applicationContext = context.getApplicationContext();
        Map<String, CatalogImage> images = new LinkedHashMap<>();
        for (HomeCategory category : categories) addCatalogImage(images, category.getImageUrl(), CATEGORY_THUMB_SIZE);
        for (HomeProduct product : products) addCatalogImage(images, product.getImageUrl(), PRODUCT_THUMB_SIZE);
        CatalogBatch batch = new CatalogBatch(applicationContext, new ArrayList<>(images.values()), ready);
        batch.pump();
        return batch::cancel;
    }

    private static void addCatalogImage(Map<String, CatalogImage> images, String source, int size) {
        if (!TextUtils.isEmpty(source)) images.put(size + ":" + source, new CatalogImage(source, size));
    }

    private static final class CatalogImage {
        final String source;
        final int size;
        boolean done;
        CatalogImage(String source, int size) { this.source = source; this.size = size; }
    }

    private static final class CatalogBatch {
        final Context context;
        final List<CatalogImage> images;
        final List<Target<Drawable>> targets = new ArrayList<>();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable ready;
        boolean cancelled;
        int next;
        int running;
        CatalogBatch(Context context, List<CatalogImage> images, Runnable ready) {
            this.context = context; this.images = images; this.ready = ready;
        }
        void pump() {
            if (cancelled) return;
            while (running < 4 && next < images.size()) {
                CatalogImage image = images.get(next++);
                running++;
                resolve(image.source, false, new UrlCallback() {
                    @Override public void onResolved(@NonNull String url) { load(image, url, true); }
                    @Override public void onFailed() { finish(image); }
                });
            }
            if (running == 0 && next == images.size()) {
                cancelled = true;
                handler.post(ready);
            }
        }
        void load(CatalogImage image, String url, boolean mayRefresh) {
            if (cancelled) return;
            Target<Drawable> target = Glide.with(context).load(url).diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(image.size, image.size).fitCenter().dontAnimate()
                    .listener(new RequestListener<Drawable>() {
                        @Override public boolean onResourceReady(Drawable value, Object model, Target<Drawable> target, DataSource source, boolean first) {
                            handler.post(() -> { release(target); finish(image); });
                            return false;
                        }
                        @Override public boolean onLoadFailed(@Nullable GlideException error, Object model, Target<Drawable> target, boolean first) {
                            handler.post(() -> {
                                release(target);
                                if (cancelled) return;
                                if (mayRefresh && isFirebaseUrl(url)) resolve(url, true, new UrlCallback() {
                                    @Override public void onResolved(@NonNull String fresh) {
                                        if (!fresh.equals(url)) load(image, fresh, false); else finish(image);
                                    }
                                    @Override public void onFailed() { finish(image); }
                                });
                                else finish(image);
                            });
                            return false;
                        }
                    }).preload(image.size, image.size);
            targets.add(target);
        }
        void release(Target<Drawable> target) { targets.remove(target); Glide.with(context).clear(target); }
        void finish(CatalogImage image) {
            if (cancelled || image.done) return;
            image.done = true; running--; pump();
        }
        void cancel() {
            cancelled = true;
            handler.removeCallbacks(ready);
            for (Target<Drawable> target : new ArrayList<>(targets)) release(target);
        }
    }

    public static void prefetch(Context context, List<HomeProduct> products) {
        applicationContext = context.getApplicationContext();
        for (int i = 0; i < Math.min(products.size(), 12); i++) {
            prefetchThumbnail(context, products.get(i).getImageUrl(), PRODUCT_THUMB_SIZE);
        }
    }

    public static void prefetchCategories(Context context, List<HomeCategory> categories) {
        applicationContext = context.getApplicationContext();
        for (int i = 0; i < Math.min(categories.size(), 16); i++) {
            prefetchThumbnail(context, categories.get(i).getImageUrl(), CATEGORY_THUMB_SIZE);
        }
    }

    private static void prefetchThumbnail(Context context, String source, int size) {
        resolveToDownloadUrl(source, new UrlCallback() {
                @Override public void onResolved(@NonNull String url) {
                    Glide.with(context.getApplicationContext()).load(url).diskCacheStrategy(DiskCacheStrategy.ALL)
                            .override(size, size).fitCenter().priority(Priority.LOW).preload(size, size);
                }
                @Override public void onFailed() { }
            });
    }

    public static void resolveToDownloadUrl(String source, @NonNull UrlCallback callback) { resolve(source, false, callback); }

    private static void resolve(String source, boolean refresh, UrlCallback callback) {
        if (TextUtils.isEmpty(source)) { callback.onFailed(); return; }
        String normalized = source.trim();
        String uid = FirebaseAuth.getInstance().getUid();
        String key = (uid == null ? "guest" : uid) + ":" + normalized;
        SharedPreferences prefs = applicationContext == null ? null : applicationContext.getSharedPreferences("dayani_image_urls_" + (uid == null ? "guest" : uid), Context.MODE_PRIVATE);
        if (!refresh) {
            String cached = RESOLVED_URL_CACHE.get(key);
            if (cached == null && prefs != null && System.currentTimeMillis() - prefs.getLong(normalized + ":at", 0L) < 86400000L) cached = prefs.getString(normalized, "");
            if (!TextUtils.isEmpty(cached)) { RESOLVED_URL_CACHE.put(key, cached); callback.onResolved(cached); return; }
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) { callback.onResolved(normalized); return; }
        }
        synchronized (PENDING) {
            if (PENDING.containsKey(key)) { PENDING.get(key).add(callback); return; }
            List<UrlCallback> callbacks = new ArrayList<>(); callbacks.add(callback); PENDING.put(key, callbacks);
        }
        try {
            StorageReference reference = normalized.startsWith("gs://") || isFirebaseUrl(normalized)
                    ? FirebaseStorage.getInstance().getReferenceFromUrl(normalized)
                    : FirebaseStorage.getInstance().getReference().child(normalizePath(normalized));
            reference.getDownloadUrl().addOnSuccessListener(uri -> {
                String url = uri.toString(); RESOLVED_URL_CACHE.put(key, url);
                if (prefs != null) prefs.edit().putString(normalized, url).putLong(normalized + ":at", System.currentTimeMillis()).apply();
                complete(key, url);
            }).addOnFailureListener(error -> complete(key, null));
        } catch (Exception error) { complete(key, null); }
    }

    private static void complete(String key, String url) {
        List<UrlCallback> callbacks;
        synchronized (PENDING) { callbacks = PENDING.remove(key); }
        if (callbacks == null) return;
        for (UrlCallback callback : callbacks) {
            if (url == null) callback.onFailed(); else callback.onResolved(url);
        }
    }
    private static boolean isFirebaseUrl(String value) { return "firebasestorage.googleapis.com".equals(Uri.parse(value).getHost()); }
    private static boolean isClosed(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return ((Activity) context).isFinishing() || ((Activity) context).isDestroyed();
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return false;
    }
    private static String normalizePath(String value) { while (value.startsWith("/")) value = value.substring(1); return value; }
}
