/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.HandlerThread;
import android.os.Parcelable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Utils;

/**
 * Optional log of message notifications and of what this patch did with them, kept in
 * Android/data/com.instagram.android/files/piko_notifications.log. It holds message texts and
 * names, and stays on the device until shared.
 */
public final class NotificationLog {
    private static final String FILE_NAME = "piko_notifications.log";
    private static final long MAX_SIZE = 4 * 1024 * 1024;
    private static final String[] EXTRAS = {
            "com.instagram.android.igns.logging.push_category",
            "com.instagram.android.igns.logging.collapse_key",
            "com.instagram.android.igns.logging.sender_id",
            Notification.EXTRA_TITLE,
            Notification.EXTRA_CONVERSATION_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_IS_GROUP_CONVERSATION,
            Notification.EXTRA_SELF_DISPLAY_NAME,
    };

    private static final boolean ENABLED = Boolean.parseBoolean(enabled());
    private static Handler writer;
    private static final AtomicBoolean processLogged = new AtomicBoolean();

    /** Replaced at patch time with "true" when the log is turned on. */
    private static String enabled() {
        return "notificationLogEnabled";
    }

    /** Built with the log, and not switched off in piko's settings. */
    static boolean isEnabled() {
        if (!ENABLED) return false;
        try {
            return app.morphe.extension.instagram.utils.Pref.notificationLog();
        } catch (Throwable ex) {
            return true;
        }
    }

    /** Whether this build has the log at all, so the settings only offer the switch then. */
    public static boolean isBuiltIn() {
        return ENABLED;
    }

    static void event(String event) {
        if (!isEnabled()) return;
        Date now = new Date();
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(now);
        // The first line of every process, so a log that spans restarts can be cut into runs. The
        // epoch time is here because the line prefix is local time without a zone.
        if (processLogged.compareAndSet(false, true)) {
            String start = time + "  PROCESS start pid=" + Process.myPid() + " build=" + buildName()
                    + " at=" + now.getTime() + "\n";
            writer().post(() -> append(start));
        }
        String line = time + "  " + event + "\n";
        writer().post(() -> append(line));
    }

    private static String buildName() {
        try {
            Context context = Utils.getContext();
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable ex) {
            return "unknown";
        }
    }

    /**
     * Short names of the first three frames that are not this patch's, to tell apart who called
     * a hooked method. Empty when the log is off.
     */
    static String callers() {
        if (!isEnabled()) return "";
        StringBuilder out = new StringBuilder();
        int found = 0;
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            String className = frame.getClassName();
            if (className.startsWith("app.morphe.extension.")) continue;
            if (found++ > 0) out.append(',');
            out.append(className.substring(className.lastIndexOf('.') + 1)).append('.').append(frame.getMethodName());
            if (found == 3) break;
        }
        return found == 0 ? "-" : out.toString();
    }

    static void notification(String what, String tag, int id, Notification notification) {
        if (!isEnabled() || notification == null) return;
        StringBuilder text = new StringBuilder(what)
                .append(" tag=").append(tag).append(" id=").append(id)
                .append(" channel=").append(notification.getChannelId())
                .append(" category=").append(notification.category)
                .append(" shortcut=").append(notification.getShortcutId())
                .append(" flags=0x").append(Integer.toHexString(notification.flags));

        Bundle extras = notification.extras;
        for (String key : EXTRAS) {
            Object value = extras.get(key);
            if (value != null) text.append("\n    ").append(shortKey(key)).append(" = ").append(value);
        }
        text.append("\n    largeIcon = ").append(notification.getLargeIcon() != null);
        // Whether a post shows a picture: its style, a big picture, or images on its messages.
        text.append("\n    style = ").append(shortKey(String.valueOf(extras.getString(Notification.EXTRA_TEMPLATE))))
                .append(" picture = ").append(extras.get(Notification.EXTRA_PICTURE) != null
                        || extras.get("android.pictureIcon") != null);

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            for (Parcelable parcelable : messages) {
                if (!(parcelable instanceof Bundle)) continue;
                Bundle message = (Bundle) parcelable;
                Person sender = message.getParcelable("sender_person");
                CharSequence name = message.getCharSequence("sender");
                text.append("\n    message ")
                        .append(sender != null ? sender.getName() + " (key " + sender.getKey() + ")" : name != null ? name : "(you)")
                        .append(" @").append(message.getLong("time"))
                        .append(": ").append(message.getCharSequence("text"));
                if (message.getParcelable("uri") != null) {
                    text.append(" [image ").append(message.getString("type")).append("]");
                }
            }
        }
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                text.append("\n    action ").append(action.title)
                        .append(" semantic=").append(action.getSemanticAction())
                        .append(" inputs=").append(action.getRemoteInputs() != null ? action.getRemoteInputs().length : 0);
            }
        }
        event(text.toString());
    }

    private static String shortKey(String key) {
        int dot = key.lastIndexOf('.');
        return dot >= 0 ? key.substring(dot + 1) : key;
    }

    private static void append(String line) {
        try {
            Context context = Utils.getContext();
            File directory = context.getExternalFilesDir(null);
            if (directory == null) return;
            File file = new File(directory, FILE_NAME);
            if (file.length() > MAX_SIZE) {
                File old = new File(directory, FILE_NAME + ".1");
                old.delete();
                file.renameTo(old);
            }
            try (Writer out = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                out.write(line);
            }
        } catch (Throwable ignored) {
            // The log never gets in the way of notifications.
        }
    }

    private static synchronized Handler writer() {
        if (writer == null) {
            HandlerThread thread = new HandlerThread("piko-notification-log");
            thread.start();
            writer = new Handler(thread.getLooper());
        }
        return writer;
    }
}
