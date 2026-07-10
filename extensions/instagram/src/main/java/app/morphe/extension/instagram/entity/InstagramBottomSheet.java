/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A lightweight bottom-sheet style dialog that visually matches Instagram's native
 * "more options" sheet (rounded top corners, drag handle, slide-up animation, dark theme)
 * without needing to reflect into Instagram's own obfuscated dialog classes.
 *
 * Fully self-contained — safe against Instagram app updates/obfuscation changes.
 */
public class InstagramBottomSheet {

    private static final int BACKGROUND_COLOR = Color.parseColor("#1C1C1E");
    private static final int HANDLE_COLOR = Color.parseColor("#4D4D4D");
    private static final int TEXT_COLOR = Color.WHITE;
    private static final int DESTRUCTIVE_TEXT_COLOR = Color.parseColor("#ED4956");

    private final Context context;
    private final Dialog dialog;
    private final LinearLayout container;

    public InstagramBottomSheet(Context context) {
        this.context = context;
        this.dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(roundedTopBackground());
        container.setPadding(0, dp(8), 0, dp(24));

        // Drag handle, like native IG bottom sheets.
        View handle = new View(context);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = dp(10);
        handleParams.bottomMargin = dp(10);
        handle.setBackground(pillDrawable());
        container.addView(handle, handleParams);

        dialog.setContentView(container);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.gravity = Gravity.BOTTOM;
            window.setAttributes(lp);
        }

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
    }

    /** Adds a regular (non-destructive) row. */
    public void addItem(CharSequence text, Runnable onClick) {
        addItem(text, onClick, false);
    }

    /** Adds a row; pass destructive=true for red text (e.g. "Report", "Block"). */
    public void addItem(CharSequence text, Runnable onClick, boolean destructive) {
        TextView item = new TextView(context);
        item.setText(text);
        item.setTextColor(destructive ? DESTRUCTIVE_TEXT_COLOR : TEXT_COLOR);
        item.setTextSize(16);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(24), dp(14), dp(24), dp(14));

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        if (outValue.resourceId != 0) {
            item.setBackgroundResource(outValue.resourceId);
        }

        item.setOnClickListener(v -> {
            dismiss();
            if (onClick != null) onClick.run();
        });

        container.addView(item, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    public void show() {
        dialog.show();
        View decor = dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
        if (decor != null) {
            decor.setTranslationY(dp(400));
            decor.animate()
                    .translationY(0)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public Dialog getDialog() {
        return dialog;
    }

    // ---------- helpers ----------

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    private GradientDrawable roundedTopBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(BACKGROUND_COLOR);
        float r = dp(16);
        drawable.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return drawable;
    }

    private GradientDrawable pillDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(HANDLE_COLOR);
        drawable.setCornerRadius(dp(2));
        return drawable;
    }
}
