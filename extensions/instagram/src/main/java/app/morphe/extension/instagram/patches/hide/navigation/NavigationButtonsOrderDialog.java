/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.hide.navigation;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Utils;

/**
 * Drag-to-reorder dialog for the navigation tabs. Not a ListView/BaseAdapter:
 * view recycling during an active drag detaches the View receiving the touch
 * stream mid-gesture. Rows are plain LinearLayout children, rebuilt once per
 * drop.
 */
public final class NavigationButtonsOrderDialog {

    private static final Tab[] TABS = {
            new Tab("fragment_feed", "piko_nav_tab_feed", "tab_home_drawable", Settings.HIDE_NAVIGATION_FEED),
            new Tab("fragment_clips", "piko_nav_tab_reels", "tab_clips_drawable", Settings.HIDE_NAVIGATION_REELS),
            new Tab("fragment_direct_tab", "piko_nav_tab_direct", "tab_prism_direct_drawable", Settings.HIDE_NAVIGATION_DIRECT),
            new Tab("fragment_search", "piko_nav_tab_search", "tab_search_drawable", Settings.HIDE_NAVIGATION_SEARCH),
            new Tab("fragment_profile", "piko_nav_tab_profile", "tab_profile_drawable", Settings.HIDE_NAVIGATION_PROFILE),
            new Tab("fragment_share", "piko_nav_tab_create", "tab_camera_drawable", Settings.HIDE_NAVIGATION_CREATE),
            new Tab("fragment_news", "piko_nav_tab_news", "tab_activity_heart_drawable", Settings.HIDE_NAVIGATION_NEWS),
    };

    private NavigationButtonsOrderDialog() {
    }

    public static void show(Context context) {
        Context themed = InstagramPreferenceStyle.dialogContext(context);
        List<Tab> items = currentState();

        LinearLayout root = new LinearLayout(themed);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(InstagramPreferenceStyle.backgroundColor());
        cardBackground.setCornerRadius(dp(themed, 20));
        root.setBackground(cardBackground);

        int pad = dp(themed, 20);
        TextView title = new TextView(themed);
        title.setText(str("piko_reorder_navigation_buttons_title"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        title.setPadding(pad, pad, pad, dp(themed, 12));
        root.addView(title);

        ScrollView scrollView = new ScrollView(themed) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxHeightPx = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
                int cappedHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, cappedHeightSpec);
            }
        };
        LinearLayout rowsContainer = new LinearLayout(themed);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(rowsContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Dialog dialog = new Dialog(themed, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar_MinWidth);
        dialog.setContentView(root);

        DragController controller = new DragController(themed, scrollView, rowsContainer, items);
        controller.rebuildRows();

        LinearLayout buttonRow = new LinearLayout(themed);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(pad, dp(themed, 12), pad, dp(themed, 16));

        TextView reset = actionText(themed, str("piko_reorder_navigation_buttons_reset"));
        TextView cancel = actionText(themed, str("piko_cancel"));
        TextView set = actionText(themed, str("piko_ok"));

        reset.setOnClickListener(v -> controller.resetToDefault());
        cancel.setOnClickListener(v -> dialog.dismiss());
        set.setOnClickListener(v -> {
            String prevOrder = Pref.navigationButtonsOrder();
            String nextOrder = String.join(",", controller.currentKeyOrder());
            boolean changed = !nextOrder.equals(prevOrder);

            dialog.dismiss();
            if (changed) {
                new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                        .setTitle(str("piko_restart_app"))
                        .setCancelable(false)
                        .setPositiveButton(str("piko_ok"), (d, w) -> {
                            SharedPref.setStringPref(Settings.NAVIGATION_BUTTONS_ORDER.key, nextOrder);
                            SettingsRestart.markChanged(prevOrder, nextOrder);
                            Utils.restartApp(Utils.getContext());
                        })
                        .setNegativeButton(str("piko_cancel"), null)
                        .show();
            }
        });

        LinearLayout.LayoutParams flexParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        buttonRow.addView(reset, flexParams);
        buttonRow.addView(cancel, flexParams);
        buttonRow.addView(set, flexParams);
        root.addView(buttonRow);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (themed.getResources().getDisplayMetrics().widthPixels * 0.88f);
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }

