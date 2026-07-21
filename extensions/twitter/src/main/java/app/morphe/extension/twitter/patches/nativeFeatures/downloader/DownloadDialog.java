/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.downloader;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Window;

import androidx.annotation.Nullable;

import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.twitter.entity.Media;
import app.morphe.extension.crimera.PikoUtils;

import static app.morphe.extension.shared.StringRef.str;

/**
 * A dialog that lists {@link DownloadItem}s, each with a download action, a copy-link action,
 * and (if it has variants) a variants action that opens a nested dialog of the same style.
 * <p>
 * Built on top of {@link CustomDialog} so it inherits the same rounded background, title
 * styling, and window sizing. Its list body is custom, since {@link CustomDialog} only
 * supports a plain message or an EditText as content.
 * <p>
 * Usage — just pass the list:
 * <pre>{@code
 * DownloadDialog.show(context, "Share", items);
 *
 * </pre>
 * <p>
 * The download/copy/variants icon drawable names are set directly in {@link #buildItemRow}
 * below — edit the three {@code createIconButton(...)} calls there to point at your own
 * drawable resource names.
 */
public class DownloadDialog {

    private DownloadDialog() {
    }

    /**
     * Builds and shows a dialog. Used both for the top-level list (with a variants action
     * on each row) and for the nested variants list (name + download + copy only).
     */
    public static Dialog buildDialog(Context context, CharSequence title, List<DownloadItem> items) {
        // Reuse CustomDialog purely for its chrome (rounded background, title, window params).
        // Content and buttons are left null/empty here since this dialog's body is custom.
        Pair<Dialog, LinearLayout> pair = CustomDialog.create(
                context, title, null, null, null,
                null, null, null, null, true);
        Dialog dialog = pair.first;
        LinearLayout mainLayout = pair.second;

        // the rounded corners regardless of theme. Clearing it here fixes that.
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            ShapeDrawable background = new ShapeDrawable(new RoundRectShape(Dim.roundedCorners(28), null, null));
            background.getPaint().setColor(app.morphe.extension.twitter.Utils.resolveColor(context, "coreColorAppBackground"));
            mainLayout.setBackground(background);
        }

        View firstChild = mainLayout.getChildAt(0);
        if (firstChild instanceof TextView) {
            ((TextView) firstChild).setTextColor(primaryTextColor(context));
        }

        int accentColor = ResourceUtils.getColor("twitter_blue_fill_pressed");

        mainLayout.addView(buildItemList(context, items, accentColor));
        mainLayout.addView(buildBottomButtons(context, dialog, items, accentColor));

