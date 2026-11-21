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
            NewXCrashHandler.showToast(context, "Crash log is no longer available");
            return;
        }
        String text = NewXCrashHandler.readBounded(report, NewXCrashHandler.MAX_REPORT_CHARS);
        if (text == null || text.isEmpty()) {
            NewXCrashHandler.showToast(context, "Could not read crash log");
            return;
        }
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("No clipboard service");
            clipboard.setPrimaryClip(ClipData.newPlainText("Piko crash log", text));
            NewXCrashHandler.showToast(context, "Crash log copied to clipboard");
        } catch (Throwable ignored) {
            NewXCrashHandler.showToast(context, "Could not copy crash log");
        }
    }
}
