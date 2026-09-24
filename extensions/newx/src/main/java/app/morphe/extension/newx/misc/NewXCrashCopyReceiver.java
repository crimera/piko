package app.morphe.extension.newx.misc;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import java.io.File;

/** Copies the saved NewX crash log to the clipboard from the crash notification. */
public final class NewXCrashCopyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        File report = NewXCrashHandler.reportForIntent(context, intent);
        if (report == null) {
            NewXCrashHandler.showToast(context, NewXCrashHandler.localized(context, "piko_newx_ui_crash_gone"));
            return;
        }
        String text = NewXCrashHandler.readBounded(report, NewXCrashHandler.MAX_REPORT_CHARS);
        if (text == null || text.isEmpty()) {
            NewXCrashHandler.showToast(context, NewXCrashHandler.localized(context, "piko_newx_ui_crash_read_failed"));
            return;
        }
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("No clipboard service");
            clipboard.setPrimaryClip(ClipData.newPlainText(NewXCrashHandler.localized(context, "piko_newx_ui_crash_clip_label"), text));
            NewXCrashHandler.showToast(context, NewXCrashHandler.localized(context, "piko_newx_ui_crash_copied"));
        } catch (Throwable ignored) {
            NewXCrashHandler.showToast(context, NewXCrashHandler.localized(context, "piko_newx_ui_crash_copy_failed"));
        }
    }
}
