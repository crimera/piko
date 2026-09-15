package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.ui.BottomSheetView;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.IconView;
import app.morphe.extension.newx.ui.ListItem;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;

/**
 * Media Picker Bottom Sheet for NewX inline download action.
 * Supports:
 * - Single item tap to download.
 * - Long-press (hold) on any item to enter multi-selection mode.
 * - Checkbox icons for item selection.
 * - Dynamic "Download (N)" action button reflecting the count of selected items.
 * - Auto-exit selection mode back to default view when 0 items are selected.
 * - Copy link trailing action when not in selection mode.
 */
public final class MediaPickerDialog {
    private static final String COPY_LINK_SETTING_ID = "newx.content.media_picker_copy_link";
    private static final String THUMBNAILS_SETTING_ID = "newx.content.media_picker_thumbnails";
    private static final String MERGE_BUTTON_SETTING_ID = "newx.content.media_picker_merge_button";
    private static final String LOG_PREFIX = "[PikoNewX][MediaPicker] ";

    public interface OnMediaSelectedListener {
        void onDownloadItem(int index);
        void onDownloadAll();
        void onDownloadAndMerge(List<InlineDownloadButton.DownloadItem> items);
    }

    private MediaPickerDialog() {
    }

    public static void show(
            Context context,
            List<InlineDownloadButton.DownloadItem> downloads,
            String username,
            String postId,
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

        LinearLayout listContainer = new LinearLayout(current);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        boolean hasMultiple = downloads.size() > 1;
        boolean showCopyLinkButton = showCopyLinkButton();
        boolean loadThumbnails = thumbnailsEnabled();
        Theme.SettingsSnapshot themeSettings = Theme.snapshot();
        NewXLogger.printInfo(() ->
                LOG_PREFIX + "showing picker items=" + downloads.size() +
                        " thumbnailsEnabled=" + loadThumbnails +
                        " copyLinkEnabled=" + showCopyLinkButton
        );

        final Set<Integer> selectedIndices = new LinkedHashSet<>();
        final boolean[] isSelectionMode = new boolean[]{false};
        final List<ListItem> listItems = new ArrayList<>(downloads.size());
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

            for (int i = 0; i < downloads.size(); i++) {
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

                    itemRow.createTrailingIconButton(cbType, cbColor, v -> {
                        if (selectedIndices.contains(itemIndex)) {
                            selectedIndices.remove(itemIndex);
                        } else {
                            selectedIndices.add(itemIndex);
                        }
                        updateUiHolder[0].run();
                    });
                } else if (showCopyLinkButton) {
                    itemRow.createTrailingIconButton(
                            IconView.IconType.COPY_LINK,
                            themeSettings.secondaryText(current),
                            v -> {
                                dialog.dismiss();
                                Utils.setClipboard(item.url);
                                Utils.showToastShort("Link copied");
                            }
                    );
                } else {
                    itemRow.setTrailingView(null);
                }
            }
        };

        // Populate items
        for (int i = 0; i < downloads.size(); i++) {
            final int selectedIndex = i;
            InlineDownloadButton.DownloadItem item = downloads.get(i);

            ListItem itemRow = new ListItem(current, themeSettings);
            itemRow.setTitle(item.label + (hasMultiple ? " " + (i + 1) : ""));
            itemRow.setSubtitle(null);

            IconView.IconType iconType = resolveIconType(item);
            int primaryAccent = themeSettings.primaryAccent(current);
            itemRow.setLeadingIcon(iconType, primaryAccent, themeSettings.surfaceVariant(current));

            if (showCopyLinkButton) {
                View copyLinkButton = itemRow.createTrailingIconButton(
                        IconView.IconType.COPY_LINK,
                        themeSettings.secondaryText(current),
                        v -> {
                            dialog.dismiss();
                            Utils.setClipboard(item.url);
                            Utils.showToastShort("Link copied");
                        }
                );
                itemRow.setTrailingView(copyLinkButton);
            }

            // Normal tap
            itemRow.setOnClickListener(v -> {
                NewXLogger.printInfo(() ->
                        LOG_PREFIX + "item[" + selectedIndex + "] tapped selectionMode=" +
                                isSelectionMode[0]
                );
                if (isSelectionMode[0]) {
                    if (selectedIndices.contains(selectedIndex)) {
                        selectedIndices.remove(selectedIndex);
                    } else {
                        selectedIndices.add(selectedIndex);
                    }
                    updateUiHolder[0].run();
                } else {
                    dialog.dismiss();
                    listener.onDownloadItem(selectedIndex);
                }
            });

            // Long-press / Hold to enter selection mode
            itemRow.setOnLongClickListener(v -> {
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

            listItems.add(itemRow);
            listContainer.addView(itemRow);
        }

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
                            boolean isSelected = selectedIndices.contains(itemIndex);
                            int badgeBg = isSelectionMode[0] && isSelected
                                    ? themeSettings.primaryContainer(current)
                                    : themeSettings.surfaceVariant(current);
                            listItems.get(itemIndex).setLeadingImage(bitmap, badgeBg);
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

        dialog.setScrollableBodyView(listContainer);
        dialog.show();
        NewXLogger.printInfo(() -> LOG_PREFIX + "picker shown");
    }

    static boolean isImage(InlineDownloadButton.DownloadItem item) {
        if (item == null) return false;
        if (item.mimeType != null && item.mimeType.startsWith("image/")) return true;
        if (item.label != null && item.label.equalsIgnoreCase("image")) return true;
        if (item.extension != null) {
            String ext = item.extension.toLowerCase();
            return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp");
        }
        return false;
    }

    private static boolean showCopyLinkButton() {
        return SettingsRegistry.getBooleanOrDefault(COPY_LINK_SETTING_ID, true);
    }

    private static boolean thumbnailsEnabled() {
        return SettingsRegistry.getBooleanOrDefault(THUMBNAILS_SETTING_ID, true);
    }

    private static boolean mergeButtonEnabled() {
        return SettingsRegistry.getBooleanOrDefault(MERGE_BUTTON_SETTING_ID, true);
    }

    private static IconView.IconType resolveIconType(InlineDownloadButton.DownloadItem item) {
        if (item == null || item.label == null) return IconView.IconType.IMAGE;
        String label = item.label.toLowerCase();
        if (label.contains("gif")) {
            return IconView.IconType.GIF;
        } else if (label.contains("video")) {
            return IconView.IconType.VIDEO;
        }
        return IconView.IconType.IMAGE;
    }
}
