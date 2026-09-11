/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.preference.Preference;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.shared.ResourceUtils;

public final class InstagramPreferenceStyle {
    static final long NAVIGATION_DRAG_DURATION_MS = 120L;
    private static final String TAG_TITLE = "piko_instagram_pref_title";
    private static final String TAG_SUMMARY = "piko_instagram_pref_summary";
    private static final String TAG_SWITCH = "piko_instagram_pref_switch";
    private static final String TAG_TRAILING = "piko_instagram_pref_trailing";
    private static final String IGDS_SWITCH_CLASS_NAME =
            "com.instagram.igds.components.switchbutton.IgdsSwitch";

    public static final int TRAILING_SWITCH = 1;
    public static final int TRAILING_CHEVRON = 2;
    private static final int TRAILING_CHECK = 3;

    private InstagramPreferenceStyle() {
    }

    private static String chevronDrawableName(int layoutDirection) {
        return layoutDirection == View.LAYOUT_DIRECTION_RTL
                ? UI.DRAWABLE_CHEVRON_RIGHT_RTL
                : UI.DRAWABLE_CHEVRON_RIGHT;
    }

    private static int topPaddingDp(int trailingType, boolean hasSummary) {
        return hasSummary || trailingType == TRAILING_SWITCH ? 10 : 15;
    }