        // Dialog isn't Activity-managed, so it survives a MaterialYouTheme-triggered
        // activity.recreate() and crashes on the torn-down window. Auto-dismiss on destroy.
        Activity hostActivity = unwrapActivity(context);
        if (hostActivity != null) {
            Application app = hostActivity.getApplication();
            Application.ActivityLifecycleCallbacks lifecycleGuard =
                    new Application.ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityDestroyed(Activity activity) {
                            if (activity == hostActivity && dialog.isShowing()) {
                                dialog.dismiss();
                            }
                        }

                        @Override public void onActivityCreated(Activity a, android.os.Bundle b) {}
                        @Override public void onActivityStarted(Activity a) {}
                        @Override public void onActivityResumed(Activity a) {}
                        @Override public void onActivityPaused(Activity a) {}
                        @Override public void onActivityStopped(Activity a) {}
                        @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle b) {}
                    };
            app.registerActivityLifecycleCallbacks(lifecycleGuard);
            dialog.setOnDismissListener(d -> app.unregisterActivityLifecycleCallbacks(lifecycleGuard));
        }

        dialog.show();
    }

    private static Activity unwrapActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private static TextView actionText(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        tv.setPadding(0, dp(context, 8), 0, dp(context, 8));
        return tv;
    }

    private static List<Tab> currentState() {
        List<String> allKeys = new ArrayList<>();
        for (Tab t : TABS) allKeys.add(t.key);

        List<String> ordered = new ArrayList<>();
        String saved = Pref.navigationButtonsOrder();
        if (saved != null && !saved.isEmpty()) {
            for (String key : saved.split(",")) {
                if (allKeys.remove(key)) ordered.add(key);
            }
        }
        ordered.addAll(allKeys);

        List<Tab> result = new ArrayList<>(ordered.size());
        for (String key : ordered) {
            result.add(tabByKey(key));
        }
        return result;
    }

    private static Tab tabByKey(String key) {
        for (Tab t : TABS) if (t.key.equals(key)) return t;
        throw new IllegalStateException("Unknown tab key: " + key);
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    private static final class Tab {
        final String key;
        final String labelResKey;
        final String iconResName;
        final BooleanSetting hideSetting;

        Tab(String key, String labelResKey, String iconResName, BooleanSetting hideSetting) {
            this.key = key;
            this.labelResKey = labelResKey;
            this.iconResName = iconResName;
            this.hideSetting = hideSetting;
        }
    }

    /** Owns the row Views + drag gesture. Rows rebuild from `items` once per drop. */
    private static final class DragController {
        private final Context context;
        private final ScrollView scrollView;
        private final LinearLayout container;
        private final List<Tab> items;
        private final int touchSlop;

        DragController(Context context, ScrollView scrollView, LinearLayout container, List<Tab> items) {
            this.context = context;
            this.scrollView = scrollView;
            this.container = container;
            this.items = items;
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        List<String> currentKeyOrder() {
            List<String> keys = new ArrayList<>(items.size());
            for (Tab t : items) keys.add(t.key);
            return keys;
        }

        void resetToDefault() {
            items.clear();
            for (Tab t : TABS) items.add(t);
            rebuildRows();
        }

        void rebuildRows() {
            container.removeAllViews();
            for (int i = 0; i < items.size(); i++) {
                container.addView(buildRow(i), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        // News/Create hide toggles aren't confirmed working yet — block reordering
        // them until that's sorted, so a drag can't be blamed on the wrong bug.
        private static final Set<String> LOCKED_KEYS =
                new HashSet<>(Arrays.asList("fragment_news", "fragment_share"));

        private View buildRow(int index) {
            Tab tab = items.get(index);
            boolean locked = LOCKED_KEYS.contains(tab.key);

            View row = InstagramPreferenceStyle.createPreferenceView(
                    context, InstagramPreferenceStyle.TRAILING_SWITCH, tab.iconResName);

            Preference dummy = new Preference(context);
            dummy.setTitle(str(tab.labelResKey));
            dummy.setEnabled(true);
            InstagramPreferenceStyle.bindText(dummy, row);

            View handle = replaceSwitchWithDragHandle(row);
            if (locked) {
                if (handle != null) handle.setAlpha(0.35f);
            } else {
                row.setOnTouchListener(new DragTouchListener(index));
            }
            return row;
        }

        private View replaceSwitchWithDragHandle(View row) {
            CompoundButton switchView = InstagramPreferenceStyle.findSwitch(row);
            if (switchView == null) return null;
            Object parentObj = switchView.getParent();
            if (!(parentObj instanceof ViewGroup)) return null;
            ViewGroup switchParent = (ViewGroup) parentObj;
            int slot = switchParent.indexOfChild(switchView);
            switchParent.removeView(switchView);

            int handleSize = dp(context, 36);
            View handle = buildDragHandle(context);
            switchParent.addView(handle, slot, new LinearLayout.LayoutParams(handleSize, handleSize));
            return handle;
        }

        private static View buildDragHandle(Context context) {
            LinearLayout handle = new LinearLayout(context);
            handle.setOrientation(LinearLayout.VERTICAL);
            handle.setGravity(Gravity.CENTER);

            GradientDrawable buttonBackground = new GradientDrawable();
            buttonBackground.setColor(withAlpha(InstagramPreferenceStyle.secondaryTextColor(), 40));
            buttonBackground.setCornerRadius(dp(context, 10));
            handle.setBackground(buttonBackground);

            int barWidth = dp(context, 16);
            int barHeight = dp(context, 2);
            int barGap = dp(context, 3);
            int barColor = InstagramPreferenceStyle.primaryTextColor();

            for (int i = 0; i < 3; i++) {
                View bar = new View(context);
                GradientDrawable barBackground = new GradientDrawable();
                barBackground.setColor(barColor);
                barBackground.setCornerRadius(barHeight / 2f);
                bar.setBackground(barBackground);

                LinearLayout.LayoutParams barParams =
                        new LinearLayout.LayoutParams(barWidth, barHeight);
                if (i > 0) barParams.topMargin = barGap;
                handle.addView(bar, barParams);
            }
            return handle;
        }

        private static int withAlpha(int color, int alpha) {
            return (color & 0x00FFFFFF) | (alpha << 24);
        }

        private final class DragTouchListener implements View.OnTouchListener {
            private final int originalIndex;
            private float startRawY;
            private boolean armed;
            private boolean dragging;
            private Runnable armRunnable;
            private List<Integer> virtualOrder;
            private int rowHeightPx;

            DragTouchListener(int originalIndex) {
                this.originalIndex = originalIndex;
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawY = event.getRawY();
                        armed = false;
                        dragging = false;
                        armRunnable = () -> startDrag(v);
                        v.postDelayed(armRunnable, ViewConfiguration.getLongPressTimeout());
                        return true; // row isn't clickable anymore, must consume to keep the gesture

                    case MotionEvent.ACTION_MOVE:
                        if (!armed) {
                            if (Math.abs(event.getRawY() - startRawY) > touchSlop) {
                                v.removeCallbacks(armRunnable);
                            }
                            return false;
                        }
                        dragging = true;
                        float dy = event.getRawY() - startRawY;
                        v.setTranslationY(dy);
                        updateHoverSlot(dy);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(armRunnable);
                        if (!armed) return false;
                        finishDrag(v);
                        return dragging;
                }
                return false;
            }

            private static final float DRAG_ELEVATION_DP = 8f;

            private void startDrag(View v) {
                armed = true;
                rowHeightPx = v.getHeight();
                virtualOrder = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) virtualOrder.add(i);
                v.setBackgroundColor(InstagramPreferenceStyle.pressedBackgroundColor());
                // elevation only, not bringToFront(): LinearLayout lays out by array
                // order, not Z, so reordering the child array would relocate it mid-drag.
                v.setElevation(dp(v.getContext(), (int) DRAG_ELEVATION_DP));
                scrollView.requestDisallowInterceptTouchEvent(true);
            }

            private void updateHoverSlot(float dy) {
                if (rowHeightPx <= 0) return;
                int currentSlot = virtualOrder.indexOf(originalIndex);
                int targetSlot = clamp(
                        originalIndex + Math.round(dy / rowHeightPx), 0, items.size() - 1);
                if (targetSlot == currentSlot) return;

                virtualOrder.remove(Integer.valueOf(originalIndex));
                virtualOrder.add(targetSlot, originalIndex);

                for (int i = 0; i < container.getChildCount(); i++) {
                    if (i == originalIndex) continue;
                    View sibling = container.getChildAt(i);
                    int newSlot = virtualOrder.indexOf(i);
                    sibling.animate().translationY((newSlot - i) * rowHeightPx).setDuration(120).start();
                }
            }

            private void finishDrag(View v) {
                scrollView.requestDisallowInterceptTouchEvent(false);
                v.setBackgroundColor(Color.TRANSPARENT);
                v.setElevation(0f);

                if (dragging && virtualOrder != null) {
                    List<Tab> reordered = new ArrayList<>(items.size());
                    for (int originalIdx : virtualOrder) reordered.add(items.get(originalIdx));
                    items.clear();
                    items.addAll(reordered);
                }
                for (int i = 0; i < container.getChildCount(); i++) {
                    container.getChildAt(i).animate().cancel();
                }
                // Deferred: mutating `container` mid-dispatchTouchEvent corrupts the
                // ScrollView's children array.
                v.post(this::rebuildRowsSafe);
            }

            private void rebuildRowsSafe() {
                if (!container.isAttachedToWindow()) return;
                rebuildRows();
            }
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}