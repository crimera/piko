/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.shareMenu;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ui.SheetBottomDialog;
/**
 * Builds and displays a styled bottom sheet on top of the existing
 * {@link SheetBottomDialog} infrastructure — no new dependencies required.
 *
 * <h3>Usage</h3>
 * <pre>
 * BottomSheetHelper.show(
 *     context,
 *     myPost,           // any data object — delivered to every callback on tap
 *     "Share post",     // title shown at top; pass null to hide
 *     Arrays.asList(
 *         new BottomSheetAction&lt;&gt;(R.drawable.ic_dm,       "Send via Direct Message", p -&gt; shareDM(p)),
 *         new BottomSheetAction&lt;&gt;(R.drawable.ic_bookmark, "Delete Bookmark",         p -&gt; removeBookmark(p)),
 *         new BottomSheetAction&lt;&gt;(R.drawable.ic_link,     "Copy link",               p -&gt; copyLink(p)),
 *         new BottomSheetAction&lt;&gt;(R.drawable.ic_share,    "Share post via\u2026",    p -&gt; shareVia(p))
 *     ),
 *     null   // optional Runnable fired after the sheet fully dismisses
 * );
 * </pre>
 */
public final class BottomSheetHelper {

    // ------------------------------------------------------------------
    // Tunables — adjust freely without touching any other file
    // ------------------------------------------------------------------

    /** Slide-in / slide-out animation duration in ms. */
    private static final int ANIM_DURATION_MS = 300;

    /** Height of each action row in dp. */
    private static final int ROW_HEIGHT_DP = 56;

    /** Start/end padding of each action row in dp. */
    private static final int ROW_PADDING_H_DP = 20;

    /** Gap between the icon and the label in dp. */
    private static final int ICON_LABEL_GAP_DP = 16;

    /** Icon size in dp. */
    private static final int ICON_SIZE_DP = 24;

    /** Label text size in sp. */
    private static final int LABEL_TEXT_SP = 16;

    /** Title text size in sp. */
    private static final int TITLE_TEXT_SP = 17;

    /** Vertical padding around the title in dp. */
    private static final int TITLE_PADDING_V_DP = 14;

    private BottomSheetHelper() { /* utility class */ }

    private static int resolveColor(@NonNull Context context, String attrName) {
        TypedValue tv = new TypedValue();
        int attrId = ResourceUtils.getIdentifier(ResourceType.ATTR, attrName);
        context.getTheme().resolveAttribute(attrId, tv, true);
        return tv.data;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Builds and shows the bottom sheet.
     *
     * @param context     Activity context.
     * @param item        Data object delivered to every {@link BottomSheetAction} callback on tap.
     * @param title       Optional bold header. Pass {@code null} to omit.
     * @param actions     Ordered list of action rows.
     * @param onDismiss   Optional {@link Runnable} called after the sheet fully dismisses.
     * @param <T>         Type of {@code item}.
     */
    public static <T> void show(
            @NonNull Context context,
            @NonNull T item,
            @Nullable String title,
            @NonNull List<BottomSheetAction<T>> actions,
            @Nullable Runnable onDismiss) {

        // 1. Build the main layout using SheetBottomDialog's own factory.
        //    This gives us the rounded background + drag handle for free.

        SheetBottomDialog.DraggableLinearLayout mainLayout =
                SheetBottomDialog.createMainLayout(context, resolveColor(context,"coreColorAppBackground"));

        // 2. Optional title row
        if (title != null && !title.trim().isEmpty()) {
            mainLayout.addView(buildTitleView(context, title));
        }

        // 3. One action row per BottomSheetAction
        for (BottomSheetAction<T> action : actions) {
            mainLayout.addView(buildActionRow(context, action, item,
                    // We need a reference to the dialog to dismiss it on tap,
                    // but the dialog doesn't exist yet. We use a holder array
                    // (effectively-final single-element array trick) so the
                    // lambda can close over it.
                    new SlideDialogHolder()));
        }

        // 4. Wire up the dialog via SheetBottomDialog's factory
        SheetBottomDialog.SlideDialog dialog =
                SheetBottomDialog.createSlideDialog(context, mainLayout, ANIM_DURATION_MS);

        // 5. Inject the real dialog reference into every row's holder
        //    Now that the dialog is created, update the holders we passed above.
        updateDialogHolders(mainLayout, dialog);

        // 6. Optional dismiss callback
        if (onDismiss != null) {
            dialog.setOnDismissListener(d -> onDismiss.run());
        }

        dialog.show();
    }

    // ------------------------------------------------------------------
    // View builders
    // ------------------------------------------------------------------

    /** Builds the optional bold title TextView. */
    private static TextView buildTitleView(@NonNull Context context, @NonNull String title) {
        TextView tv = new TextView(context);
        int hPad = dp(context, ROW_PADDING_H_DP);
        int vPad = dp(context, TITLE_PADDING_V_DP);
        tv.setPadding(hPad, vPad, hPad, vPad);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SP);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setTextColor(resolveColor(context, "textColorPrimary"));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    /**
     * Inflates a single action row: [icon 24dp] [gap] [label].
     *
     * <p>The row stores a {@link SlideDialogHolder} as its tag so that
     * {@link #updateDialogHolders} can inject the real dialog later.
     */
    private static <T> View buildActionRow(
            @NonNull Context context,
            @NonNull BottomSheetAction<T> action,
            @NonNull T item,
            @NonNull SlideDialogHolder holder) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int hPad = dp(context, ROW_PADDING_H_DP);
        row.setPadding(hPad, 0, hPad, 0);

        // Ripple feedback using the standard system attribute
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);
        row.setClickable(true);
        row.setFocusable(true);

