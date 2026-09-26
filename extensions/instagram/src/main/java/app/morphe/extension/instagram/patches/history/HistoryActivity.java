/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.history;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.db.PikoHistoryDb;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

/**
 * Two-column grid of viewed items, rendered a page at a time as the user scrolls. Uses plain
 * framework views: Instagram's R8 build strips the androidx classes (e.g. RecyclerView) that an
 * extension would link against.
 */
public class HistoryActivity extends Activity {

    private static final int PAGE_SIZE = 40;

    private List<PikoHistoryDb.Entry> history;
    private int renderedCount;
    private int thumbnailWidth;
    private LinearLayout leftColumn;
    private LinearLayout rightColumn;
    private int leftHeightEstimate;
    private int rightHeightEstimate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);

        history = PikoHistoryDb.getInstance(this).getHistory();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        InstagramPreferenceStyle.applySystemBarStyle(this);

        // Toolbar
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        toolbar.setPadding(Dim.dp8, Dim.dp8, Dim.dp8, Dim.dp8);

        ImageView back = new ImageView(this);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(Dim.dp48, Dim.dp48);
        backParams.gravity = Gravity.CENTER_VERTICAL;
        back.setLayoutParams(backParams);
        UI.setThemedIcon(back, "material_ic_keyboard_arrow_left_black_24dp");
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText(str("piko_view_history"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, PikoUtils.spToPixels(20));
        title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1
        );
        titleParams.gravity = Gravity.CENTER_VERTICAL;
        titleParams.leftMargin = Dim.dp8 / 2;
        title.setLayoutParams(titleParams);

        toolbar.addView(back);
        toolbar.addView(title);

        TextView clear = new TextView(this);
        clear.setText(str("piko_clear_view_history"));
        clear.setTextSize(TypedValue.COMPLEX_UNIT_PX, PikoUtils.spToPixels(16));
        clear.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        clear.setPadding(Dim.dp8, Dim.dp8, Dim.dp8, Dim.dp8);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        clearParams.gravity = Gravity.CENTER_VERTICAL;
        clear.setLayoutParams(clearParams);
        clear.setOnClickListener(v -> new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(this))
            .setMessage(str("piko_clear_view_history_confirm"))
            .setPositiveButton(str("piko_clear_view_history"), (d, w) -> {
                PikoHistoryDb.getInstance(this).clearAll();
                recreate();
            })
            .setNegativeButton(str("piko_cancel"), null)
            .show());
        toolbar.addView(clear);
        root.addView(toolbar);

        if (history.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(str("piko_no_view_history"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Dim.dp8 * 2, Dim.dp8 * 4, Dim.dp8 * 2, Dim.dp8 * 4);
            empty.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
            root.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ));
        } else {
            ScrollView scrollView = new ScrollView(this);
            scrollView.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.HORIZONTAL);
            grid.setPadding(Dim.dp4, Dim.dp4, Dim.dp4, Dim.dp4);

            leftColumn = new LinearLayout(this);
            leftColumn.setOrientation(LinearLayout.VERTICAL);
            rightColumn = new LinearLayout(this);
            rightColumn.setOrientation(LinearLayout.VERTICAL);

            grid.addView(leftColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            grid.addView(rightColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            thumbnailWidth = getResources().getDisplayMetrics().widthPixels / 2;
            addNextPage();
            scrollView.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
                if (y + v.getHeight() >= grid.getHeight() - v.getHeight()) addNextPage();
            });

            scrollView.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ));
        }

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets;
        });

        setContentView(root);
    }

    private static String typeLabel(String postType) {
        if ("REEL".equals(postType)) return str("piko_history_type_reel");
        if ("STORY".equals(postType)) return str("piko_history_type_story");
        return str("piko_history_type_post");
    }

    private void openEntry(PikoHistoryDb.Entry entry) {
        String link = webLink(entry);
        if (link == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .setPackage(getPackageName()));
        } catch (Exception e) {
            Logger.printException(() -> "Failed to open " + link, e);
        }
    }

    private void addNextPage() {
        int end = Math.min(renderedCount + PAGE_SIZE, history.size());
        for (; renderedCount < end; renderedCount++) {
            addCard(history.get(renderedCount));
        }
    }

    private void showEntryMenu(PikoHistoryDb.Entry entry, View card, LinearLayout column) {
        String webLink = webLink(entry);
        List<String> items = new ArrayList<>();
        if (webLink != null) items.add(str("piko_history_copy_link"));
        items.add(str("piko_delete"));
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(this))
            .setItems(items.toArray(new String[0]), (d, which) -> {
                if (webLink != null && which == 0) {
                    Utils.setClipboard(webLink);
                    Utils.showToastShort(str("piko_copied"));
                } else {
                    confirmDelete(entry.id, card, column);
                }
            })
            .show();
    }

    /**
     * The instagram.com link. Stories logged by 3.10.0-personal.2 hold an internal
     * {@code instagram://stories?user_id=..&media_id=..} link, which Instagram ignores, so
     * rebuild those from the owner's username.
     */
    private static String webLink(PikoHistoryDb.Entry entry) {
        String permalink = entry.permalink;
        if (permalink == null || permalink.isEmpty()) return null;
        if (!permalink.startsWith("instagram://stories")) return permalink;
        String mediaId = Uri.parse(permalink).getQueryParameter("media_id");
        if (mediaId == null || entry.ownerUsername == null || entry.ownerUsername.isEmpty()) return null;
        return String.format(Constants.INSTAGRAM_SHARE_LINK, "stories", entry.ownerUsername) + mediaId;
    }

    private void confirmDelete(long id, View card, LinearLayout column) {
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(this))
            .setMessage(str("piko_delete_view_history_confirm"))
            .setPositiveButton(str("piko_delete"), (d, w) -> {
                PikoHistoryDb.getInstance(this).deleteEntry(id);
                column.removeView(card);
            })
            .setNegativeButton(str("piko_cancel"), null)
            .show();
    }

    /** Appends a card to the column that is estimated to be shorter. */
    private void addCard(PikoHistoryDb.Entry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(Dim.dp4, Dim.dp4, Dim.dp4, Dim.dp4);
        card.setLayoutParams(cardParams);

        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(UI.getThemedColour("igds_color_secondary_background"));
        cardBackground.setCornerRadius(Dim.dp(12));
        card.setBackground(cardBackground);
        card.setClipToOutline(true);

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        thumb.setAdjustViewBounds(true);
        thumb.setMinimumHeight(Dim.dp(96));
        thumb.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        ThumbnailCache.load(thumb, entry.thumbUrl, thumbnailWidth);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setPadding(Dim.dp8, Dim.dp6, Dim.dp8, Dim.dp8);

        if (entry.caption != null && !entry.caption.isEmpty()) {
            TextView captionView = new TextView(this);
            captionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            captionView.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            captionView.setMaxLines(2);
            captionView.setText(entry.caption);
            textBlock.addView(captionView);
        }

        String who = entry.ownerUsername != null && !entry.ownerUsername.isEmpty()
                ? "@" + entry.ownerUsername : str("piko_unknown");
        TextView metaView = new TextView(this);
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        metaView.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        metaView.setPadding(0, Dim.dp2, 0, 0);
        metaView.setText(who + "\n" + typeLabel(entry.postType) + "  ·  "
                + DateFormat.format("MMM dd, HH:mm", new Date(entry.viewedAt)));

        textBlock.addView(metaView);

        card.addView(thumb);
        card.addView(textBlock);

        card.setOnClickListener(v -> openEntry(entry));

        boolean placeLeft = leftHeightEstimate <= rightHeightEstimate;
        LinearLayout targetColumn = placeLeft ? leftColumn : rightColumn;
        card.setOnLongClickListener(v -> {
            showEntryMenu(entry, card, targetColumn);
            return true;
        });

        // Thumbnail sizes are unknown until they load, so balance on a fixed estimate.
        int estimate = Dim.dp(96) + Dim.dp(48);
        if (placeLeft) {
            leftColumn.addView(card);
            leftHeightEstimate += estimate;
        } else {
            rightColumn.addView(card);
            rightHeightEstimate += estimate;
        }
    }
}
