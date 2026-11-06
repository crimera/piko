/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Optional bundle of every chat under one summary, as messengers like Element do: the shade shows
 * one "Instagram" entry that expands into the chats. Without it each chat keeps Instagram's own
 * group of one. The summary never alerts, so whether a chat alerts is decided by the chat alone:
 * alerting records use GROUP_ALERT_CHILDREN, silent ones GROUP_ALERT_SUMMARY (muted, as the summary
 * is silent).
 */
@TargetApi(Build.VERSION_CODES.R)
final class ChatBundle {
    static final String GROUP = "piko_direct_chats";
    private static final String SUMMARY_TAG = "piko_direct_summary";
    private static final int SUMMARY_ID = 64279;
    /** After the post or cancel that asked for it has reached the NotificationManager. */
    private static final long REFRESH_DELAY_MS = 300;
    private static final int MAX_LINES = 6;

    private static Handler main;
    private static final Runnable REFRESH = ChatBundle::refreshNow;

    private ChatBundle() {
    }

    static boolean enabled() {
        try {
            return Pref.bundleChatNotifications();
        } catch (Throwable ex) {
            return false;
        }
    }

    /**
     * Group and alert behaviour of a chat record. {@code silentGroup}: without the bundle, a silent
     * record with no group of its own joins it, so that GROUP_ALERT_SUMMARY mutes it.
     */
    static void apply(Notification.Builder builder, Notification original, boolean alert, String silentGroup) {
        if (enabled()) {
            builder.setGroup(GROUP);
            builder.setGroupAlertBehavior(alert ? Notification.GROUP_ALERT_CHILDREN : Notification.GROUP_ALERT_SUMMARY);
        } else if (!alert) {
            builder.setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
            if (TextUtils.isEmpty(original.getGroup())) builder.setGroup(silentGroup);
        }
    }

    /**
     * For an update of a record already posted: the record keeps the behaviour of its earlier post,
     * so one first posted silently would stay muted when this update should alert.
     */
    static void applyUpdate(Notification.Builder builder, boolean alert) {
        if (enabled()) {
            builder.setGroup(GROUP);
            builder.setGroupAlertBehavior(alert ? Notification.GROUP_ALERT_CHILDREN : Notification.GROUP_ALERT_SUMMARY);
        } else if (alert) {
            builder.setGroupAlertBehavior(Notification.GROUP_ALERT_ALL);
        }
    }

    /** Brings the summary in line with the chats showing, shortly after a post or cancel. */
    static void schedule() {
        try {
            synchronized (ChatBundle.class) {
                if (main == null) main = new Handler(Looper.getMainLooper());
            }
            main.removeCallbacks(REFRESH);
            main.postDelayed(REFRESH, REFRESH_DELAY_MS);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to schedule the chat summary", ex);
        }
    }

    private static void refreshNow() {
        try {
            Context context = Utils.getContext();
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            boolean enabled = enabled();
            boolean summaryShowing = false;
            List<StatusBarNotification> chats = new ArrayList<>();
            for (StatusBarNotification posted : manager.getActiveNotifications()) {
                Notification n = posted.getNotification();
                if (posted.getId() == SUMMARY_ID && SUMMARY_TAG.equals(posted.getTag())) {
                    summaryShowing = true;
                    continue;
                }
                // Counted even with the bundle off: chats posted before it was switched off are still in the group.
                if (!GROUP.equals(n.getGroup()) || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                // A bubbled chat whose record is hidden is not in the shade.
                Notification.BubbleMetadata bubble = n.getBubbleMetadata();
                if (bubble != null && bubble.isNotificationSuppressed()) continue;
                chats.add(posted);
            }
            // Cancelling a summary makes Android cancel every chat in its group, so it only goes once
            // the group is empty. A group of one is shown as that chat, so one chat needs no summary,
            // but one already showing is kept (and kept current) rather than taking the chat with it.
            if (chats.isEmpty()) {
                if (summaryShowing) {
                    manager.cancel(SUMMARY_TAG, SUMMARY_ID);
                    NotificationLog.event("BUNDLE summary removed, no chats left");
                }
                return;
            }
            if (!summaryShowing && (chats.size() < 2 || !enabled)) return;
            Collections.sort(chats, (a, b) -> Long.compare(b.getNotification().when, a.getNotification().when));
            Notification latest = chats.get(0).getNotification();

            Notification.InboxStyle style = new Notification.InboxStyle();
            for (int i = 0; i < chats.size() && i < MAX_LINES; i++) {
                CharSequence line = line(chats.get(i).getNotification());
                if (line != null) style.addLine(line);
            }
            String title = Texts.chats(chats.size());
            if (chats.size() > MAX_LINES) style.setSummaryText(Texts.moreChats(chats.size() - MAX_LINES));

            Notification.Builder builder = new Notification.Builder(context, latest.getChannelId())
                    .setSmallIcon(latest.getSmallIcon())
                    .setColor(latest.color)
                    .setContentTitle(title)
                    .setContentText(line(latest))
                    .setStyle(style)
                    .setNumber(chats.size())
                    .setWhen(latest.when)
                    .setShowWhen(true)
                    .setGroup(GROUP)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                    .setOnlyAlertOnce(true)
                    .setVisibility(Notification.VISIBILITY_PRIVATE);
            PendingIntent open = openApp(context);
            if (open != null) builder.setContentIntent(open);
            manager.notify(SUMMARY_TAG, SUMMARY_ID, builder.build());
            NotificationLog.event("BUNDLE summary chats=" + chats.size());
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to update the chat summary", ex);
            NotificationLog.event("FAILED bundle summary: " + android.util.Log.getStackTraceString(ex));
        }
    }

    /** "Chat: last message", for the summary's lines. */
    private static CharSequence line(Notification n) {
        Bundle extras = n.extras;
        CharSequence chat = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (TextUtils.isEmpty(chat)) chat = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = null;
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0 && messages[messages.length - 1] instanceof Bundle) {
            text = ((Bundle) messages[messages.length - 1]).getCharSequence("text");
        }
        if (TextUtils.isEmpty(text)) text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(chat)) return text;
        if (TextUtils.isEmpty(text)) return chat;
        return TextUtils.concat(chat, ": ", text);
    }

    private static PendingIntent openApp(Context context) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch == null) return null;
        return PendingIntent.getActivity(context, SUMMARY_ID, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
