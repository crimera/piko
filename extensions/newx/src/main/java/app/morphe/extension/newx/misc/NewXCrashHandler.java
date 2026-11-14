package app.morphe.extension.newx.misc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import app.morphe.extension.shared.Utils;

/**
 * Captures uncaught NewX crashes to a log file and surfaces a notification
 * with share and copy-to-clipboard actions.
 *
 * <p>The report is written synchronously to app-private storage so it survives
 * scoped storage restrictions, then re-surfaced on the next launch (when the
 * process is healthy enough to toast and notify reliably).</p>
 */
public final class NewXCrashHandler implements Thread.UncaughtExceptionHandler {
    static final String EXTRA_CRASH_PATH = "app.morphe.extension.newx.extra.CRASH_PATH";
    static final int MAX_REPORT_CHARS = 128 * 1024;
    static final int MAX_SHARE_CHARS = 48 * 1024;

    private static final String PREFS_NAME = "piko_newx_crash";
    private static final String KEY_PENDING_CRASH = "pending_crash_path";
    private static final String CRASH_DIRECTORY = "piko-newx-crash";
    private static final String CHANNEL_ID = "piko_newx_crash";
    private static final int NOTIFICATION_ID = 0x50494B58;
    private static final int MAX_OLD_FILES = 5;
    private static final String TRUNCATION_SUFFIX = "\n[text truncated]";

    private static volatile boolean installed;

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    private NewXCrashHandler(Context context, Thread.UncaughtExceptionHandler defaultHandler) {
        this.context = context;
        this.defaultHandler = defaultHandler;
    }

    /**
     * Installs the crash handler and surfaces any crash left pending by the
     * previous process. Safe to call once from application init.
     */
    public static synchronized void install(Context context) {
        if (context == null) return;
        Context applicationContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        if (!installed) {
            installed = true;
            Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
            if (!(current instanceof NewXCrashHandler)) {
                Thread.setDefaultUncaughtExceptionHandler(
                        new NewXCrashHandler(applicationContext, current));
            }
        }
        try {
            showPendingCrash(applicationContext);
        } catch (Throwable ignored) {
            // Diagnostics must never break app startup.
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            File report = saveCrashReport(context, throwable);
            showToast(context, "Piko crash log saved: " + report.getName());
            showCrashNotification(context, report);
        } catch (Throwable ignored) {
            // The original handler must still run even if reporting fails.
        }
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
            return;
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }

    /** Pure report assembly; kept android-free so it stays unit-testable. */
    static String formatReport(
            String deviceInfo,
            String appInfo,
            String threadName,
            String stackTrace) {
        String safeDeviceInfo = deviceInfo != null ? deviceInfo : "Unavailable";
        String safeAppInfo = appInfo != null ? appInfo : "Unavailable";
        String safeThreadName = threadName != null ? threadName : "unknown";
        String safeStackTrace = stackTrace != null ? stackTrace : "Unavailable";
        StringBuilder report = new StringBuilder();
        report.append("--- Device Info ---\n").append(safeDeviceInfo).append("\n\n");
        report.append("--- App Info ---\n").append(safeAppInfo).append("\n\n");
        report.append("Thread: ").append(safeThreadName).append("\n\n");
        report.append("--- Stack Trace ---\n");
        int headroom = MAX_REPORT_CHARS - TRUNCATION_SUFFIX.length() - report.length();
        if (headroom < 0) headroom = 0;
        report.append(boundText(safeStackTrace, headroom));
        return report.toString();
    }

    static String boundText(String value, int maxChars) {
        if (value == null) return "";
        if (maxChars < 0) maxChars = 0;
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + TRUNCATION_SUFFIX;
    }

