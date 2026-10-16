package com.nexcentauri.dayanisupercenter.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.slider.RangeSlider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.nexcentauri.dayanisupercenter.util.ProductSnapshotCache;
import com.nexcentauri.dayanisupercenter.R;
import com.nexcentauri.dayanisupercenter.adapter.HomeProductAdapter;
import com.nexcentauri.dayanisupercenter.adapter.RecentSearchAdapter;
import com.nexcentauri.dayanisupercenter.model.HomeProduct;

import org.json.JSONArray;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SearchActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_FILTER = "extra_open_filter";
    public static final String EXTRA_AUTO_FOCUS_SEARCH = "extra_auto_focus_search";

    private static final String PREFS_SEARCH = "search_prefs";
    private static final String KEY_RECENT = "recent_keywords";
    private static final int MAX_RECENT = 8;

    private EditText edtSearch;
    private TextView txtResultsTitle;
    private TextView txtResultCount;
    private View layoutRecent;
    private View layoutResults;
    private View layoutEmpty;
    private RecyclerView recyclerRecent;
    private RecyclerView recyclerResults;
    private TextView btnClearAll;
    private TextView txtEmptyMessage;
    private TextView txtSearchClear;
    private TextView txtResultsTitleEmpty;
    private TextView txtResultCountEmpty;

    private HomeProductAdapter productAdapter;
    private RecentSearchAdapter recentSearchAdapter;
    private boolean filtersApplied;
    private boolean priceFilterChanged;
    private final java.util.Map<String, Long> productCreatedTimes = new java.util.HashMap<>();
    private final List<HomeProduct> allProducts = new ArrayList<>();
    private final List<HomeProduct> filteredProducts = new ArrayList<>();
    private final List<String> recentKeywords = new ArrayList<>();
    private final Set<String> availableCategories = new LinkedHashSet<>();
    private final List<String> sortOptions = Arrays.asList("Popular", "Most Recent", "Price Low", "Price High");
    private final List<Integer> ratingOptions = Arrays.asList(0, 5, 4, 3, 2);
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    private FirebaseFirestore db;
    private BottomSheetDialog filterDialog;

    private String selectedCategory = "All";
    private String selectedSort = "Popular";
    private int selectedRating = 0;
    private int availableMinPrice = 0;
    private int availableMaxPrice = 10000;
    private int selectedMinPrice = 0;
    private int selectedMaxPrice = 10000;
    private long fetchSequence = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);

        View root = findViewById(R.id.searchRoot);
        com.nexcentauri.dayanisupercenter.util.SystemBarUtil.applyWindowInsets(this);

        db = FirebaseFirestore.getInstance();
        bindViews();
        setupLists();
        loadRecentSearches();
        setupActions();
        boolean openFilter = getIntent().getBooleanExtra(EXTRA_OPEN_FILTER, false);
        filtersApplied = false;
        loadProducts();
        boolean autoFocusSearch = getIntent().getBooleanExtra(EXTRA_AUTO_FOCUS_SEARCH, !openFilter);
        if (autoFocusSearch) {
            root.post(this::focusSearchFieldAndOpenKeyboard);
        }
        if (openFilter) {
            root.post(this::showFilterSheet);
        }
    }

    private void bindViews() {
        edtSearch = findViewById(R.id.edtSearch);
        txtResultsTitle = findViewById(R.id.txtResultsTitle);
        txtResultCount = findViewById(R.id.txtResultCount);
        layoutRecent = findViewById(R.id.layoutRecentState);
        layoutResults = findViewById(R.id.layoutResultsState);
        layoutEmpty = findViewById(R.id.layoutEmptyState);
        recyclerRecent = findViewById(R.id.recyclerRecentSearches);
        recyclerResults = findViewById(R.id.recyclerSearchResults);
        btnClearAll = findViewById(R.id.txtClearAllRecent);
        txtEmptyMessage = findViewById(R.id.txtEmptyKeywordMessage);
        txtSearchClear = findViewById(R.id.txtSearchClear);
        txtResultsTitleEmpty = findViewById(R.id.txtResultsTitleEmptyHolder);
        txtResultCountEmpty = findViewById(R.id.txtResultCountEmptyHolder);
    }

    private void setupLists() {
        recyclerRecent.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recentSearchAdapter = new RecentSearchAdapter(new RecentSearchAdapter.Listener() {
            @Override
            public void onRecentClicked(String keyword) {
                edtSearch.setText(keyword);
                edtSearch.setSelection(keyword.length());
                applySearch();
            }

            @Override
            public void onRemoveClicked(String keyword) {
                recentKeywords.remove(keyword);
                saveRecentSearches();
                updateRecentState();
            }
        });
        recyclerRecent.setAdapter(recentSearchAdapter);

        recyclerResults.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerResults.setItemAnimator(null);
        if (recyclerResults.getItemDecorationCount() == 0) {
            recyclerResults.addItemDecoration(new SpaceItemDecoration(14, 18));
        }
        productAdapter = new HomeProductAdapter(filteredProducts, product -> {
            saveRecentSearch(edtSearch.getText().toString().trim());
            Intent intent = new Intent(SearchActivity.this, SingleProductActivity.class);
            intent.putExtra(SingleProductActivity.EXTRA_PRODUCT_ID, product.getId());
            startActivity(intent);
        });
        recyclerResults.setAdapter(productAdapter);
    }

    private void setupActions() {
        findViewById(R.id.btnSearchBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOpenFilter).setOnClickListener(v -> showFilterSheet());
        btnClearAll.setOnClickListener(v -> {
            recentKeywords.clear();
            saveRecentSearches();
            updateRecentState();
        });
        txtSearchClear.setVisibility(View.GONE);

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                saveRecentSearch(edtSearch.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void focusSearchFieldAndOpenKeyboard() {
        edtSearch.requestFocus();
        edtSearch.setSelection(edtSearch.getText() != null ? edtSearch.getText().length() : 0);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(edtSearch, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void loadProducts() {
        long requestId = ++fetchSequence;
        final boolean[] fresh = {false};
        java.util.List<DocumentSnapshot> memory = ProductSnapshotCache.all();
        if (!memory.isEmpty()) renderProducts(memory);
        db.collection("products").get(Source.CACHE).addOnSuccessListener(snapshot -> {
            if (!isFinishing() && !isDestroyed() && requestId == fetchSequence && !fresh[0] && !snapshot.isEmpty()) renderProducts(snapshot.getDocuments());
        });
        db.collection("products").get().addOnSuccessListener(snapshot -> {
            if (isFinishing() || isDestroyed() || requestId != fetchSequence) return;
            fresh[0] = true;
            ProductSnapshotCache.replace(snapshot);
            renderProducts(snapshot.getDocuments());
        }).addOnFailureListener(error -> {
            if (!isFinishing() && !isDestroyed() && allProducts.isEmpty()) Toast.makeText(this, "Failed to load products.", Toast.LENGTH_SHORT).show();
        });
    }

    private void renderProducts(java.util.List<DocumentSnapshot> documents) {
        allProducts.clear();
        productCreatedTimes.clear();
        availableCategories.clear();
        int minPrice = Integer.MAX_VALUE;
        int maxPrice = 0;

        for (DocumentSnapshot document : documents) {
            boolean active = !Boolean.FALSE.equals(document.getBoolean("active"));
            if (!active) {
                continue;
            }
            int price = (int) longValue(document.getLong("price"));
            HomeProduct product = new HomeProduct(
                    document.getString("id") != null ? document.getString("id") : document.getId(),
                    value(document.getString("name")),
                    doubleValue(document.getDouble("rating")),
                    (int) longValue(document.getLong("soldCount")),
                    price,
                    value(document.getString("imageUrl")),
                    value(document.getString("categoryId")),
                    value(document.getString("categoryName")),
                    Boolean.TRUE.equals(document.getBoolean("mostPopular")),
                    active
            );
            allProducts.add(product);
            productCreatedTimes.put(product.getId(), createdTime(document));
            if (!product.getCategoryName().trim().isEmpty()) {
                availableCategories.add(product.getCategoryName().trim());
            }
            if (price > 0) {
                minPrice = Math.min(minPrice, price);
                maxPrice = Math.max(maxPrice, price);
            }
        }

        if (minPrice == Integer.MAX_VALUE) {
            minPrice = 0;
        }
        availableMinPrice = normalizeDown(minPrice);
        availableMaxPrice = Math.max(normalizeUp(maxPrice), availableMinPrice + 1000);
        if (!priceFilterChanged) {
            selectedMinPrice = availableMinPrice;
            selectedMaxPrice = availableMaxPrice;
        }
        selectedMinPrice = Math.max(availableMinPrice, Math.min(selectedMinPrice, availableMaxPrice));
        selectedMaxPrice = Math.max(selectedMinPrice, Math.min(selectedMaxPrice, availableMaxPrice));
        if (filterDialog != null && filterDialog.isShowing()) updateFilterViews(filterDialog.findViewById(R.id.filterContentRoot));
        applySearch();
    }

    private void applySearch() {
        String keyword = edtSearch.getText().toString().trim();
        if (keyword.isEmpty() && !filtersApplied) {
            showRecentState();
            return;
        }

        filteredProducts.clear();
        String lowerKeyword = keyword.toLowerCase(Locale.US);
        for (HomeProduct product : allProducts) {
            if (!matchesKeyword(product, lowerKeyword)) {
                continue;
            }
            if (!matchesCategory(product)) {
                continue;
            }
            if (!matchesPrice(product)) {
                continue;
            }
            if (!matchesRating(product)) {
                continue;
            }
            filteredProducts.add(product);
        }

        sortProducts(filteredProducts);
        productAdapter.updateProducts(filteredProducts);
        updateResultsHeader(keyword, filteredProducts.size());

        if (filteredProducts.isEmpty()) {
            showEmptyState(keyword);
        } else {
            showResultsState();
        }
    }

    private boolean hasCustomFilters() {
        return !"All".equalsIgnoreCase(selectedCategory)
                || !"Popular".equals(selectedSort)
                || selectedRating > 0
                || selectedMinPrice > availableMinPrice
                || selectedMaxPrice < availableMaxPrice;
    }

    private boolean matchesKeyword(HomeProduct product, String keyword) {
        return value(product.getName()).toLowerCase(Locale.US).contains(keyword)
                || value(product.getCategoryName()).toLowerCase(Locale.US).contains(keyword)
                || value(product.getCategoryId()).toLowerCase(Locale.US).contains(keyword);
    }

    private boolean matchesCategory(HomeProduct product) {
        return "All".equalsIgnoreCase(selectedCategory)
                || value(product.getCategoryName()).equalsIgnoreCase(selectedCategory)
                || value(product.getCategoryId()).equalsIgnoreCase(selectedCategory);
    }

    private boolean matchesPrice(HomeProduct product) {
        return product.getPrice() >= selectedMinPrice && product.getPrice() <= selectedMaxPrice;
    }

    private boolean matchesRating(HomeProduct product) {
        return selectedRating <= 0 || product.getRating() >= selectedRating;
    }

    private void sortProducts(List<HomeProduct> products) {
        switch (selectedSort) {
            case "Most Recent":
                products.sort((a, b) -> Long.compare(productCreatedTimes.getOrDefault(b.getId(), 0L), productCreatedTimes.getOrDefault(a.getId(), 0L)));
                break;
            case "Price Low":
                products.sort((a, b) -> Integer.compare(a.getPrice(), b.getPrice()));
                break;
            case "Price High":
                products.sort((a, b) -> Integer.compare(b.getPrice(), a.getPrice()));
                break;
            case "Popular":
            default:
                products.sort((a, b) -> Integer.compare(b.getSoldCount(), a.getSoldCount()));
                break;
        }
    }

    private void updateResultsHeader(String keyword, int count) {
        String title = keyword.isEmpty() ? "Filtered products" : "Results for \"" + keyword + "\"";
        String countText = numberFormat.format(count) + " found";
        txtResultsTitle.setText(title);
        txtResultCount.setText(countText);
        txtResultsTitleEmpty.setText(title);
        txtResultCountEmpty.setText(countText);
    }

    private void showRecentState() {
        layoutRecent.setVisibility(View.VISIBLE);
        layoutResults.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.GONE);
        updateRecentState();
    }

    private void showResultsState() {
        layoutRecent.setVisibility(View.GONE);
        layoutResults.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);
    }

    private void showEmptyState(String keyword) {
        layoutRecent.setVisibility(View.GONE);
        layoutResults.setVisibility(View.GONE);
        layoutEmpty.setVisibility(View.VISIBLE);
        String title = keyword.isEmpty() ? "Filtered products" : "Results for \"" + keyword + "\"";
        txtResultsTitle.setText(title);
        txtResultCount.setText("0 found");
        txtResultsTitleEmpty.setText(title);
        txtResultCountEmpty.setText("0 found");
        txtEmptyMessage.setText(keyword.isEmpty() ? "No products match these filters. Try changing or resetting the filters."
                : "No products match this search and filters. Try another keyword or reset the filters.");
    }

    private void updateRecentState() {
        recentSearchAdapter.submitList(recentKeywords);
        btnClearAll.setVisibility(recentKeywords.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadRecentSearches() {
        recentKeywords.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE);
        String rawJson = prefs.getString(KEY_RECENT, "[]");
        try {
            JSONArray array = new JSONArray(rawJson);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty()) {
                    recentKeywords.add(value);
                }
            }
        } catch (Exception ignored) {
        }
        updateRecentState();
    }

    private void saveRecentSearch(String keyword) {
        if (keyword == null) {
            return;
        }
        String cleanKeyword = keyword.trim();
        if (cleanKeyword.isEmpty()) {
            return;
        }
        recentKeywords.remove(cleanKeyword);
        recentKeywords.add(0, cleanKeyword);
        while (recentKeywords.size() > MAX_RECENT) {
            recentKeywords.remove(recentKeywords.size() - 1);
        }
        saveRecentSearches();
        updateRecentState();
    }

    private void saveRecentSearches() {
        JSONArray jsonArray = new JSONArray();
        for (String item : recentKeywords) {
            jsonArray.put(item);
        }
        getSharedPreferences(PREFS_SEARCH, MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENT, jsonArray.toString())
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (productAdapter != null) productAdapter.refreshWishlist(this);
    }

    private void hideSearchKeyboard() {
        InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
    }

    private long createdTime(DocumentSnapshot product) {
        Object time = product.get("createdAt");
        if (time == null) time = product.get("createdAtClient");
        if (time instanceof com.google.firebase.Timestamp) return ((com.google.firebase.Timestamp) time).toDate().getTime();
        if (time instanceof java.util.Date) return ((java.util.Date) time).getTime();
        return time instanceof Number ? ((Number) time).longValue() : 0L;
    }

    private void showFilterSheet() {
        hideSearchKeyboard();
        if (filterDialog == null) {
            filterDialog = new BottomSheetDialog(this);
            View contentView = LayoutInflater.from(this).inflate(R.layout.dialog_search_filter, null, false);
            filterDialog.setContentView(contentView);
            setupFilterContent(contentView);
        }
        updateFilterViews(filterDialog.findViewById(R.id.filterContentRoot));
        filterDialog.show();
    }

    private void setupFilterContent(View root) {
        if (root == null) {
            return;
        }
        RangeSlider rangeSlider = root.findViewById(R.id.rangePrice);
        TextView txtMinPrice = root.findViewById(R.id.txtMinPrice);
        TextView txtMaxPrice = root.findViewById(R.id.txtMaxPrice);
        TextView txtSelectedPriceRange = root.findViewById(R.id.txtSelectedPriceRange);
        LinearLayout layoutCategories = root.findViewById(R.id.layoutCategoryChips);
        LinearLayout layoutSort = root.findViewById(R.id.layoutSortChips);
        LinearLayout layoutRatings = root.findViewById(R.id.layoutRatingChips);
        TextView btnReset = root.findViewById(R.id.btnResetFilter);
        TextView btnApply = root.findViewById(R.id.btnApplyFilter);

        rangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            priceFilterChanged = true;
            List<Float> values = slider.getValues();
            selectedMinPrice = Math.round(values.get(0));
            selectedMaxPrice = Math.round(values.get(1));
            txtMinPrice.setText("Min Rs. " + numberFormat.format(selectedMinPrice));
            txtMaxPrice.setText("Max Rs. " + numberFormat.format(selectedMaxPrice));
            txtSelectedPriceRange.setText("Rs. " + numberFormat.format(selectedMinPrice) + " - Rs. " + numberFormat.format(selectedMaxPrice));
        });

        btnReset.setOnClickListener(v -> {
            filtersApplied = false;
            priceFilterChanged = false;
            selectedCategory = "All";
            selectedSort = "Popular";
            selectedRating = 0;
            selectedMinPrice = availableMinPrice;
            selectedMaxPrice = availableMaxPrice;
            updateFilterViews(root);
            applySearch();
        });

        btnApply.setOnClickListener(v -> {
            priceFilterChanged = selectedMinPrice > availableMinPrice || selectedMaxPrice < availableMaxPrice;
            filtersApplied = hasCustomFilters();
            hideSearchKeyboard();
            saveRecentSearch(edtSearch.getText().toString().trim());
            applySearch();
            if (filterDialog != null) {
                filterDialog.dismiss();
            }
        });

        populateChipRow(layoutCategories, getDisplayCategories(), selectedCategory, value -> {
            selectedCategory = value;
            updateFilterViews(root);
        });
        populateChipRow(layoutSort, sortOptions, selectedSort, value -> {
            selectedSort = value;
            updateFilterViews(root);
        });
        populateRatingRow(layoutRatings, selectedRating, value -> {
            selectedRating = value;
            updateFilterViews(root);
        });
    }

    private void updateFilterViews(View root) {
        if (root == null) {
            return;
        }
        RangeSlider rangeSlider = root.findViewById(R.id.rangePrice);
        TextView txtMinPrice = root.findViewById(R.id.txtMinPrice);
        TextView txtMaxPrice = root.findViewById(R.id.txtMaxPrice);
        TextView txtSelectedPriceRange = root.findViewById(R.id.txtSelectedPriceRange);
        LinearLayout layoutCategories = root.findViewById(R.id.layoutCategoryChips);
        LinearLayout layoutSort = root.findViewById(R.id.layoutSortChips);
        LinearLayout layoutRatings = root.findViewById(R.id.layoutRatingChips);

        rangeSlider.setValueFrom(availableMinPrice);
        rangeSlider.setValueTo(availableMaxPrice);
        rangeSlider.setStepSize(100f);
        rangeSlider.setValues((float) selectedMinPrice, (float) selectedMaxPrice);
        txtMinPrice.setText("Min Rs. " + numberFormat.format(selectedMinPrice));
        txtMaxPrice.setText("Max Rs. " + numberFormat.format(selectedMaxPrice));
        txtSelectedPriceRange.setText("Rs. " + numberFormat.format(selectedMinPrice) + " - Rs. " + numberFormat.format(selectedMaxPrice));

        populateChipRow(layoutCategories, getDisplayCategories(), selectedCategory, value -> {
            selectedCategory = value;
            updateFilterViews(root);
        });
        populateChipRow(layoutSort, sortOptions, selectedSort, value -> {
            selectedSort = value;
            updateFilterViews(root);
        });
        populateRatingRow(layoutRatings, selectedRating, value -> {
            selectedRating = value;
            updateFilterViews(root);
        });
    }

    private List<String> getDisplayCategories() {
        List<String> categories = new ArrayList<>();
        categories.add("All");
        categories.addAll(availableCategories);
        return categories;
    }

    private void populateChipRow(LinearLayout container, List<String> items, String selectedValue, ChipClickListener listener) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        for (String item : items) {
            TextView chip = (TextView) LayoutInflater.from(this).inflate(R.layout.item_filter_chip, container, false);
            chip.setText(item);
            boolean isSelected = item.equalsIgnoreCase(selectedValue);
            chip.setBackgroundResource(isSelected ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip_unselected);
            chip.setTextColor(ContextCompat.getColor(this, isSelected ? android.R.color.white : android.R.color.black));
            chip.setTypeface(chip.getTypeface(), isSelected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setOnClickListener(v -> listener.onChipClicked(item));
            container.addView(chip);
        }
    }

    private void populateRatingRow(LinearLayout container, int selectedValue, RatingChipClickListener listener) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        for (Integer item : ratingOptions) {
            TextView chip = (TextView) LayoutInflater.from(this).inflate(R.layout.item_filter_chip, container, false);
            chip.setText(item == 0 ? "★ All" : "★ " + item);
            boolean isSelected = item == selectedValue;
            chip.setBackgroundResource(isSelected ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip_unselected);
            chip.setTextColor(ContextCompat.getColor(this, isSelected ? android.R.color.white : android.R.color.black));
            chip.setTypeface(chip.getTypeface(), isSelected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setOnClickListener(v -> listener.onChipClicked(item));
            container.addView(chip);
        }
    }

    private int normalizeDown(int value) {
        return Math.max(0, (value / 100) * 100);
    }

    private int normalizeUp(int value) {
        if (value <= 0) {
            return 1000;
        }
        return ((value + 99) / 100) * 100;
    }

    private long longValue(Long value) {
        return value != null ? value : 0L;
    }

    private double doubleValue(Double value) {
        return value != null ? value : 0.0;
    }

    private String value(String text) {
        return text != null ? text : "";
    }


    private static class SpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int horizontal;
        private final int vertical;

        SpaceItemDecoration(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            outRect.bottom = vertical;
            if (position % 2 == 0) {
                outRect.right = horizontal / 2;
            } else {
                outRect.left = horizontal / 2;
            }
        }
    }

    private interface ChipClickListener {
        void onChipClicked(String value);
    }

    private interface RatingChipClickListener {
        void onChipClicked(int value);
    }
}
