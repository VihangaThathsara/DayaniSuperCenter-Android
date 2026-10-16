package com.nexcentauri.dayanisupercenter.util;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.nexcentauri.dayanisupercenter.R;

public final class AppMessageUtil {

    public static final int TYPE_INFO = 0;
    public static final int TYPE_SUCCESS = 1;
    public static final int TYPE_WARNING = 2;
    public static final int TYPE_ERROR = 3;

    private AppMessageUtil() {
    }

    public static void showFromContext(android.content.Context context, String message, int type) {
        while (context instanceof android.content.ContextWrapper && !(context instanceof Activity)) {
            android.content.Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        if (context instanceof Activity) show((Activity) context, message, type);
    }

    public static void show(Activity activity, String message, int type) {
        show(activity, message, type, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 70);
    }

    public static void showVerify(Activity activity, String message, int type) {
        show(activity, message, type, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 50);
    }

    public static void show(Activity activity, String message, int type, int gravity, int yOffsetDp) {
        if (activity == null || activity.isFinishing()) {
            return;
        }


        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.layout_custom_message, null, false);

        View root = view.findViewById(R.id.messageRoot);
        ImageView icon = view.findViewById(R.id.imgMessageIcon);
        TextView text = view.findViewById(R.id.txtMessage);

        text.setText(message);

        switch (type) {
            case TYPE_SUCCESS:
                root.setBackgroundResource(R.drawable.bg_custom_message_success);
                icon.setImageResource(R.drawable.ic_status_success);
                break;
            case TYPE_WARNING:
                root.setBackgroundResource(R.drawable.bg_custom_message_warning);
                icon.setImageResource(R.drawable.ic_status_warning);
                break;
            case TYPE_ERROR:
                root.setBackgroundResource(R.drawable.bg_custom_message_error);
                icon.setImageResource(R.drawable.ic_status_error);
                break;
            case TYPE_INFO:
            default:
                root.setBackgroundResource(R.drawable.bg_custom_message_info);
                icon.setImageResource(R.drawable.ic_status_info);
                break;
        }

        Toast toast = new Toast(activity.getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(view);
        toast.setGravity(gravity, 0, dpToPx(activity, yOffsetDp));
        toast.show();
    }

    private static int dpToPx(Activity activity, int dp) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