        dialog.show();
        return dialog;
    }

    // ---------------------------------------------------------------------------------------
    // List body
    // ---------------------------------------------------------------------------------------

    private static View buildItemList(Context context, List<DownloadItem> items,
                                      @Nullable Integer accentColor) {
        LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(str("piko_pref_native_downloader_no_media"));
            empty.setTextColor(primaryTextColor(context));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Dim.dp16, 0, Dim.dp16);
            listContainer.addView(empty);
        } else {
            for (int i = 0; i < items.size(); i++) {
                DownloadItem item = items.get(i);
                listContainer.addView(buildItemRow(context, item, accentColor));
                if (i < items.size() - 1) {
                    listContainer.addView(buildDivider(context));
                }
            }
        }

        // Cap the list height so a long list scrolls instead of pushing the dialog off screen.
        MaxHeightScrollView scrollView = new MaxHeightScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setMaxHeightPx((int) (context.getResources().getDisplayMetrics().heightPixels * 0.5f));
        scrollView.addView(listContainer);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return scrollView;
    }

    private static View buildItemRow(Context context, DownloadItem item, @Nullable Integer accentColor) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Dim.dp8, 0, Dim.dp8);

        // Label: file name.
        TextView nameView = new TextView(context);
        nameView.setText(item.labelText);
        nameView.setTextSize(15);
        nameView.setTextColor(primaryTextColor(context));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginEnd(Dim.dp8);
        nameView.setLayoutParams(nameParams);
        row.addView(nameView);

        // Download. Replace "ic_download" with your actual drawable resource name.
        row.addView(createIconButton(context, "ic_vector_incoming", accentColor,
                () -> downloadFile(item)));

        // Copy link. Replace "ic_copy_link" with your actual drawable resource name.
        row.addView(createIconButton(context, "ic_vector_copy_stroke", accentColor,
                () -> copyLinkToClipboard(item)));

        // Variants (only on the top-level list; disabled when the item has none).
        if (item.hasVariants()) {
            View variantsButton = createIconButton(context, "ic_vector_bulleted_list", accentColor,
                    () -> showVariantsDialog(context, item));
            variantsButton.setEnabled(item.hasVariants());
            variantsButton.setAlpha(item.hasVariants() ? 1f : 0.35f);
            row.addView(variantsButton);
        }

        return row;
    }

    private static void showVariantsDialog(Context context, DownloadItem item) {
        if (!item.hasVariants()) return;
        String titleTag = str("piko_video_variants");

        buildDialog(context, titleTag, item.variants);
    }

    private static View buildDivider(Context context) {
        View divider = new View(context);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 1)));
        divider.setBackgroundColor(Utils.isDarkModeEnabled() ? 0x33FFFFFF : 0x1F000000);
        return divider;
    }

    // ---------------------------------------------------------------------------------------
    // Icon buttons
    // ---------------------------------------------------------------------------------------

    /**
     * A small circular, ripple-backed icon button.
     * <p>
     * If {@code iconName} resolves to a real drawable in the app's resources, that drawable is
     * used (tinted with {@code accentColor}). Otherwise it falls back to a Unicode glyph
     * placeholder, so passing no icon names still renders something usable.
     *
     * @param iconName Resource name of a drawable (e.g. "ic_download"), or null to use the glyph.
     */
    private static View createIconButton(Context context, @Nullable String iconName, @Nullable Integer accentColor,
                                         Runnable onClick) {
        int tint = accentColor != null ? accentColor : Utils.getAppForegroundColor();
        int attrId = ResourceUtils.getIdentifier(ResourceType.DRAWABLE, iconName);

        ImageButton imageButton = new ImageButton(context);
        imageButton.setImageResource(attrId);
        imageButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageButton.setPadding(Dim.dp8, Dim.dp8, Dim.dp8, Dim.dp8);
        imageButton.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        View button = imageButton;

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Dim.dp36, Dim.dp36);
        params.setMarginStart(Dim.dp4);
        button.setLayoutParams(params);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.TRANSPARENT);
        int rippleColor = (tint & 0x00FFFFFF) | 0x33000000;
        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(rippleColor), circle, circle);
        button.setBackground(ripple);
        button.setClickable(true);
        button.setFocusable(true);

        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    private static Button createBottomButton(Context context, Dialog dialog,Integer accentColor, CharSequence buttonText, @Nullable Runnable onClick ){
        Button button = CustomDialog.createButton(context, dialog, buttonText, onClick, true, true);

        ShapeDrawable background = new ShapeDrawable(new RoundRectShape(Dim.roundedCorners(20), null, null));
        background.getPaint().setColor(accentColor);
        button.setBackground(background);
        button.setTextColor(Color.WHITE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Dim.dp36
        );
        params.setMargins(0, Dim.dp16, 0, 0);

        button.setLayoutParams(params);
        return button;
    }

    private static View buildBottomButtons(Context context, Dialog dialog, List<DownloadItem> items, Integer accentColor) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, Dim.dp16, 0, 0);
        row.setLayoutParams(rowParams);

        if(items.size()>1){
            Runnable downloaddAllFuncCall = () -> {
                items.forEach(item->{
                    downloadFile(item);
                });
            };
            Button downloadAllButton = createBottomButton(context,dialog,accentColor,str("piko_pref_native_downloader_download_all"),downloaddAllFuncCall);
            row.addView(downloadAllButton);
        }

        Button closeButton = createBottomButton(context,dialog,accentColor,str("piko_cancel"),null);
        row.addView(closeButton);
        return row;
    }

    // ---------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------

    private static void downloadFile(DownloadItem item) {
        PikoUtils.toast(str("download_started"));
        Media media = item.media;
        String fileName = item.fileName;
        app.morphe.extension.twitter.Utils.downloadFile(media.url, fileName, media.ext);
    }


    private static void copyLinkToClipboard(DownloadItem item) {
        String url = item.media.url;

        Utils.setClipboard(url);
        PikoUtils.toast(str("link_copied_to_clipboard"));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private static int primaryTextColor(Context context){
        return app.morphe.extension.twitter.Utils.resolveColor(context, "textColorPrimary");
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    /** A ScrollView that never grows taller than a given pixel height. */
    private static class MaxHeightScrollView extends ScrollView {
        private int maxHeightPx = Integer.MAX_VALUE;

        MaxHeightScrollView(Context context) {
            super(context);
        }

        void setMaxHeightPx(int maxHeightPx) {
            this.maxHeightPx = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int heightSpec = heightMeasureSpec;
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
                int heightSize = Math.min(MeasureSpec.getSize(heightMeasureSpec), maxHeightPx);
                heightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightSpec);
        }
    }
}