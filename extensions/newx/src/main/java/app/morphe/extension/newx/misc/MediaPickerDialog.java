package app.morphe.extension.newx.misc;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.crimera.ui.ButtonView;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.ui.BottomSheetView;
import app.morphe.extension.newx.ui.IconView;
import app.morphe.extension.newx.ui.ListItem;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.StringSetting;

/**
 * Media Picker Bottom Sheet for NewX inline download action.
 * Inspired by Material 3 Expressive motion, shape morphing, and connected button groups.
 * Supports:
 * - List, Grid, and Staggered (aspect-ratio-preserving masonry) views via a 3-segment M3 Expressive
 *   connected button group in the header or in settings.
 * - Single item tap to download.
 * - Long-press (hold) on any item to enter multi-selection mode with expressive shape & inset morphing.
 * - Checkbox icons for item selection.
 * - Dynamic "Download (N)" action button reflecting the count of selected items.
 * - Auto-exit selection mode back to default view when 0 items are selected.
 * - Copy link and resolution chooser actions when not in selection mode.
 */
public final class MediaPickerDialog {
    private static final String COPY_LINK_SETTING_ID = "newx.content.media_picker_copy_link";
    private static final String RESOLUTION_BUTTON_SETTING_ID = "newx.content.media_picker_resolution_button";
    private static final String THUMBNAILS_SETTING_ID = "newx.content.media_picker_thumbnails";
    private static final String LAYOUT_SETTING_ID = "newx.content.media_picker_layout";
    private static final String GRID_COLUMNS_SETTING_ID = "newx.content.media_picker_grid_columns";
    private static final String GRID_COLUMNS_AUTO = "auto";
    private static final float WIDE_SCREEN_DP = 600f;
    private static final float MIN_STAGGERED_ASPECT = 0.45f;
    private static final float MAX_STAGGERED_ASPECT = 2.2f;
    private static final Pattern DIMENSIONS_PATTERN = Pattern.compile("(\\d{2,5})x(\\d{2,5})");
    private static final String MERGE_BUTTON_SETTING_ID = "newx.content.media_picker_merge_button";
    private static final String LOG_PREFIX = "[PikoNewX][MediaPicker] ";

    // Material 3 Expressive motion tokens:
    // Spatial spring (default expressive): lively overshoot for position, scale, and corner morphs.
    private static final Interpolator M3_EXPRESSIVE_SPATIAL =
            new PathInterpolator(0.38f, 1.21f, 0.22f, 1.00f);
    // Effects spring (fast): critically damped, no bounce for opacity and color shifts.
    private static final Interpolator M3_EXPRESSIVE_EFFECTS =
            new PathInterpolator(0.31f, 0.94f, 0.34f, 1.00f);

    enum LayoutMode {
        LIST("list"),
        GRID("grid"),
        STAGGERED("staggered");

        final String key;

        LayoutMode(String key) {
            this.key = key;
        }

        boolean isGridBased() {
            return this == GRID || this == STAGGERED;
        }

        static LayoutMode fromKey(String key) {
            if (GRID.key.equals(key)) return GRID;
            if (STAGGERED.key.equals(key)) return STAGGERED;
            return LIST;
        }
    }

    public interface OnMediaSelectedListener {
        void onDownloadItem(int index);
        void onDownloadAll();
        void onDownloadAndMerge(List<InlineDownloadButton.DownloadItem> items);
        void onShowResolutions(int index);
    }

    private MediaPickerDialog() {
    }

