package com.nexcentauri.dayanisupercenter.activity;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import lk.payhere.androidsdk.PHMainActivity;

public class TopAlignedPayHereActivity extends PHMainActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View sheet = findViewById(lk.payhere.androidsdk.R.id.bottom_sheet);
        if (sheet == null) return;
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        behavior.setFitToContents(false);
        behavior.setExpandedOffset(0);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(false);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override public void onStateChanged(@NonNull View view, int state) {
                if (state == BottomSheetBehavior.STATE_COLLAPSED || state == BottomSheetBehavior.STATE_HALF_EXPANDED)
                    expandSheet(sheet, behavior);
            }
            @Override public void onSlide(@NonNull View view, float offset) { }
        });
        expandSheet(sheet, behavior);
    }

    private void expandSheet(View sheet, BottomSheetBehavior<View> behavior) {
        sheet.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(sheet);
            if (insets != null) behavior.setExpandedOffset(insets.getInsets(WindowInsetsCompat.Type.systemBars()).top);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
    }
}