    static File saveCrashReport(Context context, Throwable throwable) throws IOException {
        String stackTrace;
        try {
            stackTrace = Log.getStackTraceString(throwable);
        } catch (Throwable ignored) {
            stackTrace = String.valueOf(throwable);
        }
        String threadName;
        try {
            threadName = Thread.currentThread().getName();
        } catch (Throwable ignored) {
            threadName = "unknown";
        }
        String report = formatReport(
                collectDeviceInfo(), collectAppInfo(context), threadName, stackTrace);
        File directory = crashDirectory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create crash directory");
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)
                .format(new Date());
        File destination = new File(directory, "piko-crash-" + timestamp + ".txt");
        writeBytes(destination, report.getBytes(StandardCharsets.UTF_8));
        crashPrefs(context).edit().putString(KEY_PENDING_CRASH, destination.getAbsolutePath()).commit();
        return destination;
    }

    /** Resolves the crash file referenced by a share/copy action, if it still exists. */
    static File reportForIntent(Context context, Intent intent) {
        if (intent != null) {
            File fromExtra = fileIfExists(intent.getStringExtra(EXTRA_CRASH_PATH));
            if (fromExtra != null) return fromExtra;
        }
        File pending = fileIfExists(crashPrefs(context).getString(KEY_PENDING_CRASH, null));
        if (pending != null) return pending;
        return newestCrashFile(context);
    }

    static String readBounded(File file, int maxChars) {
        if (file == null || !file.isFile()) return null;
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            int cap = Math.max(maxChars, 0) * 4;
            while ((read = input.read(buffer)) != -1) {
                int remaining = cap - total;
                if (remaining <= 0) break;
                output.write(buffer, 0, Math.min(read, remaining));
                total += Math.min(read, remaining);
            }
            return boundText(output.toString(StandardCharsets.UTF_8.name()), maxChars);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static PendingIntent actionIntent(Context context, Class<?> receiver, File report, int requestCode) {
        Intent intent = new Intent(context, receiver);
        intent.setAction(receiver.getName() + ".ACTION");
        intent.putExtra(EXTRA_CRASH_PATH, report.getAbsolutePath());
        return PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags());
    }

    /**
     * Direct share intent for the notification action. Android 12+ blocks
     * notification trampolines, so share must not hop through a broadcast
     * receiver that calls startActivity; it launches the chooser directly.
     */
    static PendingIntent shareIntent(Context context, File report) {
        String text = shareText(report);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "Piko crash log: " + report.getName());
        share.putExtra(Intent.EXTRA_TEXT, text != null ? text : report.getName());
        Intent chooser = Intent.createChooser(share, "Share crash log");
        return PendingIntent.getActivity(context, 1, chooser, pendingIntentFlags());
    }

    static String shareText(File report) {
        return readBounded(report, MAX_SHARE_CHARS);
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    /**
     * Throws to simulate a crash for testing crash logging. Uses {@link Error}
     * instead of {@link Exception} so it escapes the settings action try/catch
     * and reaches this handler as an uncaught throwable.
     */
    public static void testCrash(String source) {
        throw new AssertionError("Piko requested test crash" + (source != null ? ": " + source : ""));
    }

    static void showToast(Context context, String message) {
        if (context == null || message == null) return;
        Runnable show = () -> {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        };
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                show.run();
                return;
            }
            new Handler(Looper.getMainLooper()).post(show);
        } catch (Throwable ignored) {
        }
    }

    private static void showPendingCrash(Context context) {
        pruneOldFiles(context);
        SharedPreferences prefs = crashPrefs(context);
        File pending = fileIfExists(prefs.getString(KEY_PENDING_CRASH, null));
        if (pending == null) return;
        prefs.edit().remove(KEY_PENDING_CRASH).apply();
        try {
            exportToDownloads(context, pending);
        } catch (Throwable ignored) {
        }
        showToast(context, "Piko crash detected, log saved: " + pending.getName());
        try {
            showCrashNotification(context, pending);
        } catch (Throwable ignored) {
        }
    }

    static void showCrashNotification(Context context, File report) {
        if (context == null || report == null) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !notificationsAllowed(context)) return;
        createChannel(manager);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        String preview = readBounded(report, 512);
        builder.setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Piko crash detected")
                .setContentText("Crash log saved: " + report.getName())
                .setStyle(new Notification.BigTextStyle().bigText(
                        preview != null ? preview : report.getName()))
                .addAction(android.R.drawable.ic_menu_share, "Share",
                        shareIntent(context, report))
                .addAction(android.R.drawable.ic_menu_edit, "Copy",
                        actionIntent(context, NewXCrashCopyReceiver.class, report, 2))
                .setAutoCancel(true);
        PendingIntent content = launchIntent(context);
        if (content != null) builder.setContentIntent(content);
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Throwable ignored) {
            // Missing runtime permission or a dead manager must not crash the reporter.
        }
    }

    private static PendingIntent launchIntent(Context context) {
        try {
            Intent launch = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) return null;
            return PendingIntent.getActivity(context, 0, launch, pendingIntentFlags());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean notificationsAllowed(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void createChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Piko crash logs", NotificationManager.IMPORTANCE_HIGH));
        } catch (Throwable ignored) {
        }
    }

    private static String collectDeviceInfo() {
        try {
            return "Brand: " + Build.BRAND + "\n"
                    + "Model: " + Build.MODEL + "\n"
                    + "OS Version: " + Build.VERSION.RELEASE + "\n"
                    + "SDK Level: " + Build.VERSION.SDK_INT;
        } catch (Throwable ignored) {
            return "Unavailable";
        }
    }

    private static String collectAppInfo(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            String patchVersion;
            try {
                patchVersion = Utils.getPatchesReleaseVersion();
            } catch (Throwable ignored) {
                patchVersion = "Unknown";
            }
            return "Package Name: " + context.getPackageName() + "\n"
                    + "Version Name: " + info.versionName + "\n"
                    + "Version Code: " + info.versionCode + "\n"
                    + "Patch version: " + patchVersion;
        } catch (Throwable ignored) {
            return "Unavailable";
        }
    }

    private static void exportToDownloads(Context context, File report) throws IOException {
        String content = readBounded(report, MAX_REPORT_CHARS);
        if (content == null) throw new IOException("Could not read crash report");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportWithMediaStore(context, report.getName(), bytes);
            return;
        }
        File downloads = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
        if (!downloads.isDirectory() && !downloads.mkdirs()) {
            throw new IOException("Could not create Downloads directory");
        }
        writeBytes(new File(downloads, report.getName()), bytes);
    }

    private static void exportWithMediaStore(Context context, String fileName, byte[] bytes)
            throws IOException {
        android.content.ContentResolver resolver = context.getContentResolver();
        android.net.Uri collection = android.provider.MediaStore.Downloads
                .getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
        android.net.Uri destination = resolver.insert(collection, values);
        if (destination == null) throw new IOException("Could not create MediaStore download");
        try {
            try (OutputStream output = resolver.openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("Could not open MediaStore download");
                output.write(bytes);
            }
            android.content.ContentValues completed = new android.content.ContentValues();
            completed.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(destination, completed, null, null);
        } catch (IOException | RuntimeException exception) {
            try {
                resolver.delete(destination, null, null);
            } catch (Throwable ignored) {
            }
            throw exception;
        }
    }

    private static void pruneOldFiles(Context context) {
        try {
            File directory = crashDirectory(context);
            File[] files = directory.listFiles();
            if (files == null || files.length <= MAX_OLD_FILES) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (int index = 0; index < files.length - MAX_OLD_FILES; index++) {
                try {
                    // Never delete the file a posted notification may still reference.
                    if (files[index].getAbsolutePath().equals(
                            crashPrefs(context).getString(KEY_PENDING_CRASH, null))) {
                        continue;
                    }
                    files[index].delete();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static File newestCrashFile(Context context) {
        try {
            File[] files = crashDirectory(context).listFiles();
            if (files == null || files.length == 0) return null;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            return files[files.length - 1].isFile() ? files[files.length - 1] : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File fileIfExists(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            File file = new File(path);
            return file.isFile() ? file : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File crashDirectory(Context context) {
        return new File(context.getFilesDir(), CRASH_DIRECTORY);
    }

    private static SharedPreferences crashPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void writeBytes(File destination, byte[] bytes) throws IOException {
        try (OutputStream output = new FileOutputStream(destination, false)) {
            output.write(bytes);
            output.flush();
        }
    }
}