    public static void show(
            Context context,
            List<InlineDownloadButton.DownloadItem> downloads,
            String username,
            OnMediaSelectedListener listener
    ) {
        if (context == null || downloads == null || downloads.isEmpty() || listener == null) {
            NewXLogger.printInfo(() ->
                    LOG_PREFIX + "show skipped invalid arguments context=" + (context != null) +
                            " items=" + (downloads == null ? "null" : downloads.size()) +
                            " listener=" + (listener != null)
            );
            return;
        }

        Activity activity = NewXUtils.findActivity(context);
        Activity current = activity != null ? activity : InlineDownloadButton.currentActivity();
        if (current == null || current.isFinishing() || current.isDestroyed()) {
            NewXLogger.printInfo(() ->
                    LOG_PREFIX + "show skipped unavailable activity resolved=" + (current != null) +
                            " finishing=" + (current != null && current.isFinishing()) +
                            " destroyed=" + (current != null && current.isDestroyed())
            );
            return;
        }

        BottomSheetView dialog = new BottomSheetView(current);
        dialog.setTitle("Download media");

        String defaultSubtitle = (username != null && !username.trim().isEmpty())
                ? "From @" + username.trim()
                : "Select media to save to your device";
        dialog.setSubtitle(defaultSubtitle);

        boolean hasMultiple = downloads.size() > 1;
        boolean showCopyLinkButton = showCopyLinkButton();
        boolean showResolutionButton = resolutionButtonEnabled();
        boolean loadThumbnails = thumbnailsEnabled();
        final LayoutMode[] layoutMode = new LayoutMode[]{layoutModeSetting()};
        Theme.SettingsSnapshot themeSettings = Theme.snapshot();
        NewXLogger.printInfo(() ->
                LOG_PREFIX + "showing picker items=" + downloads.size() +
                        " layout=" + layoutMode[0].key +
                        " thumbnailsEnabled=" + loadThumbnails +
                        " copyLinkEnabled=" + showCopyLinkButton +
                        " resolutionButtonEnabled=" + showResolutionButton
        );

        final Set<Integer> selectedIndices = new LinkedHashSet<>();
        final boolean[] isSelectionMode = new boolean[]{false};
        final List<ListItem> listItems = new ArrayList<>(downloads.size());
        final List<GridTile> gridTiles = new ArrayList<>(downloads.size());
        final List<Bitmap> loadedThumbnails = new ArrayList<>(downloads.size());
        for (int i = 0; i < downloads.size(); i++) {
            loadedThumbnails.add(null);
        }

        boolean mergeEnabled = mergeButtonEnabled();
        int imageCount = 0;
        for (InlineDownloadButton.DownloadItem item : downloads) {
            if (isImage(item)) imageCount++;
        }
        final boolean canMerge = mergeEnabled && imageCount >= 2;

        // Create merge action button (placed to the left of download button)
        final ButtonView mergeButton;
        if (canMerge) {
            mergeButton = new ButtonView(
                    current,
                    ButtonView.ButtonStyle.TONAL,
                    "Download & Merge"
            );
            dialog.addButton(mergeButton);
        } else {
            mergeButton = null;
        }

        // Create download action button
        final ButtonView downloadButton = new ButtonView(
                current,
                ButtonView.ButtonStyle.FILLED,
                hasMultiple ? "Download All (" + downloads.size() + ")" : "Download (1)"
        );
        if (!hasMultiple) {
            downloadButton.setVisibility(View.GONE);
        }
        dialog.addButton(downloadButton);

        // Helper to update selection UI states
        final Runnable[] updateUiHolder = new Runnable[1];
        updateUiHolder[0] = () -> {
            int selectedCount = selectedIndices.size();
            NewXLogger.printInfo(() ->
                    LOG_PREFIX + "selection state mode=" + isSelectionMode[0] +
                            " selected=" + selectedIndices
            );
            // Automatically revert to default view if 0 items are selected
            if (isSelectionMode[0] && selectedCount == 0) {
                isSelectionMode[0] = false;
            }

            int selectedImageCount = 0;
            if (isSelectionMode[0]) {
                for (int idx : selectedIndices) {
                    if (isImage(downloads.get(idx))) {
                        selectedImageCount++;
                    }
                }
            }

            if (isSelectionMode[0]) {
                dialog.setTitle("Select media");
                dialog.setSubtitle(selectedCount + " of " + downloads.size() + " selected");
                downloadButton.setEnabled(true);
                if (selectedCount == downloads.size()) {
                    downloadButton.setText("Download All (" + downloads.size() + ")");
                } else {
                    downloadButton.setText("Download (" + selectedCount + ")");
                }
                downloadButton.setVisibility(View.VISIBLE);

                if (mergeButton != null) {
                    if (selectedImageCount >= 2) {
                        mergeButton.setText("Merge (" + selectedImageCount + ")");
                        mergeButton.setVisibility(View.VISIBLE);
                        mergeButton.setEnabled(true);
                    } else {
                        mergeButton.setVisibility(View.GONE);
                    }
                }
            } else {
                dialog.setTitle("Download media");
                dialog.setSubtitle(defaultSubtitle);
                downloadButton.setEnabled(true);
                if (hasMultiple) {
                    downloadButton.setText("Download All (" + downloads.size() + ")");
                    downloadButton.setVisibility(View.VISIBLE);
                } else {
                    downloadButton.setVisibility(View.GONE);
                }

                if (mergeButton != null) {
                    mergeButton.setText("Download & Merge");
                    mergeButton.setVisibility(View.VISIBLE);
                    mergeButton.setEnabled(true);
                }
            }

            if (mergeButton != null) {
                LinearLayout.LayoutParams dlParams = (LinearLayout.LayoutParams) downloadButton.getLayoutParams();
                if (dlParams != null) {
                    int marginStart = mergeButton.getVisibility() == View.VISIBLE ? Theme.dpToPx(current, 10f) : 0;
                    if (dlParams.getMarginStart() != marginStart) {
                        dlParams.setMarginStart(marginStart);
                        downloadButton.setLayoutParams(dlParams);
                    }
                }
            }

            if (layoutMode[0].isGridBased()) {
                for (int i = 0; i < gridTiles.size(); i++) {
                    final int itemIndex = i;
                    GridTile tile = gridTiles.get(i);
                    if (tile == null) continue;
                    InlineDownloadButton.DownloadItem item = downloads.get(i);
                    boolean isSelected = selectedIndices.contains(i);
                    Bitmap thumbnail = loadedThumbnails.get(i);
                    tile.setThumbnail(thumbnail);
                    tile.updateState(
                            isSelectionMode[0],
                            isSelected,
                            item,
                            itemIndex,
                            showResolutionButton,
                            showCopyLinkButton,
                            dialog,
                            listener,
                            () -> toggleSelection(selectedIndices, itemIndex, updateUiHolder[0])
                    );
                }
            } else {
                for (int i = 0; i < listItems.size(); i++) {
                    ListItem itemRow = listItems.get(i);
                    InlineDownloadButton.DownloadItem item = downloads.get(i);
                    boolean isSelected = selectedIndices.contains(i);
                    IconView.IconType iconType = resolveIconType(item);
                    int primaryAccent = themeSettings.primaryAccent(current);

                    int badgeBg = (isSelectionMode[0] && isSelected)
                            ? themeSettings.primaryContainer(current)
                            : themeSettings.surfaceVariant(current);
                    Bitmap thumbnail = loadedThumbnails.get(i);
                    if (thumbnail != null) {
                        itemRow.setLeadingImage(thumbnail, badgeBg);
                    } else {
                        itemRow.setLeadingIcon(iconType, primaryAccent, badgeBg);
                    }

                    final int itemIndex = i;
                    if (isSelectionMode[0]) {
                        IconView.IconType cbType = isSelected
                                ? IconView.IconType.CHECKBOX_CHECKED
                                : IconView.IconType.CHECKBOX_UNCHECKED;
                        int cbColor = isSelected
                                ? themeSettings.checkboxChecked(current)
                                : themeSettings.secondaryText(current);

                        itemRow.createTrailingIconButton(cbType, cbColor, v ->
                                toggleSelection(selectedIndices, itemIndex, updateUiHolder[0])
                        );
                    } else {
                        bindTrailingActions(
                                current,
                                itemRow,
                                item,
                                itemIndex,
                                themeSettings,
                                showResolutionButton,
                                showCopyLinkButton,
                                dialog,
                                listener
                        );
                    }
                }
            }
        };

        // Helper to build and attach the active body layout (list, grid, or staggered)
        final boolean[] hasAttachedBody = new boolean[]{false};
        final Runnable bindBodyLayout = () -> {
            listItems.clear();
            gridTiles.clear();
            View bodyView;
            switch (layoutMode[0]) {
                case GRID:
                    bodyView = buildGridBody(
                            current,
                            downloads,
                            themeSettings,
                            isSelectionMode,
                            selectedIndices,
                            gridTiles,
                            updateUiHolder,
                            dialog,
                            listener
                    );
                    break;
                case STAGGERED:
                    bodyView = buildStaggeredBody(
                            current,
                            downloads,
                            loadedThumbnails,
                            themeSettings,
                            isSelectionMode,
                            selectedIndices,
                            gridTiles,
                            updateUiHolder,
                            dialog,
                            listener
                    );
                    break;
                case LIST:
                default:
                    bodyView = buildListBody(
                            current,
                            downloads,
                            hasMultiple,
                            themeSettings,
                            isSelectionMode,
                            selectedIndices,
                            listItems,
                            updateUiHolder,
                            dialog,
                            listener
                    );
                    break;
            }
            dialog.setScrollableBodyView(bodyView);
            updateUiHolder[0].run();

            if (hasAttachedBody[0]) {
                animateExpressiveBodyShift(current, bodyView);
            }
            hasAttachedBody[0] = true;
        };

        // Material 3 Expressive 3-segment Connected Button Group (List | Grid | Staggered)
        ExpressiveViewModeToggle viewModeToggle = new ExpressiveViewModeToggle(
                current,
                themeSettings,
                layoutMode[0],
                newMode -> {
                    if (layoutMode[0] == newMode) return;
                    layoutMode[0] = newMode;
                    saveLayoutMode(newMode);
                    NewXLogger.printInfo(() -> LOG_PREFIX + "layout toggled mode=" + newMode.key);
                    bindBodyLayout.run();
                }
        );
        dialog.setHeaderTrailingView(viewModeToggle);

        bindBodyLayout.run();

        if (loadThumbnails) {
            for (int i = 0; i < downloads.size(); i++) {
                InlineDownloadButton.DownloadItem item = downloads.get(i);
                String thumbnailUrl = item.thumbnailUrl;
                int itemIndex = i;
                if (thumbnailUrl == null) {
                    NewXLogger.printInfo(() ->
                            LOG_PREFIX + "item[" + itemIndex + "] label=" + item.label +
                                    " skipped: no thumbnail URL"
                    );
                    continue;
                }

                NewXLogger.printInfo(() ->
                        LOG_PREFIX + "item[" + itemIndex + "] label=" + item.label +
                                " thumbnail queued network=" + MediaThumbnailLoader.describeUrl(thumbnailUrl) +
                                " cache=" + MediaThumbnailLoader.describeUrl(item.thumbnailCacheUrl)
                );
                MediaThumbnailLoader.load(
                        current,
                        item.thumbnailCacheUrl,
                        thumbnailUrl,
                        bitmap -> {
                            loadedThumbnails.set(itemIndex, bitmap);
                            if (layoutMode[0].isGridBased()) {
                                if (itemIndex < gridTiles.size()) {
                                    GridTile tile = gridTiles.get(itemIndex);
                                    if (tile != null) {
                                        tile.setThumbnail(bitmap);
                                    }
                                }
                            } else if (itemIndex < listItems.size()) {
                                boolean isSelected = selectedIndices.contains(itemIndex);
                                int badgeBg = isSelectionMode[0] && isSelected
                                        ? themeSettings.primaryContainer(current)
                                        : themeSettings.surfaceVariant(current);
                                listItems.get(itemIndex).setLeadingImage(bitmap, badgeBg);
                            }
                            NewXLogger.printInfo(() ->
                                    LOG_PREFIX + "item[" + itemIndex + "] label=" + item.label +
                                            " thumbnail applied size=" + bitmap.getWidth() + "x" + bitmap.getHeight() +
                                            " network=" + MediaThumbnailLoader.describeUrl(thumbnailUrl)
                            );
                        }
                );
            }
        } else {
            NewXLogger.printInfo(() -> LOG_PREFIX + "thumbnail loading disabled; using media-type icons");
        }

        if (mergeButton != null) {
            mergeButton.setOnClickListener(v -> {
                NewXLogger.printInfo(() ->
                        LOG_PREFIX + "merge button tapped selectionMode=" + isSelectionMode[0] +
                                " selected=" + selectedIndices
                );
                List<InlineDownloadButton.DownloadItem> toMerge = new ArrayList<>();
                if (isSelectionMode[0]) {
                    for (int idx : selectedIndices) {
                        InlineDownloadButton.DownloadItem item = downloads.get(idx);
                        if (isImage(item)) {
                            toMerge.add(item);
                        }
                    }
                } else {
                    for (InlineDownloadButton.DownloadItem item : downloads) {
                        if (isImage(item)) {
                            toMerge.add(item);
                        }
                    }
                }
                if (toMerge.size() < 2) return;
                dialog.dismiss();
                listener.onDownloadAndMerge(toMerge);
            });
        }

        downloadButton.setOnClickListener(v -> {
            NewXLogger.printInfo(() ->
                    LOG_PREFIX + "download button tapped selectionMode=" + isSelectionMode[0] +
                            " selected=" + selectedIndices
            );
            if (isSelectionMode[0]) {
                if (selectedIndices.isEmpty()) return;
                dialog.dismiss();
                if (selectedIndices.size() == downloads.size()) {
                    listener.onDownloadAll();
                } else {
                    for (int idx : selectedIndices) {
                        listener.onDownloadItem(idx);
                    }
                }
            } else {
                dialog.dismiss();
                listener.onDownloadAll();
            }
        });

        dialog.show();
        NewXLogger.printInfo(() -> LOG_PREFIX + "picker shown");
    }

