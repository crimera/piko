/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.instagram.constants.UI;

public final class InstagramSearchStyle {
    private static final int NO_RESULTS_PORTRAIT_TOP_MARGIN_DP = 132;
    private static final int NO_RESULTS_LANDSCAPE_TOP_MARGIN_DP = 24;

    private InstagramSearchStyle() {}

    public static void applyInputStyle(EditText input) {
        input.setHighlightColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(InstagramPreferenceStyle.primaryTextColor());
        cursor.setSize(
                InstagramPreferenceStyle.dp(input.getContext(), 2),
                InstagramPreferenceStyle.dp(input.getContext(), 24)
        );
        input.setTextCursorDrawable(cursor);
    }

    // Keep search input backgrounds consistent while preserving IME underlines and composition state.
    public static void removeBackgroundSpans(Editable editable) {
        if (editable == null || editable.length() == 0) return;
        for (BackgroundColorSpan span : editable.getSpans(0, editable.length(), BackgroundColorSpan.class)) {
            editable.removeSpan(span);
        }
    }

    public static View createNoResultsView(Context context) {
        FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        overlay.setClickable(true);

        LinearLayout message = new LinearLayout(context);
        message.setOrientation(LinearLayout.VERTICAL);
        message.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconPadding = InstagramPreferenceStyle.dp(context, 12);
        icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        UI.setThemedIcon(icon, UI.DRAWABLE_SEARCH, "igds_color_secondary_text");
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        message.addView(icon, new LinearLayout.LayoutParams(
                InstagramPreferenceStyle.dp(context, 96),
                InstagramPreferenceStyle.dp(context, 96)
        ));

        TextView title = new TextView(context);
        title.setText(str("piko_settings_search_no_results"));
        title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        title.setIncludeFontPadding(false);
        title.setPadding(
                InstagramPreferenceStyle.dp(context, 24),
                InstagramPreferenceStyle.dp(context, 8),
                InstagramPreferenceStyle.dp(context, 24),
                0
        );
        message.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        messageParams.gravity = android.view.Gravity.TOP
                | android.view.Gravity.CENTER_HORIZONTAL;
        int orientation = context.getResources().getConfiguration().orientation;
        int topMarginDp = orientation == Configuration.ORIENTATION_LANDSCAPE
                ? NO_RESULTS_LANDSCAPE_TOP_MARGIN_DP
                : NO_RESULTS_PORTRAIT_TOP_MARGIN_DP;
        messageParams.topMargin = InstagramPreferenceStyle.dp(context, topMarginDp);
        overlay.addView(message, messageParams);
        return overlay;
    }
}
