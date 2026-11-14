/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.emoji;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.preference.Preference;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.patches.customise.emoji.EmojiFontStorage.Source;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.shared.Utils;

/**
 * The "Emoji font" setting: pick a font and download it with progress, or import a font file. The change is registered with
 * {@code SettingsRestart}, so leaving the settings offers the usual restart.
 */
public final class EmojiFontDialogs {

    private EmojiFontDialogs() {
    }

    /** The file picker key, handled by {@code ActivityHook} and {@code RestorePrefFragment}. */
    public static final String IMPORT_KEY = "piko_pref_add_emoji_font";

    /** Imports a picked file off the main thread and reports how it went. */
    public static void importFile(Context context, Uri uri, Runnable done) {
        Utils.runOnBackgroundThread(() -> {
            EmojiFontStorage.Result result = EmojiFontStorage.importFrom(context, uri);
            Utils.runOnMainThread(() -> {
                switch (result) {
                    case DONE:
                        PikoUtils.toast(str("piko_pref_add_font_success"));
                        break;
                    case NOT_A_FONT:
                        PikoUtils.toast(str("piko_pref_add_font_invalid"));
                        break;
                    case TOO_LARGE:
                        PikoUtils.toast(str("piko_pref_add_font_too_large"));
                        break;
                    default:
                        PikoUtils.toast(str("piko_pref_add_font_fail"));
                        break;
                }
                done.run();
            });
        });
    }

    public static String summary() {
        if (EmojiFontStorage.isImported()) {
            return str("piko_emoji_font_imported");
        }
        Source current = EmojiFontStorage.current();
        return current == null ? str("piko_emoji_font_system") : current.label;
    }

    public static void show(Context context, Preference preference) {
        Source[] sources = Source.values();
        // System default, the downloadable fonts, then importing a file.
        String[] labels = new String[sources.length + 2];
        labels[0] = str("piko_emoji_font_system");
        labels[labels.length - 1] = str("piko_emoji_font_import");
        Source current = EmojiFontStorage.current();
        int checked = EmojiFontStorage.isImported() ? labels.length - 1 : 0;
        for (int i = 0; i < sources.length; i++) {
            labels[i + 1] = sources[i].label + "  (" + sources[i].approximateMegabytes + " MB)";
            if (sources[i] == current) {
                checked = i + 1;
            }
        }

        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                .setTitle(str("piko_emoji_font"))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        if (EmojiFontStorage.hasFont()) {
                            EmojiFontStorage.delete();
                            preference.setSummary(summary());
                        }
                    } else if (which == labels.length - 1) {
                        // The same picker as Add font; RestorePrefFragment hands the file back here.
                        ActivityHook.launchFragment(context, IMPORT_KEY);
                    } else {
                        download(context, preference, sources[which - 1]);
                    }
                })
                .setNegativeButton(str("piko_cancel"), null)
                .show();
    }

    private static void download(Context context, Preference preference, Source source) {
        Context themed = InstagramPreferenceStyle.dialogContext(context);
        ProgressBar bar = new ProgressBar(themed, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setMax(1000);
        FrameLayout frame = new FrameLayout(themed);
        int pad = (int) (24 * context.getResources().getDisplayMetrics().density);
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(bar);

        final boolean[] cancelled = {false};
        AlertDialog dialog = new AlertDialog.Builder(themed)
                .setTitle(String.format(str("piko_emoji_font_downloading"), source.label))
                .setMessage(String.format(str("piko_emoji_font_size"), source.approximateMegabytes))
                .setView(frame)
                .setCancelable(false)
                .setNegativeButton(str("piko_cancel"), (d, w) -> cancelled[0] = true)
                .show();

        Utils.runOnBackgroundThread(() -> {
            EmojiFontStorage.Result result = EmojiFontStorage.download(source, new EmojiFontStorage.Progress() {
                @Override
                public void onProgress(long done, long total) {
                    Utils.runOnMainThread(() -> {
                        long mb = done / (1024 * 1024);
                        if (total > 0) {
                            bar.setIndeterminate(false);
                            bar.setProgress((int) (done * 1000 / total));
                            dialog.setMessage(mb + " / " + total / (1024 * 1024) + " MB");
                        } else {
                            dialog.setMessage(mb + " MB");
                        }
                    });
                }

                @Override
                public boolean isCancelled() {
                    return cancelled[0];
                }
            });

            Utils.runOnMainThread(() -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                switch (result) {
                    case DONE:
                        preference.setSummary(summary());
                        break;
                    case NOT_A_FONT:
                        PikoUtils.toast(str("piko_emoji_font_invalid"));
                        break;
                    case TOO_LARGE:
                    case FAILED:
                        PikoUtils.toast(str("piko_emoji_font_failed"));
                        break;
                    case CANCELLED:
                        break;
                }
            });
        });
    }
}
