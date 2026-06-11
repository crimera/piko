/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.morphe.extension.crimera.constants;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

public class TooltipHelper {

    public static void showPersistentTooltip(Context context, View anchorView, String text) {
        if (anchorView == null || context == null) return;
        anchorView.post(new Runnable() {
            @Override
            public void run() {
                createAndShowTooltip(context, anchorView, text);
            }
        });
    }

    private static void createAndShowTooltip(Context context, View anchorView, String text) {

        android.widget.FrameLayout container = new android.widget.FrameLayout(context);

        // Add generous vertical padding to the container so the bounce isn't clipped
        // 40px on top and bottom gives plenty of room for a 20px translation
        container.setPadding(0, 30, 0, 30);

        // Design the styled TextView for the tooltip
        TextView tooltipTextView = new TextView(context);
        tooltipTextView.setText(text);
        tooltipTextView.setTextColor(Color.WHITE);
        tooltipTextView.setTextSize(16);
        tooltipTextView.setPadding(32, 16, 32, 16);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(16f);
        shape.setColor(Color.parseColor("#E6212121"));
        tooltipTextView.setBackground(shape);

        // Add the TextView to our padded container
        android.widget.FrameLayout.LayoutParams layoutParams = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = android.view.Gravity.CENTER;
        container.addView(tooltipTextView, layoutParams);

        // Wrap the CONTAINER inside the PopupWindow (not the TextView directly)
        PopupWindow popupWindow = new PopupWindow(
                container,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        popupWindow.setOutsideTouchable(true);

        // Measure screen metrics & anchor positioning
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int screenHeight = displayMetrics.heightPixels;

        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);
        int anchorY = location[1];

        // Pre-measure the container size so our math remains flawless
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int tooltipWidth = container.getMeasuredWidth();
        int tooltipHeight = container.getMeasuredHeight();

        // Smart Positioning Logic (Screen Edge Awareness)
        int xOffset = (anchorView.getWidth() - tooltipWidth) / 2;
        int yOffset;
        boolean positionAtBottom;

        if (anchorY < (screenHeight * 0.35)) {
            // Offset adjusted slightly to compensate for the container's internal padding
            yOffset = -30;
            positionAtBottom = true;
        } else {
            // Offset adjusted slightly to compensate for the container's internal padding
            yOffset = -(anchorView.getHeight() + tooltipHeight - 30);
            positionAtBottom = false;
        }

        // Show the popup window safely
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset);

        // Smooth Animation targeting the TEXTVIEW (not the container)
        float startDelta = 0f;
        float endDelta = positionAtBottom ? 20f : -20f;

        // We animate tooltipTextView. The parent container stays wide open, preventing any crops!
        ObjectAnimator bounceAnim = ObjectAnimator.ofFloat(
                tooltipTextView,
                "translationY",
                startDelta,
                endDelta
        );

        bounceAnim.setDuration(600);
        bounceAnim.setRepeatMode(ValueAnimator.REVERSE);
        bounceAnim.setRepeatCount(ValueAnimator.INFINITE);
        bounceAnim.start();
    }
}