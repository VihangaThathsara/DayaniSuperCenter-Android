package com.nexcentauri.dayanisupercenter.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.util.ProductSnapshotCache;
import com.nexcentauri.dayanisupercenter.util.FirebaseImageLoader;
import com.nexcentauri.dayanisupercenter.activity.NotificationActivity;
import com.nexcentauri.dayanisupercenter.activity.ProductListActivity;
import com.nexcentauri.dayanisupercenter.activity.SearchActivity;
import com.nexcentauri.dayanisupercenter.activity.SingleProductActivity;
import com.nexcentauri.dayanisupercenter.activity.WishlistActivity;
import com.nexcentauri.dayanisupercenter.adapter.HomeCategoryAdapter;
import com.nexcentauri.dayanisupercenter.adapter.HomeProductAdapter;
import com.nexcentauri.dayanisupercenter.adapter.OfferBannerAdapter;
import com.nexcentauri.dayanisupercenter.model.HomeCategory;
import com.nexcentauri.dayanisupercenter.model.HomeProduct;
import com.nexcentauri.dayanisupercenter.model.OfferBanner;
import com.nexcentauri.dayanisupercenter.util.HomeDataCache;
import com.nexcentauri.dayanisupercenter.util.ProfilePhotoRepair;
import com.nexcentauri.dayanisupercenter.util.UserProfileCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private ViewPager2 viewPagerOffers;
    private RecyclerView recyclerCategories;
    private RecyclerView recyclerProducts;
    private ImageView imgProfile;
    private TextView txtName;
    private TextView txtGreeting;
    private View notificationDot;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private boolean bannerCallbackRegistered = false;
    private String selectedPopularCategoryId = "all";
    private int pendingRefreshCalls = 0;
    private int categoryLoadSequence;
    private int productLoadSequence;
    private boolean initialCategoriesReady;
    private boolean initialProductsReady;
    private boolean preparingHomeImages;
    private boolean homeImagesPrepared;
    private Runnable cancelHomeImages;
    private final Handler homeLoadingHandler = new Handler(Looper.getMainLooper());

    private HomeCategoryAdapter categoryAdapter;
    private HomeProductAdapter productAdapter;

    private final List<HomeCategory> allCategories = new ArrayList<>();
    private final List<HomeProduct> allProducts = new ArrayList<>();
    private long lastUserServerRefreshMs = 0L;
    private com.google.firebase.firestore.ListenerRegistration notificationListener;
    private int notificationListenSequence;
    private static final long USER_REFRESH_THROTTLE_MS = 30_000L;


    private final Handler autoSlideHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSlideRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewPagerOffers != null && viewPagerOffers.getAdapter() != null) {
                int count = viewPagerOffers.getAdapter().getItemCount();
                if (count > 1) {
                    int nextItem = (viewPagerOffers.getCurrentItem() + 1) % count;
                    viewPagerOffers.setCurrentItem(nextItem, true);
                    autoSlideHandler.postDelayed(this, 3200);
                }
            }
        }
    };

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final com.nexcentauri.dayanisupercenter.util.GentleShakeDetector shakeDetector =
            new com.nexcentauri.dayanisupercenter.util.GentleShakeDetector();
    private long refreshStartedAt;
    private final Handler refreshFeedbackHandler = new Handler(Looper.getMainLooper());

    private final SensorEventListener shakeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event == null || event.sensor == null || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
                return;
            }

            if (!isAdded() || isHidden() || pendingRefreshCalls > 0 || refreshStartedAt != 0) return;
            if (getActivity() instanceof com.nexcentauri.dayanisupercenter.activity.MainActivity
                    && ((com.nexcentauri.dayanisupercenter.activity.MainActivity) getActivity()).isHomeLoading()) return;
            if (shakeDetector.sample(event.values[0], event.values[1], event.values[2], android.os.SystemClock.elapsedRealtime())) {
                triggerHomeRefresh();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initialCategoriesReady = false;
        initialProductsReady = false;
        preparingHomeImages = false;
        homeImagesPrepared = false;
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        viewPagerOffers = view.findViewById(R.id.viewPagerOffers);
        recyclerCategories = view.findViewById(R.id.recyclerCategories);
        recyclerProducts = view.findViewById(R.id.recyclerProducts);
        imgProfile = view.findViewById(R.id.imgProfile);
        txtName = view.findViewById(R.id.txtName);
        txtGreeting = view.findViewById(R.id.txtGreeting);
        notificationDot = view.findViewById(R.id.notificationDot);

        setupBannerSection();
        setupRecyclerViews();
        setupTopActions(view);
        setupRefresh();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            loadUserDetails(currentUser, false);
        }

        HomeDataCache.initialize(requireContext());
        if (HomeDataCache.isReady()) {
            allCategories.clear();
            allCategories.addAll(HomeDataCache.getCategories());
            categoryAdapter.notifyDataSetChanged();
            setupPopularCategoryChips();

            allProducts.clear();
            allProducts.addAll(HomeDataCache.getProducts());
            showPreviewProducts();
            FirebaseImageLoader.prefetchCategories(requireContext(), allCategories);
            FirebaseImageLoader.prefetch(requireContext(), allProducts);
        }
        loadCategoriesFromFirestore();
        loadProductsFromFirestore();
    }

    private void setupBannerSection() {
        List<OfferBanner> banners = new ArrayList<>();
        banners.add(new OfferBanner("", "", "", R.drawable.home_banner_1));
        banners.add(new OfferBanner("", "", "", R.drawable.home_banner_2));
        viewPagerOffers.setAdapter(new OfferBannerAdapter(banners));
        viewPagerOffers.setOffscreenPageLimit(1);
        viewPagerOffers.setClipToPadding(false);
        viewPagerOffers.setClipChildren(false);

        setupBannerDots(banners.size());
        if (!bannerCallbackRegistered) {
            viewPagerOffers.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateBannerDots(position);
                }
            });
            bannerCallbackRegistered = true;
        }
        updateBannerDots(viewPagerOffers.getCurrentItem());
    }

    private void setupRecyclerViews() {
        recyclerCategories.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        recyclerCategories.setHasFixedSize(true);
        recyclerCategories.setItemAnimator(null);
        recyclerCategories.setOverScrollMode(View.OVER_SCROLL_NEVER);
        categoryAdapter = new HomeCategoryAdapter(allCategories, category -> {
            Intent intent = new Intent(requireContext(), ProductListActivity.class);
            intent.putExtra(ProductListActivity.EXTRA_CATEGORY_ID, category.getId());
            intent.putExtra(ProductListActivity.EXTRA_CATEGORY_NAME, category.getName());
            startActivity(intent);
        });
        recyclerCategories.setAdapter(categoryAdapter);

        recyclerProducts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        productAdapter = new HomeProductAdapter(new ArrayList<>(), product -> openProductDetails(product.getId()));
        recyclerProducts.setAdapter(productAdapter);
        if (recyclerProducts.getItemDecorationCount() == 0) {
            recyclerProducts.addItemDecoration(new SpaceItemDecoration(14, 18));
        }
        recyclerProducts.setItemAnimator(null);
        recyclerProducts.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    private void showPreviewProducts() {
        List<HomeProduct> previewProducts = new ArrayList<>();
        for (HomeProduct product : allProducts) {
            if (!product.isActive()) {
                continue;
            }
            if (!product.isMostPopular()) {
                continue;
            }
            if (!matchesSelectedPopularCategory(product)) {
                continue;
            }
            previewProducts.add(product);
        }
        productAdapter.updateProducts(previewProducts);
    }

    private boolean matchesSelectedPopularCategory(HomeProduct product) {
        String selected = safeString(selectedPopularCategoryId);
        if (selected.isEmpty() || "all".equalsIgnoreCase(selected)) {
            return true;
        }

        String productCategoryId = safeString(product.getCategoryId());
        String productCategoryName = safeString(product.getCategoryName());
        if (productCategoryId.equalsIgnoreCase(selected) || productCategoryName.equalsIgnoreCase(selected)) {
            return true;
        }

        for (HomeCategory category : allCategories) {
            String categoryId = safeString(category.getId());
            String categoryName = safeString(category.getName());
            if ((categoryId.equalsIgnoreCase(productCategoryId) || categoryName.equalsIgnoreCase(productCategoryName) || categoryName.equalsIgnoreCase(productCategoryId))
                    && categoryName.equalsIgnoreCase(selected)) {
                return true;
            }
        }

        return productCategoryName.toLowerCase(Locale.US).contains(selected.toLowerCase(Locale.US))
                || productCategoryId.toLowerCase(Locale.US).contains(selected.toLowerCase(Locale.US));
    }

    private void loadCategoriesFromFirestore() {
        final int sequence = ++categoryLoadSequence;
        final boolean[] freshReceived = {false};
        if (allCategories.isEmpty()) {
            db.collection("categories").get(Source.CACHE).addOnSuccessListener(snapshot -> {
                if (isAdded() && getView() != null && sequence == categoryLoadSequence
                        && !freshReceived[0] && !snapshot.isEmpty()) handleCategoriesLoaded(snapshot);
            });
        }
        db.collection("categories").get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded() || getView() == null || sequence != categoryLoadSequence) return;
                    freshReceived[0] = true;
                    handleCategoriesLoaded(snapshot);
                    initialCategoriesReady = true;
                    prepareInitialHome();
                    onDataLoadFinished();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getView() == null || sequence != categoryLoadSequence) return;
                    onDataLoadFinished();
                    if (allCategories.isEmpty()) Toast.makeText(requireContext(), "Failed to load categories.", Toast.LENGTH_SHORT).show();
                    initialCategoriesReady = true;
                    prepareInitialHome();
                });
    }

    private void handleCategoriesLoaded(QuerySnapshot querySnapshot) {
        allCategories.clear();
        List<DocumentSnapshot> documents = querySnapshot.getDocuments();
        documents.sort((a, b) -> Long.compare(getLong(a, "sortOrder"), getLong(b, "sortOrder")));
        for (DocumentSnapshot document : documents) {
            if (Boolean.FALSE.equals(document.getBoolean("active"))) continue;
            String id = document.getId();
            String name = document.getString("name");
            String imageUrl = document.getString("imageUrl");
            if (name == null || imageUrl == null) continue;
            allCategories.add(new HomeCategory(id, name, imageUrl));
        }
        categoryAdapter.notifyDataSetChanged();
        setupPopularCategoryChips();
        if (isAdded()) FirebaseImageLoader.prefetchCategories(requireContext(), allCategories);
        HomeDataCache.setData(allCategories, allProducts);
    }

    private void loadProductsFromFirestore() {
        final int sequence = ++productLoadSequence;
        final boolean[] freshReceived = {false};
        if (allProducts.isEmpty()) {
            db.collection("products").get(Source.CACHE).addOnSuccessListener(snapshot -> {
                if (isAdded() && getView() != null && sequence == productLoadSequence
                        && !freshReceived[0] && !snapshot.isEmpty()) handleProductsLoaded(snapshot);
            });
        }
        db.collection("products").get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded() || getView() == null || sequence != productLoadSequence) return;
                    freshReceived[0] = true;
                    handleProductsLoaded(snapshot);
                    initialProductsReady = true;
                    prepareInitialHome();
                    onDataLoadFinished();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getView() == null || sequence != productLoadSequence) return;
                    onDataLoadFinished();
                    if (allProducts.isEmpty()) Toast.makeText(requireContext(), "Failed to load products.", Toast.LENGTH_SHORT).show();
                    initialProductsReady = true;
                    prepareInitialHome();
                });
    }

    private void handleProductsLoaded(QuerySnapshot querySnapshot) {
        ProductSnapshotCache.replace(querySnapshot);
        allProducts.clear();
        List<HomeProduct> popularProducts = new ArrayList<>();
        List<HomeProduct> regularProducts = new ArrayList<>();

        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            HomeProduct product = new HomeProduct(
                    document.getString("id") != null ? document.getString("id") : document.getId(),
                    safeString(document.getString("name")),
                    getDouble(document, "rating"),
                    (int) getLong(document, "soldCount"),
                    (int) getLong(document, "price"),
                    safeString(document.getString("imageUrl")),
                    safeString(document.getString("categoryId")),
                    safeString(document.getString("categoryName")),
                    Boolean.TRUE.equals(document.getBoolean("mostPopular")),
                    !Boolean.FALSE.equals(document.getBoolean("active"))
            );
            if (!product.isActive()) {
                continue;
            }
            if (product.isMostPopular()) {
                popularProducts.add(product);
            } else {
                regularProducts.add(product);
            }
        }

        popularProducts.sort((a, b) -> Integer.compare(b.getSoldCount(), a.getSoldCount()));
        regularProducts.sort((a, b) -> Integer.compare(b.getSoldCount(), a.getSoldCount()));
        allProducts.addAll(popularProducts);
        allProducts.addAll(regularProducts);
        showPreviewProducts();
        if (isAdded()) FirebaseImageLoader.prefetch(requireContext(), allProducts);
        HomeDataCache.setData(allCategories, allProducts);
    }

    private void setupTopActions(View root) {
        root.findViewById(R.id.imgProfile).setOnClickListener(v -> {
            if (requireActivity() instanceof com.nexcentauri.dayanisupercenter.activity.MainActivity) {
                ((com.nexcentauri.dayanisupercenter.activity.MainActivity) requireActivity()).openTab(com.nexcentauri.dayanisupercenter.activity.MainActivity.TAB_PROFILE);
            }
        });
        root.findViewById(R.id.iconBell).setOnClickListener(v -> startActivity(new Intent(requireContext(), NotificationActivity.class)));
        root.findViewById(R.id.iconWish).setOnClickListener(v -> startActivity(new Intent(requireContext(), WishlistActivity.class)));
        View.OnClickListener searchClickListener = v -> startActivity(new Intent(requireContext(), SearchActivity.class));
        root.findViewById(R.id.searchBarContainer).setOnClickListener(searchClickListener);
        root.findViewById(R.id.iconSearch).setOnClickListener(searchClickListener);
        root.findViewById(R.id.txtSearchHint).setOnClickListener(searchClickListener);
        root.findViewById(R.id.iconFilter).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_OPEN_FILTER, true);
            intent.putExtra(SearchActivity.EXTRA_AUTO_FOCUS_SEARCH, false);
            startActivity(intent);
        });
        setupPopularCategoryChips();
    }

    private void prepareInitialHome() {
        if (!initialCategoriesReady || !initialProductsReady || preparingHomeImages || !isAdded()
                || !(getActivity() instanceof com.nexcentauri.dayanisupercenter.activity.MainActivity)) return;
        com.nexcentauri.dayanisupercenter.activity.MainActivity activity =
                (com.nexcentauri.dayanisupercenter.activity.MainActivity) getActivity();
        if (!activity.isHomeLoading()) return;
        preparingHomeImages = true;
        cancelHomeImages = FirebaseImageLoader.prepareCatalog(requireContext(), allCategories, allProducts,
                () -> { homeImagesPrepared = true; waitForHomeImages(activity); });
    }

    private void waitForHomeImages(com.nexcentauri.dayanisupercenter.activity.MainActivity activity) {
        if (!isAdded() || !isResumed() || getView() == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!activity.isHomeLoading()) return;
        if (recyclerCategories.isLayoutRequested() || recyclerProducts.isLayoutRequested()
                || !areVisibleImagesReady(recyclerCategories, R.id.imgCategory)
                || !areVisibleImagesReady(recyclerProducts, R.id.imgProduct)) {
            homeLoadingHandler.postDelayed(() -> waitForHomeImages(activity), 32);
            return;
        }
        activity.completeHomeLoading();
    }

    private boolean areVisibleImagesReady(RecyclerView recycler, int imageId) {
        if (recycler.getAdapter() != null && recycler.getAdapter().getItemCount() > 0 && recycler.getChildCount() == 0) return false;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            ImageView image = recycler.getChildAt(i).findViewById(imageId);
            if (image != null && !FirebaseImageLoader.isReady(image)) return false;
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        homeLoadingHandler.removeCallbacksAndMessages(null);
        if (cancelHomeImages != null) cancelHomeImages.run();
        cancelHomeImages = null;
        super.onDestroyView();
    }

    private void openProductDetails(String productId) {
        Intent intent = new Intent(requireContext(), SingleProductActivity.class);
        intent.putExtra(SingleProductActivity.EXTRA_PRODUCT_ID, productId);
        startActivity(intent);
    }

    private void setupPopularCategoryChips() {
        setupPopularChip(requireView().findViewById(R.id.chipAll), "all");
        setupPopularChip(requireView().findViewById(R.id.chipElectronics), "Electronics");
        setupPopularChip(requireView().findViewById(R.id.chipFashion), "Fashion");
        setupPopularChip(requireView().findViewById(R.id.chipHomeware), "Homeware");
        setupPopularChip(requireView().findViewById(R.id.chipKitchen), "Kitchen");
        setupPopularChip(requireView().findViewById(R.id.chipPlastic), "Plastic");
        setupPopularChip(requireView().findViewById(R.id.chipPooja), "Pooja");
        updatePopularChipSelection();
    }

    private void setupPopularChip(TextView chipView, String categoryId) {
        if (chipView == null) {
            return;
        }
        chipView.setTag(categoryId);
        chipView.setOnClickListener(v -> {
            Object tag = v.getTag();
            selectedPopularCategoryId = tag != null && !tag.toString().trim().isEmpty() ? tag.toString() : "all";
            updatePopularChipSelection();
            showPreviewProducts();
        });
    }

    private void updatePopularChipSelection() {
        updatePopularChipView(requireView().findViewById(R.id.chipAll));
        updatePopularChipView(requireView().findViewById(R.id.chipElectronics));
        updatePopularChipView(requireView().findViewById(R.id.chipFashion));
        updatePopularChipView(requireView().findViewById(R.id.chipHomeware));
        updatePopularChipView(requireView().findViewById(R.id.chipKitchen));
        updatePopularChipView(requireView().findViewById(R.id.chipPlastic));
        updatePopularChipView(requireView().findViewById(R.id.chipPooja));
    }

    private void updatePopularChipView(TextView chipView) {
        if (chipView == null) {
            return;
        }
        String chipCategoryId = safeString((String) chipView.getTag());
        boolean isSelected = safeString(selectedPopularCategoryId).equalsIgnoreCase(chipCategoryId);
        chipView.setBackgroundResource(isSelected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_outline);
        chipView.setTextColor(isSelected ? 0xFFFFFFFF : 0xFF181818);
        chipView.setTypeface(chipView.getTypeface(), isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void setupRefresh() {
        swipeRefreshLayout.setColorSchemeColors(0xFF111111);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(0xFFFFFFFF);
        swipeRefreshLayout.setOnRefreshListener(this::triggerHomeRefresh);
    }

    private void triggerHomeRefresh() {
        if (pendingRefreshCalls > 0 || refreshStartedAt != 0 || !isAdded()) return;
        refreshStartedAt = android.os.SystemClock.elapsedRealtime();
        shakeDetector.reset();
        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }
        selectedPopularCategoryId = "all";
        updatePopularChipSelection();
        pendingRefreshCalls = 2;
        loadCategoriesFromFirestore();
        loadProductsFromFirestore();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            loadUserDetails(currentUser, true);
        }
        if (viewPagerOffers != null) {
            viewPagerOffers.post(() -> viewPagerOffers.setCurrentItem(0, true));
        }
    }

    private void onDataLoadFinished() {
        if (pendingRefreshCalls > 0) {
            pendingRefreshCalls--;
        }
        if (pendingRefreshCalls <= 0 && swipeRefreshLayout != null) {
            long delay = refreshStartedAt == 0 ? 0 : Math.max(0, 1500 - (android.os.SystemClock.elapsedRealtime() - refreshStartedAt));
            refreshFeedbackHandler.removeCallbacksAndMessages(null);
            refreshFeedbackHandler.postDelayed(() -> {
                if (swipeRefreshLayout != null && pendingRefreshCalls <= 0) swipeRefreshLayout.setRefreshing(false);
                refreshStartedAt = 0;
            }, delay);
        }
    }

    private void setupBannerDots(int count) {
        LinearLayout layoutDots = requireView().findViewById(R.id.layoutDots);
        layoutDots.removeAllViews();
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            dot.setLayoutParams(params);
            dot.setImageResource(i == 0 ? R.drawable.dot_active : R.drawable.dot_inactive);
            layoutDots.addView(dot);
        }
    }

    private void updateBannerDots(int activePosition) {
        LinearLayout layoutDots = requireView().findViewById(R.id.layoutDots);
        for (int i = 0; i < layoutDots.getChildCount(); i++) {
            ImageView dot = (ImageView) layoutDots.getChildAt(i);
            dot.setImageResource(i == activePosition ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void loadUserDetails(@NonNull FirebaseUser currentUser, boolean refreshFromServer) {
        String uid = currentUser.getUid();
        String cachedName = UserProfileCache.getName(requireContext());
        String cachedEmail = UserProfileCache.getEmail(requireContext());
        String cachedPhone = UserProfileCache.getPhone(requireContext());
        String cachedPhoto = UserProfileCache.getPhoto(requireContext());

        String fallbackName = !TextUtils.isEmpty(cachedName)
                ? cachedName
                : (currentUser.getDisplayName() != null && !currentUser.getDisplayName().trim().isEmpty()
                ? currentUser.getDisplayName() : "User");
        String fallbackPhoto = !TextUtils.isEmpty(cachedPhoto)
                ? cachedPhoto
                : (currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "");
        updateGreeting();
        txtName.setText(fallbackName);

        if (!fallbackPhoto.isEmpty()) {
            Glide.with(this)
                    .load(fallbackPhoto)
                    .circleCrop()
                    .placeholder(R.drawable.logo_clean)
                    .error(R.drawable.logo_clean)
                    .into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.logo_clean);
        }

        db.collection("users").document(uid).get(Source.CACHE).addOnSuccessListener(documentSnapshot -> {
            bindUserDetailsSnapshot(documentSnapshot, fallbackName, fallbackPhoto);
            if (refreshFromServer) {
                db.collection("users").document(uid).get(Source.SERVER)
                        .addOnSuccessListener(serverSnapshot -> bindUserDetailsSnapshot(serverSnapshot, fallbackName, fallbackPhoto));
            }
        }).addOnFailureListener(e -> {
            if (refreshFromServer) {
                db.collection("users").document(uid).get(Source.SERVER)
                        .addOnSuccessListener(serverSnapshot -> bindUserDetailsSnapshot(serverSnapshot, fallbackName, fallbackPhoto));
            }
        });
    }

    private void bindUserDetailsSnapshot(DocumentSnapshot documentSnapshot, String fallbackName, String fallbackPhoto) {
        if (documentSnapshot == null || !documentSnapshot.exists() || !isAdded() || getView() == null) return;

        String name = documentSnapshot.getString("name");
        String email = documentSnapshot.getString("email");
        String phone = documentSnapshot.getString("phone");
        String photoUrl = documentSnapshot.getString("photoUrl");
        String localPhotoUri = documentSnapshot.getString("localPhotoUri");

        String finalName = name != null && !name.trim().isEmpty() ? name : fallbackName;
        String displayPhoto = localPhotoUri != null && !localPhotoUri.trim().isEmpty() ? localPhotoUri : photoUrl;
        if (TextUtils.isEmpty(displayPhoto)) {
            displayPhoto = fallbackPhoto;
        }

        txtName.setText(finalName);
        UserProfileCache.save(requireContext(), finalName, email, phone, displayPhoto);
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getUid().equals(documentSnapshot.getId())) {
            ProfilePhotoRepair.repair(requireContext(), user.getUid(), documentSnapshot);
        }

        if (!TextUtils.isEmpty(displayPhoto)) {
            Glide.with(this)
                    .load(displayPhoto)
                    .circleCrop()
                    .placeholder(R.drawable.logo_clean)
                    .error(R.drawable.logo_clean)
                    .into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.logo_clean);
        }
    }

    private boolean shouldRefreshUserFromServer() {
        long now = System.currentTimeMillis();
        if (now - lastUserServerRefreshMs >= USER_REFRESH_THROTTLE_MS) {
            lastUserServerRefreshMs = now;
            return true;
        }
        return false;
    }

    private void updateGreeting() {
        if (txtGreeting == null) return;
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) {
            greeting = "Good Morning 👋";
        } else if (hour < 17) {
            greeting = "Good Afternoon 👋";
        } else if (hour < 21) {
            greeting = "Good Evening 👋";
        } else {
            greeting = "Good Night 👋";
        }
        txtGreeting.setText(greeting);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGreeting();
        FirebaseUser currentUser = auth != null ? auth.getCurrentUser() : null;
        if (currentUser != null) {
            loadUserDetails(currentUser, shouldRefreshUserFromServer());
        }
        autoSlideHandler.removeCallbacks(autoSlideRunnable);
        autoSlideHandler.postDelayed(autoSlideRunnable, 3200);
        shakeDetector.reset();
        if (!isHidden() && sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (productAdapter != null) productAdapter.refreshWishlist(requireContext());
        updateNotificationDot();
        if (homeImagesPrepared && getActivity() instanceof com.nexcentauri.dayanisupercenter.activity.MainActivity)
            waitForHomeImages((com.nexcentauri.dayanisupercenter.activity.MainActivity) getActivity());
    }

    @Override
    public void onPause() {
        refreshFeedbackHandler.removeCallbacksAndMessages(null);
        refreshStartedAt = 0;
        if (swipeRefreshLayout != null && pendingRefreshCalls <= 0) swipeRefreshLayout.setRefreshing(false);
        shakeDetector.reset();
        homeLoadingHandler.removeCallbacksAndMessages(null);
        stopNotificationListener();
        autoSlideHandler.removeCallbacks(autoSlideRunnable);
        if (sensorManager != null) {
            sensorManager.unregisterListener(shakeListener);
        }
        super.onPause();
    }


    @Override public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        shakeDetector.reset();
        if (sensorManager == null || accelerometer == null) return;
        sensorManager.unregisterListener(shakeListener);
        if (!hidden && isResumed()) sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    private void stopNotificationListener() {
        notificationListenSequence++;
        if (notificationListener != null) notificationListener.remove();
        notificationListener = null;
    }

    private void updateNotificationDot() {
        stopNotificationListener();
        FirebaseUser currentUser = auth != null ? auth.getCurrentUser() : null;
        if (notificationDot == null) return;
        if (currentUser == null) {
            notificationDot.setVisibility(View.GONE);
            return;
        }
        String uid = currentUser.getUid();
        int sequence = notificationListenSequence;
        notificationListener = db.collection("users").document(uid).collection("notifications")
                .whereEqualTo("read", false).limit(1)
                .addSnapshotListener((snapshot, error) -> {
                    if (!isAdded() || getView() == null || sequence != notificationListenSequence
                            || !uid.equals(FirebaseAuth.getInstance().getUid())) return;
                    if (error == null && snapshot != null)
                        notificationDot.setVisibility(snapshot.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    private long getLong(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value != null ? value : 0L;
    }

    private double getDouble(DocumentSnapshot document, String field) {
        Double value = document.getDouble(field);
        return value != null ? value : 0.0;
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private static class SpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int horizontal;
        private final int vertical;

        SpaceItemDecoration(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            outRect.bottom = vertical;
            if (position % 2 == 0) {
                outRect.right = horizontal / 2;
            } else {
                outRect.left = horizontal / 2;
            }
        }
    }
}