        // Row height
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, ROW_HEIGHT_DP));
        row.setLayoutParams(rowLp);

        // Icon
        ImageView icon = new ImageView(context);
        int iconSize = dp(context, ICON_SIZE_DP);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        icon.setLayoutParams(iconLp);
        int attrId = ResourceUtils.getIdentifier(ResourceType.DRAWABLE, action.iconRes);
        icon.setImageResource(attrId);
        icon.setColorFilter(resolveColor(context, "textColorSecondary"));
        row.addView(icon);

        // Gap between icon and label
        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(context, ICON_LABEL_GAP_DP), 0));
        row.addView(gap);

        // Label
        TextView label = new TextView(context);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        label.setText(action.label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP);
        label.setTextColor(resolveColor(context, "textColorPrimary"));
        row.addView(label);

        // Click: dismiss the dialog first, then fire the callback
        row.setOnClickListener(v -> {
            if (holder.dialog != null) {
                holder.dialog.setOnDismissListener(d -> action.callback.onAction(item));
                holder.dialog.dismiss();
            } else {
                // Fallback: fire callback directly if holder wasn't filled yet
                action.callback.onAction(item);
            }
        });

        // Store the holder as the tag so we can update it later
        row.setTag(holder);

        return row;
    }

    /**
     * After the {@link SheetBottomDialog.SlideDialog} is created, walk the
     * {@code mainLayout} children and inject the real dialog into every
     * {@link SlideDialogHolder} that was stored as a row's tag.
     */
    private static void updateDialogHolders(
            @NonNull LinearLayout mainLayout,
            @NonNull SheetBottomDialog.SlideDialog dialog) {

        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View child = mainLayout.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof SlideDialogHolder) {
                ((SlideDialogHolder) tag).dialog = dialog;
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Converts dp to pixels using the supplied context. */
    private static int dp(@NonNull Context context, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }

    /**
     * A mutable holder for the {@link SheetBottomDialog.SlideDialog} reference.
     *
     * <p>Used so action-row lambdas (which are created before the dialog exists)
     * can still call {@code dialog.dismiss()} once the dialog is wired up.
     */
    private static final class SlideDialogHolder {
        SheetBottomDialog.SlideDialog dialog;
    }
}