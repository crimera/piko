package app.morphe.extension.newx.misc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;

/** Shares the saved NewX crash log from the crash notification. */
public final class NewXCrashShareReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        File report = NewXCrashHandler.reportForIntent(context, intent);
        if (report == null) {
            NewXCrashHandler.showToast(context, "Crash log is no longer available");
            return;
        }
        String text = NewXCrashHandler.readBounded(report, NewXCrashHandler.MAX_SHARE_CHARS);
        if (text == null || text.isEmpty()) {
            NewXCrashHandler.showToast(context, "Could not read crash log");
            return;
        }
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Piko crash log: " + report.getName());
            share.putExtra(Intent.EXTRA_TEXT, text);
            share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(share, "Share crash log");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Throwable ignored) {
            NewXCrashHandler.showToast(context, "Could not share crash log");
        }
    }
}
