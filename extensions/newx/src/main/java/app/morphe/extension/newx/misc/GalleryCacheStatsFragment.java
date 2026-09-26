package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.ui.Theme;

/** Developer tools screen showing gallery thumbnail cache usage and hit-rate analytics. */
@SuppressWarnings("deprecation")
public final class GalleryCacheStatsFragment extends NewXCustomScreenFragment {
    private LinearLayout rows;
    private TextView status;
    private volatile boolean destroyed;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        destroyed = false;

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NewXSettingsUi.backgroundColor(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Theme.dpToPx(context, 16f);
        content.setPadding(padding, padding, padding, Theme.dpToPx(context, 32f));
        scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));

        status = NewXSettingsUi.summaryText(context);
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowsParams = new LinearLayout.LayoutParams(-1, -2);
        rowsParams.topMargin = Theme.dpToPx(context, 8f);
        content.addView(rows, rowsParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(-1, -2);
        buttonsParams.topMargin = Theme.dpToPx(context, 16f);
        content.addView(buttons, buttonsParams);

        ButtonView refresh = new ButtonView(
                context,
                ButtonView.ButtonStyle.FILLED,
                StringRef.str("piko_newx_gallery_cache_refresh")
        );
        refresh.setOnClickListener(ignored -> loadStats());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, -2, 1f);
        refreshParams.setMarginEnd(Theme.dpToPx(context, 8f));
        buttons.addView(refresh, refreshParams);

        ButtonView clear = new ButtonView(
                context,
                ButtonView.ButtonStyle.TONAL,
                StringRef.str("piko_newx_gallery_cache_clear")
        );
        clear.setOnClickListener(ignored -> confirmClear());
        buttons.addView(clear, new LinearLayout.LayoutParams(0, -2, 1f));

        loadStats();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity instanceof NewXSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(
                    StringRef.str("piko_newx_gallery_cache_stats_title"));
        }
        loadStats();
    }

    @Override
    public void onDestroyView() {
        destroyed = true;
        super.onDestroyView();
    }

    private void loadStats() {
        Activity activity = getActivity();
        if (activity == null) return;
        Context appContext = activity.getApplicationContext();
        if (appContext == null) appContext = activity;
        Context context = appContext;
        setStatus(StringRef.str("piko_newx_gallery_cache_loading").toString());
        new Thread(() -> {
            MediaDiskCache.Stats disk;
            MediaThumbnailLoader.SessionStats session;
            try {
                File dir = cacheDir(context);
                disk = MediaDiskCache.stats(dir);
                session = MediaThumbnailLoader.sessionStats();
            } catch (Throwable t) {
                postFailed();
                return;
            }
            MediaDiskCache.Stats finalDisk = disk;
            MediaThumbnailLoader.SessionStats finalSession = session;
            Activity current = getActivity();
            if (current == null || destroyed) return;
            current.runOnUiThread(() -> {
                if (destroyed) return;
                render(finalDisk, finalSession);
            });
        }).start();
    }

    private void render(MediaDiskCache.Stats disk, MediaThumbnailLoader.SessionStats session) {
        rows.removeAllViews();
        Context context = requireContext();
        List<CharSequence> diskRows = diskRows(disk);
        List<CharSequence> sessionRows = sessionRows(session);
        addSection(
                StringRef.str("piko_newx_gallery_cache_disk_title"),
                diskRows.isEmpty()
                        ? List.of(StringRef.str("piko_newx_gallery_cache_empty"))
                        : diskRows
        );
        addSection(
                StringRef.str("piko_newx_gallery_cache_session_title"),
                sessionRows
        );
        addSection(
                StringRef.str("piko_newx_gallery_cache_limits_title"),
                List.of(StringRef.str(
                        "piko_newx_gallery_cache_limits",
                        formatBytes(MediaDiskCache.MAX_DISK_CACHE_BYTES),
                        formatBytes(MediaDiskCache.MAX_DISK_ENTRY_BYTES),
                        formatBytes(MediaDiskCache.MAX_READ_BYTES)
                ))
        );
        setStatus("");
    }

    private void addSection(CharSequence title, List<CharSequence> lines) {
        Context context = requireContext();
        TextView header = NewXSettingsUi.titleText(context);
        header.setText(title);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.topMargin = rows.getChildCount() == 0 ? 0 : Theme.dpToPx(context, 16f);
        headerParams.bottomMargin = Theme.dpToPx(context, 4f);
        rows.addView(header, headerParams);
        for (CharSequence line : lines) {
            TextView row = NewXSettingsUi.summaryText(context);
            row.setText(line);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = Theme.dpToPx(context, 2f);
            rows.addView(row, params);
        }
        rows.addView(NewXSettingsUi.divider(requireContext()));
    }

    private static List<CharSequence> diskRows(MediaDiskCache.Stats disk) {
        List<CharSequence> lines = new ArrayList<>();
        if (disk.entryCount <= 0) return lines;
        int percent = MediaDiskCache.MAX_DISK_CACHE_BYTES <= 0 ? 0
                : (int) Math.min(100L, disk.totalBytes * 100L / MediaDiskCache.MAX_DISK_CACHE_BYTES);
        lines.add(StringRef.str(
                "piko_newx_gallery_cache_usage",
                formatBytes(disk.totalBytes),
                formatBytes(MediaDiskCache.MAX_DISK_CACHE_BYTES),
                percent
        ));
        lines.add(StringRef.str("piko_newx_gallery_cache_entries", disk.entryCount));
        lines.add(StringRef.str(
                "piko_newx_gallery_cache_largest", formatBytes(disk.largestBytes)));
        if (disk.oldestModified > 0L) {
            lines.add(StringRef.str(
                    "piko_newx_gallery_cache_oldest", formatInstant(disk.oldestModified)));
        }
        if (disk.newestModified > 0L && disk.newestModified != disk.oldestModified) {
            lines.add(StringRef.str(
                    "piko_newx_gallery_cache_newest", formatInstant(disk.newestModified)));
        }
        lines.add(StringRef.str("piko_newx_gallery_cache_tmp", disk.tmpCount));
        long evicted = MediaDiskCache.evictedEntries();
        if (evicted > 0L) {
            lines.add(StringRef.str(
                    "piko_newx_gallery_cache_evicted",
                    evicted,
                    formatBytes(MediaDiskCache.evictedBytes())
            ));
        }
        return lines;
    }

    private static List<CharSequence> sessionRows(MediaThumbnailLoader.SessionStats session) {
        List<CharSequence> lines = new ArrayList<>();
        lines.add(StringRef.str(
                "piko_newx_gallery_cache_hits_memory", session.extensionMemoryHits));
        lines.add(StringRef.str(
                "piko_newx_gallery_cache_hits_loader", session.imageLoaderMemoryHits));
        lines.add(StringRef.str("piko_newx_gallery_cache_hits_disk", session.diskHits));
        lines.add(StringRef.str(
                "piko_newx_gallery_cache_network",
                session.networkSuccess,
                formatBytes(session.bytesDownloaded),
                session.networkFailed
        ));
        lines.add(StringRef.str(
                "piko_newx_gallery_cache_persisted",
                session.entriesPersisted,
                session.oversizedSkipped
        ));
        lines.add(StringRef.str(
                "piko_newx_gallery_cache_memory",
                session.memoryCacheKilobytes,
                session.memoryCacheMaxKilobytes
        ));
        return lines;
    }

    private void confirmClear() {
        Activity activity = getActivity();
        if (activity == null) return;
        DialogView dialog = new DialogView(activity)
                .setTitle(StringRef.str("piko_newx_gallery_cache_clear_title"))
                .setSubtitle(StringRef.str("piko_newx_gallery_cache_clear_message"));
        dialog.getDialog().setCanceledOnTouchOutside(true);
        ButtonView cancel = new ButtonView(
                activity,
                ButtonView.ButtonStyle.TEXT,
                StringRef.str("piko_newx_settings_cancel")
        );
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        ButtonView clear = new ButtonView(
                activity,
                ButtonView.ButtonStyle.TEXT,
                StringRef.str("piko_newx_gallery_cache_clear")
        );
        clear.setTextColor(Color.rgb(244, 33, 46));
        clear.setOnClickListener(ignored -> {
            dialog.dismiss();
            clearCache();
        });
        dialog.addButton(cancel);
        dialog.addButton(clear);
        dialog.show();
    }

    private void clearCache() {
        Activity activity = getActivity();
        if (activity == null) return;
        Context appContext = activity.getApplicationContext();
        if (appContext == null) appContext = activity;
        Context context = appContext;
        setStatus(StringRef.str("piko_newx_gallery_cache_loading").toString());
        new Thread(() -> {
            int deleted;
            try {
                deleted = MediaDiskCache.clear(cacheDir(context));
            } catch (Throwable t) {
                postFailed();
                return;
            }
            int finalDeleted = deleted;
            Activity current = getActivity();
            if (current == null || destroyed) return;
            current.runOnUiThread(() -> {
                if (destroyed) return;
                setStatus(StringRef.str(
                        "piko_newx_gallery_cache_cleared", finalDeleted).toString());
                loadStats();
            });
        }).start();
    }

    private void setStatus(String text) {
        if (status == null) return;
        Activity activity = getActivity();
        if (activity == null) {
            status.setText(text);
            return;
        }
        if (Thread.currentThread() == activity.getMainLooper().getThread()) {
            status.setText(text);
            return;
        }
        activity.runOnUiThread(() -> {
            if (!destroyed) status.setText(text);
        });
    }

    private void postFailed() {
        Activity current = getActivity();
        if (current == null || destroyed) return;
        current.runOnUiThread(() -> {
            if (destroyed) return;
            setStatus(StringRef.str("piko_newx_gallery_cache_failed").toString());
        });
    }

    private static File cacheDir(Context context) {
        File root = context.getCacheDir();
        if (root == null) throw new IllegalStateException("Cache dir is missing");
        return MediaDiskCache.resolveDir(root);
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Gallery cache stats activity is missing");
        return context;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0L) bytes = 0L;
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KiB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private static String formatInstant(long millis) {
        try {
            return new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                    .format(new Date(millis));
        } catch (RuntimeException e) {
            return "—";
        }
    }
}
