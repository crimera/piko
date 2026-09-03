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

    public interface OnMediaSelectedListener {
        void onDownloadItem(int index);
        void onDownloadAll();
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
        if (context == null || downloads == null || downloads.isEmpty() || listener == null) return;

        Activity activity = NewXUtils.findActivity(context);
        Activity current = activity != null ? activity : InlineDownloadButton.currentActivity();
        if (current == null || current.isFinishing() || current.isDestroyed()) return;

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
        Theme.SettingsSnapshot themeSettings = Theme.snapshot();

        final Set<Integer> selectedIndices = new LinkedHashSet<>();
        final boolean[] isSelectionMode = new boolean[]{false};
        final List<ListItem> listItems = new ArrayList<>(downloads.size());
        final List<Bitmap> loadedThumbnails = new ArrayList<>(downloads.size());
        for (int i = 0; i < downloads.size(); i++) {
            loadedThumbnails.add(null);
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
            // Automatically revert to default view if 0 items are selected
            if (isSelectionMode[0] && selectedCount == 0) {
                isSelectionMode[0] = false;
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

        for (int i = 0; i < downloads.size(); i++) {
            String thumbnailUrl = downloads.get(i).thumbnailUrl;
            if (thumbnailUrl == null) continue;

            int itemIndex = i;
            MediaThumbnailLoader.load(thumbnailUrl, bitmap -> {
                loadedThumbnails.set(itemIndex, bitmap);
                boolean isSelected = selectedIndices.contains(itemIndex);
                int badgeBg = isSelectionMode[0] && isSelected
                        ? themeSettings.primaryContainer(current)
                        : themeSettings.surfaceVariant(current);
                listItems.get(itemIndex).setLeadingImage(bitmap, badgeBg);
            });
        }

        downloadButton.setOnClickListener(v -> {
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
    }

    private static boolean showCopyLinkButton() {
        return SettingsRegistry.getBooleanOrDefault(COPY_LINK_SETTING_ID, true);
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