    private static int bottomPaddingDp(int trailingType, boolean hasSummary) {
        return hasSummary || trailingType == TRAILING_SWITCH ? 20 : 15;
    }

    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    public static void applyToolbarLayout(
            Context context,
            LinearLayout toolbar,
            ImageView back,
            TextView title,
            boolean isRootSettings
    ) {
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(context, 15), dp(context, 10),
                dp(context, 15), dp(context, 8));
        toolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 70)));

        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setPaddingRelative(0, 0, dp(context, 16), 0);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                dp(context, 44), dp(context, 44));
        backParams.gravity = Gravity.CENTER_VERTICAL;
        back.setLayoutParams(backParams);

        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, isRootSettings ? 25 : 20);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        title.setMaxLines(1);
        if (!isRootSettings) {
            title.setAutoSizeTextTypeUniformWithConfiguration(
                    18, 20, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.gravity = Gravity.CENTER_VERTICAL;
        titleParams.leftMargin = dp(context, 7);
        title.setLayoutParams(titleParams);
    }

    /** Matches platform preference dialogs to Instagram's resolved theme. */
    public static Context dialogContext(Context context) {
        int themeRes = UI.isDarkMode()
                ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light;
        return new android.view.ContextThemeWrapper(context, themeRes);
    }

    public static int backgroundColor() {
        int primaryBackground = UI.getThemedColour("igds_color_primary_background");

        return UI.isDarkMode()
                ? ResourceUtils.getColor("igds_prism_black", primaryBackground)
                : primaryBackground;
    }

    public static int pressedBackgroundColor() {
        int fallback = UI.getThemedColour("igds_color_secondary_background");
        return ResourceUtils.getColor("igds_elevated_highlight_background", fallback);
    }

    public static int primaryTextColor() {
        return UI.getThemedColour("igds_color_primary_text");
    }

    public static int secondaryTextColor() {
        return UI.getThemedColour("igds_color_secondary_text");
    }

    public static int disabledTextColor() {
        return UI.getThemedColour("igds_color_separator");
    }

    public static int selectionColor() {
        return ResourceUtils.getColor("igds_primary_button", 0xff0095f6);
    }

    public static void applySystemBarStyle(Activity activity) {
        activity.getWindow().setStatusBarColor(backgroundColor());
        activity.getWindow().setNavigationBarColor(backgroundColor());

        int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
        if (UI.isDarkMode()) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    public static View createPreferenceView(Context context, int trailingType) {
        return createPreferenceView(context,trailingType,null);
    }

    public static View createPreferenceView(Context context, int trailingType, String iconResName) {
        PreferenceRow row = new PreferenceRow(context, trailingType);
        row.setOrientation(trailingType == TRAILING_SWITCH ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 78));
        row.setPadding(dp(context, 17), dp(context, 10), dp(context, 17), dp(context, 10));
        row.setBackgroundColor(backgroundColor());
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if(iconResName!=null){
            row.setIcon(iconResName);
        }

        if (trailingType == TRAILING_SWITCH) {
            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(titleRow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            row.setHighlightView(titleRow);

            TextView title = new TextView(context);
            title.setTag(TAG_TITLE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTextColor(primaryTextColor());
            title.setIncludeFontPadding(true);
            title.setSingleLine(false);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            titleParams.rightMargin = dp(context, 14);
            titleRow.addView(title, titleParams);

            CompoundButton switchView = createNativeSwitch(nativeSwitchContext(context));
            switchView.setClickable(false);
            switchView.setFocusable(false);
            switchView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            switchView.setTag(TAG_SWITCH);
            titleRow.addView(switchView, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 32)));

            TextView summary = new TextView(context);
            summary.setTag(TAG_SUMMARY);
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            summary.setTextColor(secondaryTextColor());
            summary.setLineSpacing(dp(context, 1), 1.0f);
            summary.setPadding(0, dp(context, 10), 0, 0);
            row.addView(summary, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            return row;
        }

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.rightMargin = dp(context, 14);
        row.addView(textColumn, textParams);

        TextView title = new TextView(context);
        title.setTag(TAG_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(primaryTextColor());
        title.setIncludeFontPadding(true);
        title.setSingleLine(false);
        textColumn.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView summary = new TextView(context);
        summary.setTag(TAG_SUMMARY);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summary.setTextColor(secondaryTextColor());
        summary.setLineSpacing(dp(context, 1), 1.0f);
        summary.setPadding(0, dp(context, 10), 0, 0);
        textColumn.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (trailingType == TRAILING_CHEVRON) {
            ImageView trailing = new ImageView(context);
            trailing.setTag(TAG_TRAILING);
            UI.setThemedIcon(
                    trailing,
                    chevronDrawableName(
                            context.getResources().getConfiguration().getLayoutDirection()
                    ),
                    "igds_color_secondary_icon"
            );
            trailing.setScaleType(ImageView.ScaleType.CENTER);
            row.addView(trailing, new LinearLayout.LayoutParams(dp(context, 22), dp(context, 34)));
        }

        return row;
    }

    public static void bindText(Preference preference, View view) {
        Context context = view.getContext();
        boolean enabled = preference.isEnabled();

        TextView title = view.findViewWithTag(TAG_TITLE);
        TextView summary = view.findViewWithTag(TAG_SUMMARY);
        View trailing = view.findViewWithTag(TAG_TRAILING);

        int titleColor = enabled ? primaryTextColor() : disabledTextColor();
        int summaryColor = enabled ? secondaryTextColor() : disabledTextColor();

        if (title != null) {
            title.setText(preference.getTitle());
            title.setTextColor(titleColor);
            title.setEnabled(enabled);
        }

        if (summary != null) {
            CharSequence summaryText = preference.getSummary();
            boolean hasSummary = !TextUtils.isEmpty(summaryText);
            summary.setText(hasSummary ? summaryText : "");
            summary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            summary.setTextColor(summaryColor);
            summary.setEnabled(enabled);
            if (view instanceof PreferenceRow) {
                ((PreferenceRow) view).setHasSummary(hasSummary);
            }
        }

        if (trailing != null) {
            trailing.setEnabled(enabled);
            if (trailing instanceof ImageView) {
                int trailingColor = enabled
                        ? UI.getThemedColour("igds_color_secondary_icon")
                        : disabledTextColor();
                ((ImageView) trailing).setColorFilter(new PorterDuffColorFilter(
                        trailingColor,
                        PorterDuff.Mode.SRC_ATOP
                ));
            }
            trailing.invalidate();
        }

        view.setEnabled(enabled);
    }

    public static void setTrailingVisible(View view, boolean visible) {
        View trailing = view.findViewWithTag(TAG_TRAILING);
        if (trailing != null) {
            trailing.setVisibility(visible ? View.VISIBLE : View.GONE);
            trailing.invalidate();
        }
    }

    public static void bindIcon(View view, String iconResName) {
        if (view instanceof PreferenceRow) {
            ((PreferenceRow) view).setIcon(iconResName);
        }
    }

    public static void setPressedHighlightEnabled(View view, boolean enabled) {
        if (view instanceof PreferenceRow) {
            ((PreferenceRow) view).setPressedHighlightEnabled(enabled);
        }
    }

    public static CompoundButton findSwitch(View view) {
        return view.findViewWithTag(TAG_SWITCH);
    }

    public static View createNavigationCheckRow(
            Context context,
            CharSequence title,
            String iconName,
            boolean checked
    ) {
        PreferenceRow row = new PreferenceRow(context, TRAILING_CHECK);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 56));
        row.setPadding(dp(context, 12), dp(context, 4), dp(context, 8), dp(context, 4));

        CheckBox checkBox = new CheckBox(context);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        checkBox.setGravity(Gravity.CENTER);
        checkBox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        checkBox.setChecked(checked);
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(context, 32), dp(context, 48)));

        ImageView iconView = new ImageView(context);
        UI.setThemedIcon(iconView, iconName);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                dp(context, 24),
                dp(context, 24)
        );
        iconParams.setMarginStart(dp(context, 8));
        iconParams.setMarginEnd(dp(context, 12));
        row.addView(iconView, iconParams);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        titleView.setTextColor(primaryTextColor());
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
        );
        titleParams.setMarginEnd(dp(context, 8));
        row.addView(titleView, titleParams);
        row.setHighlightView(titleView);

        FrameLayout dragTarget = new FrameLayout(context);
        dragTarget.setTag(TAG_TRAILING);
        dragTarget.setContentDescription(title);
        DragHandleView dragIcon = new DragHandleView(context);
        dragIcon.setAlpha(0.72f);
        FrameLayout.LayoutParams dragIconParams = new FrameLayout.LayoutParams(
                dp(context, 24),
                dp(context, 24),
                Gravity.CENTER
        );
        dragTarget.addView(dragIcon, dragIconParams);
        row.addView(dragTarget, new LinearLayout.LayoutParams(
                dp(context, 48),
                dp(context, 48)
        ));

        row.setSwitchAccessibilityChecked(checked);
        row.setContentDescription(title);

        return row;
    }

    public static boolean isNavigationDragHandleHit(View row, float rawX, float rawY) {
        View dragTarget = row.findViewWithTag(TAG_TRAILING);
        if (dragTarget == null || dragTarget.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        dragTarget.getLocationOnScreen(location);
        return rawX >= location[0]
                && rawX < location[0] + dragTarget.getWidth()
                && rawY >= location[1]
                && rawY < location[1] + dragTarget.getHeight();
    }

    public static void setNavigationRowDragging(
            View view,
            boolean dragging,
            boolean animate
    ) {
        if (view instanceof PreferenceRow
                && ((PreferenceRow) view).trailingType == TRAILING_CHECK) {
            ((PreferenceRow) view).setDragState(dragging, animate);
        }
    }

    public static void setNativeSwitchChecked(
            CompoundButton switchView,
            boolean checked,
            boolean animate
    ) {
        if (!animate || switchView.isChecked() == checked) {
            switchView.setChecked(checked);
            return;
        }

        try {
            switchView.getClass()
                    .getMethod("setCheckedAnimated", boolean.class)
                    .invoke(switchView, checked);
        } catch (ReflectiveOperationException ignored) {
            switchView.setChecked(checked);
        }
    }

    private static Context nativeSwitchContext(Context context) {
        Configuration overrideConfiguration = new Configuration();
        overrideConfiguration.uiMode = UI.isDarkMode()
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;

        ContextThemeWrapper switchContext = new ContextThemeWrapper(context, 0);
        switchContext.applyOverrideConfiguration(overrideConfiguration);
        switchContext.getTheme().setTo(context.getTheme());
        return switchContext;
    }

    private static CompoundButton createNativeSwitch(Context context) {
        try {
            CompoundButton switchView = Class
                    .forName(IGDS_SWITCH_CLASS_NAME, true, context.getClassLoader())
                    .asSubclass(CompoundButton.class)
                    .getConstructor(Context.class)
                    .newInstance(context);
            return switchView;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new IllegalStateException("Unable to create Instagram's IgdsSwitch", exception);
        }
    }

    public static void bindSwitchAccessibility(View view, boolean checked) {
        if (view instanceof PreferenceRow) {
            ((PreferenceRow) view).setSwitchAccessibilityChecked(checked);
        }
    }

    public static boolean consumeSwitchClickAllowed(View view) {
        if (view instanceof PreferenceRow) {
            return ((PreferenceRow) view).consumeSwitchClickAllowed();
        }
        return true;
    }

    private static class PreferenceRow extends LinearLayout {
        private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect switchHitRect = new Rect();
        private final int trailingType;
        private View highlightView;
        private boolean pressedHighlightEnabled;
        private boolean pressedHighlightAllowed;
        private boolean switchClickAllowed = true;
        private boolean drawPressedHighlight;
        private boolean drawDragHighlight;
        private int dragOverlayColor;
        private boolean switchAccessibilityChecked;
        private ImageView iconView;


        PreferenceRow(Context context, int trailingType) {
            this(context, trailingType, null);
        }

        PreferenceRow(Context context, int trailingType, String iconResName) {
            super(context);
            this.trailingType = trailingType;
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setWillNotDraw(false);
            if (trailingType == TRAILING_CHECK) {
                setOutlineProvider(ViewOutlineProvider.BOUNDS);
                setClipToOutline(false);
            }
        }

        private void initIconView(Context context) {
            iconView = new ImageView(context);

            int iconSize = dp(context, 24);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(iconSize, iconSize);

            params.setMarginStart(dp(context, 16));
            params.setMarginEnd(dp(context, 16));
            iconView.setLayoutParams(params);

            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(iconView, 0);
        }

        void setIcon(String iconResName) {
            if (iconResName == null) {
                if (iconView != null) {
                    iconView.setImageDrawable(null);
                    iconView.setVisibility(View.GONE);
                }
                return;
            }
            if (iconView == null) {
                initIconView(getContext());
            }
            UI.setThemedIcon(iconView, iconResName);
            iconView.setVisibility(View.VISIBLE);
        }

        void setHighlightView(View highlightView) {
            this.highlightView = highlightView;
            pressedHighlightEnabled = true;
        }

        void setPressedHighlightEnabled(boolean enabled) {
            pressedHighlightEnabled = enabled;
            if (!enabled) {
                pressedHighlightAllowed = false;
                setDrawPressedHighlight(false);
            }
        }

        void setSwitchAccessibilityChecked(boolean checked) {
            switchAccessibilityChecked = checked;
        }

        void setDragState(boolean dragging, boolean animateState) {
            drawDragHighlight = dragging;
            dragOverlayColor = UI.isDarkMode() ? 0xff3f3f3f : 0x0d000000;
            if (dragging && !UI.isDarkMode()) {
                TypedArray colors = getContext().obtainStyledAttributes(
                        new int[]{android.R.attr.colorBackgroundFloating}
                );
                setBackgroundColor(colors.getColor(0, backgroundColor()) | 0xff000000);
                colors.recycle();
            } else {
                setBackground(null);
            }
            invalidate();

            animate().cancel();
            float translationZ = dragging
                    ? dp(getContext(), 5f) : 0f;
            if (animateState) {
                animate()
                        .translationZ(translationZ)
                        .setDuration(NAVIGATION_DRAG_DURATION_MS)
                        .start();
            } else {
                setTranslationZ(translationZ);
            }
        }

        void setHasSummary(boolean hasSummary) {
            int topPadding = dp(getContext(), topPaddingDp(trailingType, hasSummary));
            int bottomPadding = dp(getContext(), bottomPaddingDp(trailingType, hasSummary));
            setMinimumHeight(dp(getContext(), hasSummary ? 80 : 62));
            setPadding(getPaddingLeft(), topPadding, getPaddingRight(), bottomPadding);
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);
            if (trailingType == TRAILING_SWITCH || trailingType == TRAILING_CHECK) {
                event.setClassName(trailingType == TRAILING_SWITCH
                        ? "android.widget.Switch"
                        : "android.widget.CheckBox");
                event.setChecked(switchAccessibilityChecked);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (trailingType == TRAILING_SWITCH || trailingType == TRAILING_CHECK) {
                info.setClassName(trailingType == TRAILING_SWITCH
                        ? "android.widget.Switch"
                        : "android.widget.CheckBox");
                info.setCheckable(true);
                info.setChecked(switchAccessibilityChecked);
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pressedHighlightAllowed = shouldDrawPressedHighlight(event.getX(), event.getY());
                switchClickAllowed = shouldHandleSwitchClick(event.getY());
            } else if (action == MotionEvent.ACTION_CANCEL) {
                pressedHighlightAllowed = false;
                setDrawPressedHighlight(false);
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            setDrawPressedHighlight(pressed && pressedHighlightAllowed);
            if (!pressed) {
                pressedHighlightAllowed = false;
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawPressedHighlight || drawDragHighlight) {
                pressedPaint.setStyle(Paint.Style.FILL);
                pressedPaint.setColor(
                        drawDragHighlight ? dragOverlayColor : pressedBackgroundColor()
                );
                int top = highlightTop();
                int bottom = highlightBottom();
                canvas.drawRect(0, top, getWidth(), bottom, pressedPaint);
            }
            super.dispatchDraw(canvas);
        }

        private boolean shouldDrawPressedHighlight(float x, float y) {
            if (!pressedHighlightEnabled || !isEnabled()) {
                return false;
            }

            if (trailingType != TRAILING_SWITCH) {
                return true;
            }

            if (highlightView == null || y < highlightTop() || y > highlightBottom()) {
                return false;
            }

            View excludedView = findViewWithTag(TAG_SWITCH);
            if (excludedView == null) {
                return true;
            }

            int horizontalSlop = dp(getContext(), 10);
            int verticalSlop = dp(getContext(), 3);
            switchHitRect.set(0, 0, excludedView.getWidth(), excludedView.getHeight());
            offsetDescendantRectToMyCoords(excludedView, switchHitRect);
            switchHitRect.inset(-horizontalSlop, -verticalSlop);
            return !switchHitRect.contains(Math.round(x), Math.round(y));
        }

        private boolean shouldHandleSwitchClick(float y) {
            return trailingType != TRAILING_SWITCH || highlightView == null || (y >= highlightTop() && y <= highlightBottom());
        }

        private boolean consumeSwitchClickAllowed() {
            boolean allowed = switchClickAllowed;
            switchClickAllowed = true;
            return allowed;
        }

        private int highlightTop() {
            if (highlightView == null) {
                return 0;
            }
            return Math.max(0, highlightView.getTop() - dp(getContext(), 10));
        }

        private int highlightBottom() {
            if (highlightView == null) {
                return getHeight();
            }
            return Math.min(getHeight(), highlightView.getBottom() + dp(getContext(), 10));
        }

        private void setDrawPressedHighlight(boolean drawPressedHighlight) {
            if (this.drawPressedHighlight == drawPressedHighlight) {
                return;
            }
            this.drawPressedHighlight = drawPressedHighlight;
            invalidate();
        }
    }

    private static final class DragHandleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DragHandleView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float lineWidth = dp(getContext(), 16f);
            float stroke = dp(getContext(), 2f);
            float left = (getWidth() - lineWidth) / 2.0f;
            float right = left + lineWidth;
            float radius = stroke / 2.0f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(secondaryTextColor());
            for (int line = 0; line < 3; line++) {
                float centerY = dp(
                        getContext(),
                        8f + 4f * line
                );
                canvas.drawRoundRect(
                        left,
                        centerY - radius,
                        right,
                        centerY + radius,
                        radius,
                        radius,
                        paint
                );
            }
        }
    }
}
