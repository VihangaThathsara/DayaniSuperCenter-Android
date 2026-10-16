package com.nexcentauri.dayanisupercenter.util;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.ColorInt;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.WeakHashMap;

public final class SystemBarUtil {

    private static final WeakHashMap<View, int[]> originalPadding = new WeakHashMap<>();

    private SystemBarUtil() {
    }

    public static void matchStatusBarToContent(Activity activity) {
        if (activity == null) return;
        applyWindowInsets(activity);
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;

        View root = content.getChildAt(0);
        Drawable background = root.getBackground();
        if (background instanceof ColorDrawable) {
            applyStatusBarColor(activity, ((ColorDrawable) background).getColor());
        }
    }

    public static void applyWindowInsets(Activity activity) {
        if (activity == null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View root = content.getChildAt(0);
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        root.setFitsSystemWindows(false);
        int[] padding = originalPadding.get(root);
        if (padding == null) {
            padding = new int[]{root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), root.getPaddingBottom()};
            originalPadding.put(root, padding);
        }
        final int[] base = padding;
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(base[0] + safe.left, base[1] + safe.top,
                    base[2] + safe.right, base[3] + safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    public static void applyStatusBarColor(Activity activity, @ColorInt int color) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        window.setStatusBarColor(color);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(isLight(color));
        }
    }

    private static boolean isLight(@ColorInt int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness < 0.45;
    }
}