    private static void animateExpressiveBodyShift(Context context, View bodyView) {
        bodyView.setAlpha(0f);
        bodyView.setScaleX(0.96f);
        bodyView.setScaleY(0.96f);
        bodyView.setTranslationY(Theme.dpToPx(context, 10f));

        ObjectAnimator fade = ObjectAnimator.ofFloat(bodyView, View.ALPHA, 0f, 1f);
        fade.setDuration(180);
        fade.setInterpolator(M3_EXPRESSIVE_EFFECTS);
        fade.start();

        bodyView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(M3_EXPRESSIVE_SPATIAL)
                .start();
    }

    private static View buildListBody(
            Activity activity,
            List<InlineDownloadButton.DownloadItem> downloads,
            boolean hasMultiple,
            Theme.SettingsSnapshot themeSettings,
            boolean[] isSelectionMode,
            Set<Integer> selectedIndices,
            List<ListItem> listItems,
            Runnable[] updateUiHolder,
            BottomSheetView dialog,
            OnMediaSelectedListener listener
    ) {
        LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < downloads.size(); i++) {
            final int selectedIndex = i;
            InlineDownloadButton.DownloadItem item = downloads.get(i);

            ListItem itemRow = new ListItem(activity, themeSettings);
            itemRow.setTitle(item.label + (hasMultiple ? " " + (i + 1) : ""));
            itemRow.setSubtitle(item.resolution);

            bindItemInteractions(
                    itemRow,
                    selectedIndex,
                    isSelectionMode,
                    selectedIndices,
                    updateUiHolder,
                    dialog,
                    listener
            );

            listItems.add(itemRow);
            listContainer.addView(itemRow);
        }
        return listContainer;
    }

    private static View buildGridBody(
            Activity activity,
            List<InlineDownloadButton.DownloadItem> downloads,
            Theme.SettingsSnapshot themeSettings,
            boolean[] isSelectionMode,
            Set<Integer> selectedIndices,
            List<GridTile> gridTiles,
            Runnable[] updateUiHolder,
            BottomSheetView dialog,
            OnMediaSelectedListener listener
    ) {
        LinearLayout gridContainer = new LinearLayout(activity);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        int padHoriz = Theme.dpToPx(activity, 16f);
        int padTop = Theme.dpToPx(activity, 8f);
        int padBottom = Theme.dpToPx(activity, 12f);
        gridContainer.setPadding(padHoriz, padTop, padHoriz, padBottom);

        int count = downloads.size();
        int columns = resolveColumnsForActivity(activity, count);
        boolean compact = columns >= 3;
        int gap = Theme.dpToPx(activity, compact ? 8f : 10f);

        for (int rowStart = 0; rowStart < count; rowStart += columns) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (rowStart > 0) {
                rowParams.topMargin = gap;
            }
            row.setLayoutParams(rowParams);

            for (int col = 0; col < columns; col++) {
                int index = rowStart + col;
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
                if (col > 0) {
                    cellParams.setMarginStart(gap);
                }

                if (index < count) {
                    InlineDownloadButton.DownloadItem item = downloads.get(index);
                    GridTile tile = new GridTile(
                            activity,
                            themeSettings,
                            resolveIconType(item),
                            columns == 1,
                            compact,
                            false,
                            1f
                    );
                    bindItemInteractions(
                            tile,
                            index,
                            isSelectionMode,
                            selectedIndices,
                            updateUiHolder,
                            dialog,
                            listener
                    );
                    gridTiles.add(tile);
                    row.addView(tile, cellParams);
                } else {
                    // Empty spacer keeps the trailing tile aligned to the same column width.
                    View spacer = new View(activity);
                    row.addView(spacer, cellParams);
                }
            }

            gridContainer.addView(row);
        }
        return gridContainer;
    }

    private static View buildStaggeredBody(
            Activity activity,
            List<InlineDownloadButton.DownloadItem> downloads,
            List<Bitmap> loadedThumbnails,
            Theme.SettingsSnapshot themeSettings,
            boolean[] isSelectionMode,
            Set<Integer> selectedIndices,
            List<GridTile> gridTiles,
            Runnable[] updateUiHolder,
            BottomSheetView dialog,
            OnMediaSelectedListener listener
    ) {
        LinearLayout lanesRow = new LinearLayout(activity);
        lanesRow.setOrientation(LinearLayout.HORIZONTAL);
        int padHoriz = Theme.dpToPx(activity, 16f);
        int padTop = Theme.dpToPx(activity, 8f);
        int padBottom = Theme.dpToPx(activity, 12f);
        lanesRow.setPadding(padHoriz, padTop, padHoriz, padBottom);

        int count = downloads.size();
        int columns = resolveColumnsForActivity(activity, count);
        boolean compact = columns >= 3;
        int gap = Theme.dpToPx(activity, compact ? 8f : 10f);

        LinearLayout[] lanes = new LinearLayout[columns];
        float[] laneHeights = new float[columns];
        for (int col = 0; col < columns; col++) {
            LinearLayout lane = new LinearLayout(activity);
            lane.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams laneParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            );
            if (col > 0) {
                laneParams.setMarginStart(gap);
            }
            lanes[col] = lane;
            lanesRow.addView(lane, laneParams);
        }

        for (int i = 0; i < count; i++) {
            gridTiles.add(null);
        }

        for (int i = 0; i < count; i++) {
            InlineDownloadButton.DownloadItem item = downloads.get(i);
            Bitmap loaded = loadedThumbnails.get(i);
            float aspectRatio = resolveInitialAspectRatio(item, loaded);

            int targetCol = 0;
            for (int col = 1; col < columns; col++) {
                if (laneHeights[col] < laneHeights[targetCol]) {
                    targetCol = col;
                }
            }

            GridTile tile = new GridTile(
                    activity,
                    themeSettings,
                    resolveIconType(item),
                    columns == 1,
                    compact,
                    true,
                    aspectRatio
            );
            bindItemInteractions(
                    tile,
                    i,
                    isSelectionMode,
                    selectedIndices,
                    updateUiHolder,
                    dialog,
                    listener
            );
            gridTiles.set(i, tile);

            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (lanes[targetCol].getChildCount() > 0) {
                tileParams.topMargin = gap;
            }
            lanes[targetCol].addView(tile, tileParams);
            laneHeights[targetCol] += (1f / aspectRatio) + 0.08f;
        }

        return lanesRow;
    }

    private static int resolveColumnsForActivity(Activity activity, int itemCount) {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        float screenWidthDp = dm.widthPixels / Math.max(1f, dm.density);
        return resolveGridColumns(itemCount, screenWidthDp, gridColumnsSetting());
    }

    private static float resolveInitialAspectRatio(
            InlineDownloadButton.DownloadItem item,
            Bitmap loadedBitmap
    ) {
        if (loadedBitmap != null && loadedBitmap.getWidth() > 0 && loadedBitmap.getHeight() > 0) {
            return clampAspectRatio((float) loadedBitmap.getWidth() / loadedBitmap.getHeight());
        }
        if (item != null && item.resolution != null) {
            Matcher matcher = DIMENSIONS_PATTERN.matcher(item.resolution);
            if (matcher.find()) {
                try {
                    int w = Integer.parseInt(matcher.group(1));
                    int h = Integer.parseInt(matcher.group(2));
                    if (w > 0 && h > 0) {
                        return clampAspectRatio((float) w / h);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 1f;
    }

    private static float clampAspectRatio(float ratio) {
        if (Float.isNaN(ratio) || Float.isInfinite(ratio) || ratio <= 0f) return 1f;
        return Math.max(MIN_STAGGERED_ASPECT, Math.min(MAX_STAGGERED_ASPECT, ratio));
    }

    private static void bindItemInteractions(
            View itemView,
            int selectedIndex,
            boolean[] isSelectionMode,
            Set<Integer> selectedIndices,
            Runnable[] updateUiHolder,
            BottomSheetView dialog,
            OnMediaSelectedListener listener
    ) {
        itemView.setOnClickListener(v -> {
            NewXLogger.printInfo(() ->
                    LOG_PREFIX + "item[" + selectedIndex + "] tapped selectionMode=" +
                            isSelectionMode[0]
            );
            if (isSelectionMode[0]) {
                toggleSelection(selectedIndices, selectedIndex, updateUiHolder[0]);
            } else {
                dialog.dismiss();
                listener.onDownloadItem(selectedIndex);
            }
        });

        itemView.setOnLongClickListener(v -> {
            NewXLogger.printInfo(() ->
                    LOG_PREFIX + "item[" + selectedIndex + "] long-pressed selectionMode=" +
                            isSelectionMode[0]
            );
            try {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Throwable ignored) {
            }
            if (!isSelectionMode[0]) {
                isSelectionMode[0] = true;
                selectedIndices.clear();
                selectedIndices.add(selectedIndex);
                updateUiHolder[0].run();
                return true;
            }
            return false;
        });
    }

    private static void toggleSelection(
            Set<Integer> selectedIndices,
            int index,
            Runnable updateUi
    ) {
        if (selectedIndices.contains(index)) {
            selectedIndices.remove(index);
        } else {
            selectedIndices.add(index);
        }
        updateUi.run();
    }

    private static void bindTrailingActions(
            Activity activity,
            ListItem itemRow,
            InlineDownloadButton.DownloadItem item,
            int itemIndex,
            Theme.SettingsSnapshot themeSettings,
            boolean showResolutionButton,
            boolean showCopyLinkButton,
            BottomSheetView dialog,
            OnMediaSelectedListener listener
    ) {
        boolean showResolutions = showResolutionButton && item.resolutionOptions.size() > 1;
        if (!showResolutions && !showCopyLinkButton) {
            itemRow.setTrailingView(null);
            return;
        }

        LinearLayout row = itemRow.beginTrailingButtons();
        if (showResolutions) {
            itemRow.addTrailingIconButton(
                    row,
                    IconView.IconType.RESOLUTION,
                    themeSettings.secondaryText(activity),
                    v -> {
                        dialog.dismiss();
                        listener.onShowResolutions(itemIndex);
                    }
            );
        }
        if (showCopyLinkButton) {
            itemRow.addTrailingIconButton(
                    row,
                    IconView.IconType.COPY_LINK,
                    themeSettings.secondaryText(activity),
                    v -> {
                        dialog.dismiss();
                        Utils.setClipboard(item.url);
                        Utils.showToastShort("Link copied");
                    }
            );
        }
    }

    static boolean isImage(InlineDownloadButton.DownloadItem item) {
        if (item == null) return false;
        if (item.mimeType != null && item.mimeType.startsWith("image/")) return true;
        if (item.label != null && item.label.equalsIgnoreCase("image")) return true;
        if (item.extension != null) {
            String ext = item.extension.toLowerCase(Locale.ROOT);
            return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp");
        }
        return false;
    }

    private static boolean showCopyLinkButton() {
        return SettingsRegistry.getBooleanOrDefault(COPY_LINK_SETTING_ID, true);
    }

    private static boolean resolutionButtonEnabled() {
        return SettingsRegistry.getBooleanOrDefault(RESOLUTION_BUTTON_SETTING_ID, true);
    }

    private static boolean thumbnailsEnabled() {
        return SettingsRegistry.getBooleanOrDefault(THUMBNAILS_SETTING_ID, true);
    }

    private static LayoutMode layoutModeSetting() {
        return LayoutMode.fromKey(SettingsRegistry.getStringOrDefault(LAYOUT_SETTING_ID, LayoutMode.LIST.key));
    }

    private static String gridColumnsSetting() {
        return SettingsRegistry.getStringOrDefault(GRID_COLUMNS_SETTING_ID, GRID_COLUMNS_AUTO);
    }

    static int resolveGridColumns(int itemCount, float screenWidthDp, String settingValue) {
        if (itemCount <= 1) return 1;
        if ("2".equals(settingValue)) return Math.min(itemCount, 2);
        if ("3".equals(settingValue)) return Math.min(itemCount, 3);
        if ("4".equals(settingValue)) return Math.min(itemCount, 4);

        boolean wideScreen = screenWidthDp >= WIDE_SCREEN_DP;
        if (itemCount == 2) return 2;
        if (itemCount == 3) return 3;
        if (itemCount == 4) return wideScreen ? 4 : 2;
        return wideScreen ? 4 : 3;
    }

    private static void saveLayoutMode(LayoutMode mode) {
        if (!SettingsRegistry.isLoaded()) {
            SettingsRegistry.load();
        }
        Setting<?> setting = SettingsRegistry.settingOrNull(LAYOUT_SETTING_ID);
        if (setting instanceof StringSetting text) {
            text.save(mode.key);
        }
    }

    private static boolean mergeButtonEnabled() {
        return SettingsRegistry.getBooleanOrDefault(MERGE_BUTTON_SETTING_ID, true);
    }

    private static IconView.IconType resolveIconType(InlineDownloadButton.DownloadItem item) {
        if (item == null || item.label == null) return IconView.IconType.IMAGE;
        String label = item.label.toLowerCase(Locale.ROOT);
        if (label.contains("gif")) {
            return IconView.IconType.GIF;
        } else if (label.contains("video")) {
            return IconView.IconType.VIDEO;
        }
        return IconView.IconType.IMAGE;
    }

    /**
     * Material 3 Expressive 3-segment Connected Button Group for switching between List, Grid,
     * and Staggered views. Unselected segments keep full-pill outer ends and tight 6dp inner
     * corners; the selected segment morphs smoothly into a full pill with primaryContainer fill.
     */
    private static final class ExpressiveViewModeToggle extends LinearLayout {
        interface OnModeChangedListener {
            void onModeChanged(LayoutMode mode);
        }

        private final Theme.SettingsSnapshot themeSettings;
        private final float outerRadiusPx;
        private final float innerUnselectedRadiusPx;
        private final FrameLayout[] segments = new FrameLayout[3];
        private final IconView[] icons = new IconView[3];
        private final GradientDrawable[] backgrounds = new GradientDrawable[3];
        private final GradientDrawable[] masks = new GradientDrawable[3];
        private final float[] innerRadii = new float[3];
        private LayoutMode selectedMode;
        private ValueAnimator morphAnimator;

        ExpressiveViewModeToggle(
                Context context,
                Theme.SettingsSnapshot themeSettings,
                LayoutMode initialMode,
                OnModeChangedListener listener
        ) {
            super(context);
            this.themeSettings = themeSettings;
            this.selectedMode = initialMode;
            this.outerRadiusPx = Theme.dpToPx(context, 18f);
            this.innerUnselectedRadiusPx = Theme.dpToPx(context, 6f);

            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);

            int segWidth = Theme.dpToPx(context, 38f);
            int segHeight = Theme.dpToPx(context, 36f);
            int gap = Theme.dpToPx(context, 2f);
            int iconSize = Theme.dpToPx(context, 18f);

            LayoutMode[] modes = new LayoutMode[]{
                    LayoutMode.LIST, LayoutMode.GRID, LayoutMode.STAGGERED
            };
            IconView.IconType[] iconTypes = new IconView.IconType[]{
                    IconView.IconType.LIST_VIEW,
                    IconView.IconType.GRID_VIEW,
                    IconView.IconType.STAGGERED_VIEW
            };
            String[] descriptions = new String[]{
                    "List view", "Grid view", "Staggered view"
            };

            for (int i = 0; i < 3; i++) {
                final LayoutMode mode = modes[i];
                innerRadii[i] = (mode == initialMode) ? outerRadiusPx : innerUnselectedRadiusPx;
                backgrounds[i] = new GradientDrawable();
                masks[i] = new GradientDrawable();
                icons[i] = new IconView(context, iconTypes[i], themeSettings.secondaryText(context));
                segments[i] = createSegment(
                        context, segWidth, segHeight, iconSize, icons[i], backgrounds[i], masks[i]
                );
                segments[i].setContentDescription(descriptions[i]);
                if (i > 0) {
                    LayoutParams lp = (LayoutParams) segments[i].getLayoutParams();
                    lp.setMarginStart(gap);
                    segments[i].setLayoutParams(lp);
                }
                segments[i].setOnClickListener(v -> {
                    if (selectedMode == mode) return;
                    setSelectedMode(mode, true);
                    listener.onModeChanged(mode);
                });
                addView(segments[i]);
            }

            applySegmentVisuals();
        }

        private FrameLayout createSegment(
                Context context,
                int width,
                int height,
                int iconSize,
                IconView icon,
                GradientDrawable bg,
                GradientDrawable mask
        ) {
            FrameLayout segment = new FrameLayout(context);
            segment.setLayoutParams(new LayoutParams(width, height));
            mask.setColor(Color.BLACK);
            segment.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(themeSettings.rippleColor(context)),
                    bg,
                    mask
            ));
            segment.setClickable(true);
            segment.setFocusable(true);
            segment.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
            return segment;
        }

        private int indexOf(LayoutMode mode) {
            if (mode == LayoutMode.GRID) return 1;
            if (mode == LayoutMode.STAGGERED) return 2;
            return 0;
        }

        private void setSelectedMode(LayoutMode mode, boolean animate) {
            this.selectedMode = mode;
            int selectedIdx = indexOf(mode);

            if (morphAnimator != null) {
                morphAnimator.cancel();
            }

            if (!animate) {
                for (int i = 0; i < 3; i++) {
                    innerRadii[i] = (i == selectedIdx) ? outerRadiusPx : innerUnselectedRadiusPx;
                }
                applySegmentVisuals();
                return;
            }

            final float[] startRadii = new float[]{innerRadii[0], innerRadii[1], innerRadii[2]};
            final float[] targetRadii = new float[]{
                    selectedIdx == 0 ? outerRadiusPx : innerUnselectedRadiusPx,
                    selectedIdx == 1 ? outerRadiusPx : innerUnselectedRadiusPx,
                    selectedIdx == 2 ? outerRadiusPx : innerUnselectedRadiusPx
            };

            morphAnimator = ValueAnimator.ofFloat(0f, 1f);
            morphAnimator.setDuration(260);
            morphAnimator.setInterpolator(M3_EXPRESSIVE_SPATIAL);
            morphAnimator.addUpdateListener(anim -> {
                float t = (float) anim.getAnimatedValue();
                for (int i = 0; i < 3; i++) {
                    innerRadii[i] = Math.max(0f, startRadii[i] + (targetRadii[i] - startRadii[i]) * t);
                }
                applySegmentVisuals();
            });
            morphAnimator.start();

            View activeSegment = segments[selectedIdx];
            activeSegment.animate().cancel();
            activeSegment.setScaleX(0.92f);
            activeSegment.setScaleY(0.92f);
            activeSegment.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(M3_EXPRESSIVE_SPATIAL)
                    .start();
        }

        private void applySegmentVisuals() {
            Context context = getContext();
            int selectedBg = themeSettings.primaryContainer(context);
            int selectedFg = themeSettings.onPrimaryContainer(context);
            int unselectedBg = themeSettings.surfaceVariant(context);
            int unselectedFg = themeSettings.secondaryText(context);
            int selectedIdx = indexOf(selectedMode);

            for (int i = 0; i < 3; i++) {
                float leftR = (i == 0) ? outerRadiusPx : innerRadii[i];
                float rightR = (i == 2) ? outerRadiusPx : innerRadii[i];
                float[] radii = new float[]{
                        leftR, leftR,
                        rightR, rightR,
                        rightR, rightR,
                        leftR, leftR
                };
                boolean isSelected = (i == selectedIdx);
                backgrounds[i].setCornerRadii(radii);
                backgrounds[i].setColor(isSelected ? selectedBg : unselectedBg);
                masks[i].setCornerRadii(radii);
                icons[i].setIconColor(isSelected ? selectedFg : unselectedFg);
                segments[i].invalidate();
            }
        }
    }

    /**
     * Media thumbnail tile for Grid and Staggered views, following Material 3 Expressive shape and
     * motion cues:
     * - Rounded surface tile without text labels (square in Grid, aspect-ratio-preserving in Staggered).
     * - Tactile spring press feedback and expressive inset + corner morph when selected.
     * - Centered play button for videos and full-pill GIF indicator in the bottom-left for GIFs.
     * - Top-right circular overlay action buttons (resolution / copy link) or selection checkbox.
     */
    private static final class GridTile extends FrameLayout {
        private final Theme.SettingsSnapshot themeSettings;
        private final IconView.IconType mediaType;
        private final boolean singleColumn;
        private final boolean compact;
        private final boolean preserveAspectRatio;
        private float aspectRatio;
        private final float outerCornerPx;
        private final float innerCornerUnselectedPx;
        private final float innerCornerSelectedPx;
        private final GradientDrawable tileBg = new GradientDrawable();
        private final FrameLayout mediaCard;
        private final GradientDrawable mediaCardBg = new GradientDrawable();
        private final ImageView thumbnailView;
        private final IconView placeholderIcon;
        private final LinearLayout topRightContainer;
        private float currentInnerCornerPx;
        private boolean wasSelected = false;
        private boolean hasBoundState = false;
        private ValueAnimator cornerAnimator;

        GridTile(
                Context context,
                Theme.SettingsSnapshot themeSettings,
                IconView.IconType mediaType,
                boolean singleColumn,
                boolean compact,
                boolean preserveAspectRatio,
                float initialAspectRatio
        ) {
            super(context);
            this.themeSettings = themeSettings;
            this.mediaType = mediaType;
            this.singleColumn = singleColumn;
            this.compact = compact;
            this.preserveAspectRatio = preserveAspectRatio;
            this.aspectRatio = clampAspectRatio(initialAspectRatio);
            this.outerCornerPx = Theme.dpToPx(context, compact ? 16f : 20f);
            this.innerCornerUnselectedPx = outerCornerPx;
            this.innerCornerSelectedPx = Theme.dpToPx(context, compact ? 22f : 26f);
            this.currentInnerCornerPx = innerCornerUnselectedPx;

            tileBg.setCornerRadius(outerCornerPx);
            tileBg.setColor(themeSettings.surfaceVariant(context));
            setBackground(tileBg);
            setClipToOutline(true);

            GradientDrawable rippleMask = new GradientDrawable();
            rippleMask.setCornerRadius(outerCornerPx);
            rippleMask.setColor(Color.BLACK);
            setForeground(new RippleDrawable(
                    ColorStateList.valueOf(themeSettings.rippleColor(context)),
                    null,
                    rippleMask
            ));
            setClickable(true);
            setFocusable(true);

            // Inner media container scales inward and morphs corners when selected.
            mediaCard = new FrameLayout(context);
            mediaCardBg.setCornerRadius(innerCornerUnselectedPx);
            mediaCardBg.setColor(themeSettings.surfaceVariant(context));
            mediaCard.setBackground(mediaCardBg);
            mediaCard.setClipToOutline(true);
            addView(mediaCard, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // 1. Placeholder icon (hidden for videos since the centered play badge occupies center)
            placeholderIcon = new IconView(context, mediaType, themeSettings.primaryAccent(context));
            int placeholderSize = Theme.dpToPx(context, compact ? 26f : 32f);
            LayoutParams placeholderParams = new LayoutParams(placeholderSize, placeholderSize, Gravity.CENTER);
            if (mediaType == IconView.IconType.VIDEO) {
                placeholderIcon.setVisibility(View.GONE);
            }
            mediaCard.addView(placeholderIcon, placeholderParams);

            // 2. Full-bleed thumbnail preview
            thumbnailView = new ImageView(context);
            thumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnailView.setVisibility(View.GONE);
            mediaCard.addView(thumbnailView, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // 3. Centered play indicator for videos
            if (mediaType == IconView.IconType.VIDEO) {
                mediaCard.addView(buildCenterPlayBadge(context, compact));
            }

            // 4. Bottom-left full-pill GIF badge for GIFs
            if (mediaType == IconView.IconType.GIF) {
                mediaCard.addView(buildGifBadge(context, compact));
            }

            // 5. Top-right actions (resolution / copy link) or selection checkbox
            topRightContainer = new LinearLayout(context);
            topRightContainer.setOrientation(LinearLayout.HORIZONTAL);
            topRightContainer.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams topRightParams = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END
            );
            int topRightMargin = Theme.dpToPx(context, compact ? 6f : 8f);
            topRightParams.setMargins(topRightMargin, topRightMargin, topRightMargin, topRightMargin);
            addView(topRightContainer, topRightParams);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    animate().cancel();
                    animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(120)
                            .setInterpolator(M3_EXPRESSIVE_EFFECTS)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animate().cancel();
                    animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(260)
                            .setInterpolator(M3_EXPRESSIVE_SPATIAL)
                            .start();
                    break;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int targetHeight;
            if (preserveAspectRatio) {
                int naturalHeight = Math.max(1, Math.round(width / aspectRatio));
                targetHeight = singleColumn
                        ? Math.min(naturalHeight, Theme.dpToPx(getContext(), 360f))
                        : naturalHeight;
            } else {
                targetHeight = singleColumn
                        ? Math.min(width, Theme.dpToPx(getContext(), 240f))
                        : width;
            }
            super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
            );
        }

        void setThumbnail(Bitmap bitmap) {
            if (bitmap != null) {
                if (preserveAspectRatio && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                    float refinedRatio = clampAspectRatio((float) bitmap.getWidth() / bitmap.getHeight());
                    if (Math.abs(refinedRatio - aspectRatio) > 0.02f) {
                        aspectRatio = refinedRatio;
                        requestLayout();
                    }
                }
                thumbnailView.setImageBitmap(bitmap);
                thumbnailView.setVisibility(View.VISIBLE);
                placeholderIcon.setVisibility(View.GONE);
            } else {
                thumbnailView.setImageDrawable(null);
                thumbnailView.setVisibility(View.GONE);
                placeholderIcon.setVisibility(
                        mediaType == IconView.IconType.VIDEO ? View.GONE : View.VISIBLE
                );
            }
        }

        void updateState(
                boolean selectionMode,
                boolean selected,
                InlineDownloadButton.DownloadItem item,
                int itemIndex,
                boolean showResolutionButton,
                boolean showCopyLinkButton,
                BottomSheetView dialog,
                OnMediaSelectedListener listener,
                Runnable onToggleSelection
        ) {
            Context context = getContext();
            boolean showSelected = selectionMode && selected;
            boolean animateSelection = hasBoundState && (showSelected != wasSelected);
            wasSelected = showSelected;
            hasBoundState = true;

            // Outer container shifts to primaryContainer when selected, framing the inset media card.
            tileBg.setColor(showSelected
                    ? themeSettings.primaryContainer(context)
                    : themeSettings.surfaceVariant(context));
            applySelectionMorph(showSelected, animateSelection);

            topRightContainer.removeAllViews();
            if (selectionMode) {
                topRightContainer.setVisibility(View.VISIBLE);
                FrameLayout cbWrapper = new FrameLayout(context);
                int wrapperSize = Theme.dpToPx(context, compact ? 24f : 28f);
                cbWrapper.setLayoutParams(new LinearLayout.LayoutParams(wrapperSize, wrapperSize));

                if (!selected) {
                    GradientDrawable scrim = new GradientDrawable();
                    scrim.setShape(GradientDrawable.OVAL);
                    scrim.setColor(Color.argb(128, 0, 0, 0));
                    cbWrapper.setBackground(scrim);
                }

                IconView.IconType cbType = selected
                        ? IconView.IconType.CHECKBOX_CHECKED
                        : IconView.IconType.CHECKBOX_UNCHECKED;
                int cbColor = selected
                        ? themeSettings.checkboxChecked(context)
                        : Color.WHITE;
                IconView cbIcon = new IconView(context, cbType, cbColor);
                int iconSize = Theme.dpToPx(context, compact ? 18f : 22f);
                cbWrapper.addView(cbIcon, new LayoutParams(iconSize, iconSize, Gravity.CENTER));
                cbWrapper.setOnClickListener(v -> onToggleSelection.run());
                if (animateSelection && selected) {
                    cbWrapper.setScaleX(0.7f);
                    cbWrapper.setScaleY(0.7f);
                    cbWrapper.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(260)
                            .setInterpolator(M3_EXPRESSIVE_SPATIAL)
                            .start();
                }
                topRightContainer.addView(cbWrapper);
            } else {
                boolean showResolutions = showResolutionButton && item.resolutionOptions.size() > 1;
                if (!showResolutions && !showCopyLinkButton) {
                    topRightContainer.setVisibility(View.GONE);
                    return;
                }
                topRightContainer.setVisibility(View.VISIBLE);
                int btnGap = Theme.dpToPx(context, compact ? 4f : 6f);

                if (showResolutions) {
                    View resBtn = buildOverlayActionButton(
                            context,
                            IconView.IconType.RESOLUTION,
                            compact,
                            v -> {
                                dialog.dismiss();
                                listener.onShowResolutions(itemIndex);
                            }
                    );
                    topRightContainer.addView(resBtn);
                }

                if (showCopyLinkButton) {
                    View copyBtn = buildOverlayActionButton(
                            context,
                            IconView.IconType.COPY_LINK,
                            compact,
                            v -> {
                                dialog.dismiss();
                                Utils.setClipboard(item.url);
                                Utils.showToastShort("Link copied");
                            }
                    );
                    if (showResolutions) {
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) copyBtn.getLayoutParams();
                        lp.setMarginStart(btnGap);
                        copyBtn.setLayoutParams(lp);
                    }
                    topRightContainer.addView(copyBtn);
                }
            }
        }

        private void applySelectionMorph(boolean selected, boolean animate) {
            float targetScale = selected ? 0.89f : 1f;
            float targetCorner = selected ? innerCornerSelectedPx : innerCornerUnselectedPx;

            if (cornerAnimator != null) {
                cornerAnimator.cancel();
            }

            if (!animate) {
                mediaCard.setScaleX(targetScale);
                mediaCard.setScaleY(targetScale);
                currentInnerCornerPx = targetCorner;
                mediaCardBg.setCornerRadius(targetCorner);
                mediaCard.invalidateOutline();
                return;
            }

            mediaCard.animate().cancel();
            mediaCard.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(280)
                    .setInterpolator(M3_EXPRESSIVE_SPATIAL)
                    .start();

            final float startCorner = currentInnerCornerPx;
            cornerAnimator = ValueAnimator.ofFloat(0f, 1f);
            cornerAnimator.setDuration(280);
            cornerAnimator.setInterpolator(M3_EXPRESSIVE_SPATIAL);
            cornerAnimator.addUpdateListener(anim -> {
                float t = (float) anim.getAnimatedValue();
                currentInnerCornerPx = Math.max(0f, startCorner + (targetCorner - startCorner) * t);
                mediaCardBg.setCornerRadius(currentInnerCornerPx);
                mediaCard.invalidateOutline();
            });
            cornerAnimator.start();
        }

        private static View buildCenterPlayBadge(Context context, boolean compact) {
            FrameLayout badge = new FrameLayout(context);
            int badgeSize = Theme.dpToPx(context, compact ? 40f : 48f);
            LayoutParams params = new LayoutParams(badgeSize, badgeSize, Gravity.CENTER);
            badge.setLayoutParams(params);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.argb(165, 0, 0, 0));
            circle.setStroke(Theme.dpToPx(context, 1f), Color.argb(56, 255, 255, 255));
            badge.setBackground(circle);

            IconView playIcon = new IconView(context, IconView.IconType.PLAY, Color.WHITE);
            int iconSize = Theme.dpToPx(context, compact ? 20f : 24f);
            badge.addView(playIcon, new LayoutParams(iconSize, iconSize, Gravity.CENTER));
            return badge;
        }

        private static View buildGifBadge(Context context, boolean compact) {
            TextView badge = new TextView(context);
            badge.setText("GIF");
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 10 : 11);
            badge.setTypeface(UpdateFont.customTypefaceOr(Typeface.DEFAULT_BOLD));
            badge.setTextColor(Color.WHITE);
            badge.setLetterSpacing(0.06f);

            int padHoriz = Theme.dpToPx(context, compact ? 6f : 8f);
            int padVert = Theme.dpToPx(context, compact ? 2.5f : 3.5f);
            badge.setPadding(padHoriz, padVert, padHoriz, padVert);

            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(Theme.dpToPx(context, 999f));
            pill.setColor(Color.argb(185, 0, 0, 0));
            pill.setStroke(Theme.dpToPx(context, 1f), Color.argb(40, 255, 255, 255));
            badge.setBackground(pill);

            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START
            );
            int margin = Theme.dpToPx(context, compact ? 6f : 10f);
            params.setMargins(margin, margin, margin, margin);
            badge.setLayoutParams(params);
            return badge;
        }

        private static View buildOverlayActionButton(
                Context context,
                IconView.IconType iconType,
                boolean compact,
                OnClickListener listener
        ) {
            FrameLayout btn = new FrameLayout(context);
            int size = Theme.dpToPx(context, compact ? 28f : 34f);
            btn.setLayoutParams(new LinearLayout.LayoutParams(size, size));

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.argb(165, 0, 0, 0));
            circle.setStroke(Theme.dpToPx(context, 1f), Color.argb(40, 255, 255, 255));

            GradientDrawable mask = new GradientDrawable();
            mask.setShape(GradientDrawable.OVAL);
            mask.setColor(Color.BLACK);

            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(Color.argb(80, 255, 255, 255)),
                    circle,
                    mask
            );
            btn.setBackground(ripple);
            btn.setClickable(true);
            btn.setFocusable(true);

            IconView icon = new IconView(context, iconType, Color.WHITE);
            int iconSize = Theme.dpToPx(context, compact ? 14f : 16f);
            btn.addView(icon, new LayoutParams(iconSize, iconSize, Gravity.CENTER));
            btn.setOnClickListener(listener);
            return btn;
        }
    }
}
