/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.entity.ImageData;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.SheetBottomDialog;

final class DownloadSelectionSheet {
    private final Context context;
    private final List<CheckBox> checks = new ArrayList<>();
    private final DownloadThumbnailLoader thumbnails = new DownloadThumbnailLoader();
    private final Button selectAll;
    private final Button download;
    private final SheetBottomDialog.SlideDialog dialog;

    static void show(Context context, MediaData media, DialogInterface.OnDismissListener onDismiss) throws Exception {
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            throw new IllegalStateException("No activity available for media selection");
        }
        new DownloadSelectionSheet(activity, media, onDismiss).show();
    }

    private DownloadSelectionSheet(Activity activity, MediaData media,
                                   DialogInterface.OnDismissListener onDismiss) throws Exception {
        context = InstagramPreferenceStyle.dialogContext(activity);
        int total = media.getCarouselSize();
        SheetBottomDialog.DraggableLinearLayout panel = SheetBottomDialog.createMainLayout(
                context, InstagramPreferenceStyle.backgroundColor());
        int screenHeight = activity.getWindow().getDecorView().getHeight();
        if (screenHeight <= 0) screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int panelHeight = Math.min((int) (screenHeight * 0.82f), dp(208 + total * 80));
        panel.setLayoutParams(new LinearLayout.LayoutParams(-1, panelHeight));
        GradientDrawable background = new GradientDrawable();
        background.setColor(InstagramPreferenceStyle.backgroundColor());
        float radius = dp(12);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        panel.setBackground(background);
        panel.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                var navigation = insets.getInsets(WindowInsets.Type.navigationBars());
                left = navigation.left;
                right = navigation.right;
                bottom = navigation.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, 0, right, bottom);
            if (view.getLayoutParams().height != panelHeight + bottom) {
                view.getLayoutParams().height = panelHeight + bottom;
                view.requestLayout();
            }
            return insets;
        });
        dialog = SheetBottomDialog.createSlideDialog(context, panel, 180);
        panel.setDialog(dialog);
        dialog.setOnDismissListener(d -> {
            thumbnails.close();
            if (onDismiss != null) onDismiss.onDismiss(d);
        });
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) { thumbnails.close(); }
        });

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(8), 0);
        TextView title = text(str("piko_select_media_download"), 22, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton close = iconButton(UI.DRAWABLE_CLEAR, str("piko_close"));
        close.setOnClickListener(v -> dialog.cancel());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(header);

        TextView author = text("@" + media.getUserData().getUsername(), 14, false);
        author.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        author.setSingleLine(true);
        author.setEllipsize(TextUtils.TruncateAt.END);
        author.setPadding(dp(20), 0, dp(20), dp(4));
        panel.addView(author);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(8), 0, dp(8), 0);

        selectAll = new Button(context);
        selectAll.setAllCaps(false);
        selectAll.setTextSize(14);
        selectAll.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        selectAll.setPadding(dp(12), 0, dp(12), 0);
        selectAll.setMinWidth(0);
        selectAll.setMinimumWidth(0);
        selectAll.setStateListAnimator(null);
        applyPressedBackground(selectAll);
        selectAll.setOnClickListener(v -> {
            boolean select = selectedIndexes().size() != checks.size();
            for (CheckBox check : checks) check.setChecked(select);
        });
        actions.addView(selectAll, new LinearLayout.LayoutParams(-2, dp(48)));
        panel.addView(actions);

        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        download = new Button(context);
        download.setAllCaps(false);
        download.setTextSize(14);
        download.setTypeface(download.getTypeface(), Typeface.BOLD);
        download.setGravity(Gravity.CENTER);
        download.setTextColor(InstagramPreferenceStyle.backgroundColor());
        download.setBackgroundTintList(null);
        GradientDrawable downloadBackground = new GradientDrawable();
        downloadBackground.setColor(InstagramPreferenceStyle.primaryTextColor());
        downloadBackground.setCornerRadius(dp(20));
        download.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3cffffff), downloadBackground, null));
        download.setStateListAnimator(null);
        download.setElevation(0);
        download.setPadding(dp(20), 0, dp(20), 0);
        download.setMinHeight(dp(40));
        download.setMinimumHeight(dp(40));
        download.setOnClickListener(v -> {
            List<Integer> indexes = selectedIndexes();
            if (indexes.isEmpty()) return;
            try {
                DownloadUtils.downloadSelectedMedia(activity, media, indexes);
                dialog.dismiss();
            } catch (Exception e) {
                Logger.printException(() -> "Could not download selected media", e);
                Utils.showToastShort(str("piko_download_failed_media"));
            }
        });
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(-1, -2);
        downloadParams.setMargins(dp(16), dp(8), dp(16), dp(12));
        panel.addView(download, downloadParams);

        for (int index = 0; index < total; index++) rows.addView(createRow(media.getMediaAt(index), index));
        refreshSelection();
    }

    private View createRow(MediaData item, int index) throws Exception {
        boolean video = item.isVideo();
        String label = str(video ? "piko_download_video_number" : "piko_download_photo_number", index + 1);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(80));
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setContentDescription(label);
        row.setFocusable(true);
        applyPressedBackground(row);
        CheckBox check = new CheckBox(context);
        check.setClickable(false);
        check.setFocusable(false);
        check.setBackground(null);
        check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        check.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{InstagramPreferenceStyle.selectionColor(), InstagramPreferenceStyle.secondaryTextColor()}));
        check.setOnCheckedChangeListener((button, checked) -> refreshSelection());
        checks.add(check);
        row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
        row.setOnClickListener(v -> check.toggle());
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(CheckBox.class.getName());
                info.setCheckable(true);
                info.setChecked(check.isChecked());
            }
        });

        ImageView image = new ImageView(context);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable background = new GradientDrawable();
        background.setColor(InstagramPreferenceStyle.pressedBackgroundColor());
        background.setCornerRadius(dp(12));
        image.setBackground(background);
        image.setClipToOutline(true);
        UI.setThemedIcon(image, video ? "fb_ic_video_24" : "instagram_photo_outline_24");
        row.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48)));
        String thumbnail = thumbnailUrl(item);
        image.setTag(thumbnail);

        TextView name = text(label, 16, true);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        name.setPadding(dp(12), 0, dp(4), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton copy = iconButton("instagram_link_outline_24", str("piko_download_copy_link_for", label));
        copy.setOnClickListener(v -> {
            try {
                String url = item.getMediaLink();
                if (url == null || url.isEmpty()) throw new IllegalStateException("Missing media link");
                Utils.setClipboard(url);
                Utils.showToastShort(str("piko_copied_media_link"));
            } catch (Exception e) {
                Logger.printException(() -> "Could not copy selected media link", e);
                Utils.showToastShort(str("piko_media_link_unavailable"));
            }
        });
        row.addView(copy, new LinearLayout.LayoutParams(dp(48), dp(48)));
        image.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                image.removeOnAttachStateChangeListener(this);
                thumbnails.load(thumbnail, image);
            }
            @Override public void onViewDetachedFromWindow(View view) {}
        });
        return row;
    }

    private List<Integer> selectedIndexes() {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < checks.size(); index++) if (checks.get(index).isChecked()) indexes.add(index);
        return indexes;
    }

    private void refreshSelection() {
        int count = selectedIndexes().size();
        selectAll.setText(str(count == checks.size() ? "piko_download_deselect_all" : "piko_download_select_all"));
        download.setText(str("piko_download_selected_count", count));
        download.setEnabled(count > 0);
        download.setAlpha(count > 0 ? 1f : 0.45f);
    }

    private void show() {
        Window window = dialog.getWindow();
        if (window != null) {
            // Let this WRAP_CONTENT dialog pass navigation insets through to the sheet.
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.setFitInsetsTypes(WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
                window.setAttributes(attributes);
            }
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (Build.VERSION.SDK_INT < 30) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            }
            decor.setSystemUiVisibility(UI.isDarkMode()
                    ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        dialog.show();
    }

    private ImageButton iconButton(String drawable, String description) {
        ImageButton button = new ImageButton(context);
        applyPressedBackground(button);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setContentDescription(description);
        UI.setThemedIcon(button, drawable);
        return button;
    }

    private TextView text(String text, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private int dp(int value) { return InstagramPreferenceStyle.dp(context, value); }

    private void applyPressedBackground(View view) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            view.setBackgroundResource(value.resourceId);
        }
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return Utils.getActivity();
    }

    private static String thumbnailUrl(MediaData item) {
        try {
            ImageData best = null;
            int bestSize = 0;
            for (Object candidate : item.getImageVariants()) {
                ImageData image = (ImageData) candidate;
                int size = Math.min(image.getWidth(), image.getHeight());
                if (size <= 0) continue;
                if (best == null || (bestSize < 256 ? size > bestSize : size >= 256 && size < bestSize)) {
                    best = image;
                    bestSize = size;
                }
            }
            return best == null ? null : best.getUrl();
        } catch (Exception e) {
            return null;
        }
    }
}
