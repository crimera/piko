package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;
import android.widget.LinearLayout;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.ui.BottomSheetView;
import app.morphe.extension.newx.ui.IconView;
import app.morphe.extension.newx.ui.ListItem;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;

/**
 * Bottom sheet that lets the user pick one image size or video variant before saving.
 *
 * <p>Image options are the original resolution plus the twimg named sizes; video options are the
 * mp4 variants, labelled with the resolution encoded in each variant URL. Media with a single
 * option is passed straight back to the caller instead of showing a one-row sheet.
 */
public final class ResolutionChooserDialog {
    private static final String LOG_PREFIX = "[PikoNewX][ResolutionChooser] ";

    public interface OnResolutionSelectedListener {
        void onResolutionSelected(InlineDownloadButton.DownloadItem option);
    }

    private ResolutionChooserDialog() {
    }

    public static void show(
            Context context,
            InlineDownloadButton.DownloadItem item,
            String username,
            OnResolutionSelectedListener listener
    ) {
        if (context == null || item == null || listener == null) return;
        if (item.resolutionOptions.size() <= 1) {
            listener.onResolutionSelected(item);
            return;
        }

        Activity activity = NewXUtils.findActivity(context);
        Activity current = activity != null ? activity : InlineDownloadButton.currentActivity();
        if (current == null || current.isFinishing() || current.isDestroyed()) {
            NewXLogger.printInfo(() -> LOG_PREFIX + "show skipped unavailable activity");
            listener.onResolutionSelected(item);
            return;
        }

        BottomSheetView dialog = new BottomSheetView(current);
        dialog.setTitle(isImage(item) ? app.morphe.extension.shared.StringRef.str("piko_newx_ui_resolution_title") : app.morphe.extension.shared.StringRef.str("piko_newx_ui_quality_title"));
        dialog.setSubtitle(subtitleFor(item, username));

        Theme.SettingsSnapshot themeSettings = Theme.snapshot();
        LinearLayout listContainer = new LinearLayout(current);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        for (InlineDownloadButton.DownloadItem option : item.resolutionOptions) {
            ListItem row = new ListItem(current, themeSettings);
            row.setTitle(("Original".equals(option.label) ? app.morphe.extension.shared.StringRef.str("piko_newx_inline_download_quality_original") : ("Large".equals(option.label) ? app.morphe.extension.shared.StringRef.str("piko_newx_inline_download_quality_large") : ("Medium".equals(option.label) ? app.morphe.extension.shared.StringRef.str("piko_newx_inline_download_quality_medium") : ("Small".equals(option.label) ? app.morphe.extension.shared.StringRef.str("piko_newx_inline_download_quality_small") : option.label)))));
            row.setSubtitle(option.detail != null ? option.detail : option.resolution);
            row.setLeadingIcon(
                    resolveIconType(item),
                    themeSettings.primaryAccent(current),
                    themeSettings.surfaceVariant(current)
            );
            row.setOnClickListener(ignored -> {
                NewXLogger.printInfo(() -> LOG_PREFIX + "selected " + option.label);
                dialog.dismiss();
                listener.onResolutionSelected(option);
            });
            listContainer.addView(row);
        }

        dialog.setScrollableBodyView(listContainer);
        dialog.show();
        NewXLogger.printInfo(() -> LOG_PREFIX + "shown options=" + item.resolutionOptions.size());
    }

    private static String subtitleFor(
            InlineDownloadButton.DownloadItem item,
            String username
    ) {
        StringBuilder builder = new StringBuilder();
        if (username != null && !username.trim().isEmpty()) {
            builder.append(app.morphe.extension.shared.StringRef.str("piko_newx_ui_media_author", username.trim()));
        }
        if (item.resolution != null) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(item.resolution);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private static boolean isImage(InlineDownloadButton.DownloadItem item) {
        return item.mimeType != null && item.mimeType.startsWith("image/");
    }

    private static IconView.IconType resolveIconType(InlineDownloadButton.DownloadItem item) {
        if (item.label != null && item.label.toLowerCase().contains("gif")) {
            return IconView.IconType.GIF;
        }
        return isImage(item) ? IconView.IconType.IMAGE : IconView.IconType.VIDEO;
    }
}
