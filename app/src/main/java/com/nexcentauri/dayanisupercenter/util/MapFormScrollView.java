package com.nexcentauri.dayanisupercenter.util;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.widget.NestedScrollView;

import com.nexcentauri.dayanisupercenter.R;

public final class MapFormScrollView extends NestedScrollView {
    private final Rect mapBounds = new Rect();
    private boolean mapGesture;

    public MapFormScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            View map = findViewById(R.id.mapContainer);
            mapGesture = map != null && map.getGlobalVisibleRect(mapBounds)
                    && mapBounds.contains((int) event.getRawX(), (int) event.getRawY());
        }
        if (mapGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) mapGesture = false;
            return false;
        }
        return super.onInterceptTouchEvent(event);
    }
}
