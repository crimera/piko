package app.morphe.extension.xlite.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Reusable List Item component.
 * Supports leading icon badge (40x40dp tonal box), title, supporting subtitle text,
 * and trailing action buttons.
 */
public class ListItem extends LinearLayout {

    private final Theme.SettingsSnapshot themeSettings;
    private FrameLayout leadingContainer;
    private IconView leadingIconView;
    private LinearLayout textContainer;
    private TextView titleView;
    private TextView subtitleView;
    private FrameLayout trailingContainer;

    public ListItem(Context context) {
        this(context, Theme.snapshot());
    }

    public ListItem(Context context, Theme.SettingsSnapshot themeSettings) {
        super(context);
        if (themeSettings == null) {
            throw new IllegalArgumentException("Theme settings snapshot is required");
        }
        this.themeSettings = themeSettings;
        init();
    }

    private void init() {
        Context context = getContext();
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Theme.dpToPx(context, 72f));

        int padHoriz = Theme.dpToPx(context, 16f);
        int padVert = Theme.dpToPx(context, 12f);
        setPadding(padHoriz, padVert, padHoriz, padVert);

        // Rectangular Ripple Background (no rounded corners)
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.BLACK);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(themeSettings.rippleColor(context)),
                null,
                mask
        );
        setBackground(ripple);
        setClickable(true);
        setFocusable(true);

        // 1. Leading Container (40dp x 40dp Tonal Badge)
        leadingContainer = new FrameLayout(context);
        int badgeSize = Theme.dpToPx(context, 40f);
        LinearLayout.LayoutParams leadingParams = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        leadingParams.setMarginEnd(Theme.dpToPx(context, 16f));
        leadingContainer.setLayoutParams(leadingParams);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(Theme.dpToPx(context, 12f));
        badgeBg.setColor(themeSettings.surfaceVariant(context));
        leadingContainer.setBackground(badgeBg);

        leadingIconView = new IconView(context);
        int iconSize = Theme.dpToPx(context, 24f);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        leadingContainer.addView(leadingIconView, iconParams);
        addView(leadingContainer);

        // 2. Text Container (Title + Subtitle)
        textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMarginEnd(Theme.dpToPx(context, 12f));
        textContainer.setLayoutParams(textParams);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setTextColor(themeSettings.primaryText(context));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textContainer.addView(titleView);

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitleView.setTextColor(themeSettings.secondaryText(context));
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setPadding(0, Theme.dpToPx(context, 2f), 0, 0);
        subtitleView.setVisibility(View.GONE);
        textContainer.addView(subtitleView);

        addView(textContainer);

        // 3. Trailing Container
        trailingContainer = new FrameLayout(context);
        LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        trailingContainer.setLayoutParams(trailingParams);
        trailingContainer.setVisibility(View.GONE);
        addView(trailingContainer);
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    public void setSubtitle(@Nullable CharSequence subtitle) {
        if (TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }
    }

    public void setLeadingIcon(IconView.IconType iconType, int iconColor, @Nullable Integer containerBgColor) {
        leadingIconView.setIconType(iconType);
        leadingIconView.setIconColor(iconColor);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(Theme.dpToPx(getContext(), 12f));
        badgeBg.setColor(containerBgColor != null
                ? containerBgColor
                : themeSettings.surfaceVariant(getContext()));
        leadingContainer.setBackground(badgeBg);
    }

    public void setTrailingView(@Nullable View view) {
        trailingContainer.removeAllViews();
        if (view == null) {
            trailingContainer.setVisibility(View.GONE);
        } else {
            trailingContainer.addView(view);
            trailingContainer.setVisibility(View.VISIBLE);
        }
    }

    public View createTrailingIconButton(IconView.IconType iconType, int iconColor, OnClickListener listener) {
        Context context = getContext();
        FrameLayout btnContainer = new FrameLayout(context);
        int btnSize = Theme.dpToPx(context, 40f);
        btnContainer.setLayoutParams(new ViewGroup.LayoutParams(btnSize, btnSize));

        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.BLACK);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(themeSettings.rippleColor(context)),
                null,
                mask
        );
        btnContainer.setBackground(ripple);
        btnContainer.setClickable(true);
        btnContainer.setFocusable(true);

        IconView iconView = new IconView(context, iconType, iconColor);
        int iconSize = Theme.dpToPx(context, 20f);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        btnContainer.addView(iconView, iconParams);

        if (listener != null) {
            btnContainer.setOnClickListener(listener);
        }

        setTrailingView(btnContainer);
        return btnContainer;
    }
}
