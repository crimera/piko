package app.morphe.extension.xlite.misc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.xlite.ui.ButtonView;
import app.morphe.extension.xlite.ui.DialogView;
import app.morphe.extension.xlite.ui.IconView;
import app.morphe.extension.xlite.ui.ListItem;
import app.morphe.extension.xlite.ui.Theme;
import app.morphe.extension.xlite.utils.XLiteUtils;

/**
 * Media Picker Dialog for X-Lite inline download action.
 */
public final class MediaPickerDialog {

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

        Activity activity = XLiteUtils.findActivity(context);
        Activity current = activity != null ? activity : InlineDownloadButton.currentActivity();
        if (current == null || current.isFinishing() || current.isDestroyed()) return;

        DialogView dialog = new DialogView(current);
        dialog.setTitle("Download media");
        if (username != null && !username.trim().isEmpty()) {
            dialog.setSubtitle("From @" + username.trim());
        } else {
            dialog.setSubtitle("Select media to save to your device");
        }

        LinearLayout listContainer = new LinearLayout(current);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        boolean hasMultiple = downloads.size() > 1;

        // Add individual media rows
        for (int i = 0; i < downloads.size(); i++) {
            final int selectedIndex = i;
            InlineDownloadButton.DownloadItem item = downloads.get(i);

            ListItem itemRow = new ListItem(current);
            itemRow.setTitle(item.label + (hasMultiple ? " " + (i + 1) : ""));

            itemRow.setSubtitle(null);

            IconView.IconType iconType = resolveIconType(item);
            int primaryAccent = Theme.primaryAccent(current);
            itemRow.setLeadingIcon(iconType, primaryAccent, Theme.surfaceVariant(current));

            View copyLinkButton = itemRow.createTrailingIconButton(
                    IconView.IconType.COPY_LINK,
                    Theme.secondaryText(current),
                    v -> {
                        dialog.dismiss();
                        Utils.setClipboard(item.url);
                        Utils.showToastShort("Link copied");
                    }
            );
            itemRow.setTrailingView(copyLinkButton);

            itemRow.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onDownloadItem(selectedIndex);
            });

            listContainer.addView(itemRow);
        }

        dialog.setScrollableBodyView(listContainer);

        // Cancel button (Text Button)
        ButtonView cancelButton = new ButtonView(current, ButtonView.ButtonStyle.TEXT, "Cancel");
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        dialog.addButton(cancelButton);

        // Download All button (Filled Button) if multiple media items exist
        if (hasMultiple) {
            ButtonView downloadAllButton = new ButtonView(
                    current,
                    ButtonView.ButtonStyle.FILLED,
                    "Download All (" + downloads.size() + ")"
            );
            downloadAllButton.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onDownloadAll();
            });
            dialog.addButton(downloadAllButton);
        }

        dialog.show();
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
