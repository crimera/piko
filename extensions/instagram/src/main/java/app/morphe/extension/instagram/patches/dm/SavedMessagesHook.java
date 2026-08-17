/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.content.Context;
import android.content.Intent;

import static app.morphe.extension.instagram.utils.IgStr.str;


import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.entity.DirectItem;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;

/** Runtime hooks for "Save deleted messages". Fields resolved at patch time via DirectItem entity. */
@SuppressWarnings("unused")
public class SavedMessagesHook {

    private static void piko(String msg) {
        android.util.Log.e("piko", msg);
    }

    private static volatile String sCurrentThreadId;
    private static volatile String sCurrentThreadTitle;

    public static void noteThreadTitle(String title) {
        if (title == null || title.trim().isEmpty()) return;
        sCurrentThreadTitle = title.trim();
        // Persist for the current thread so the all-chats screen and notifications show a name too.
        try {
            if (sCurrentThreadId != null && !sCurrentThreadId.isEmpty()) {
                PikoMessageDb.getInstance(PikoUtils.getContext())
                        .setThreadUsername(sCurrentThreadId, sCurrentThreadTitle);
            }
        } catch (Exception ignored) {}
    }

    public static void noteOpenThreadId(String threadId) {
        if (threadId != null && !threadId.isEmpty()) sCurrentThreadId = threadId;
    }

    /** Hook 6: harvest participant id→username from the thread deserializer's user list. */
    public static void noteThreadUsers(final java.util.List<?> users) {
        if (users == null || users.isEmpty()) return;
        if (!Pref.saveDeletedMessages()) return;
        final java.util.ArrayList<Object> copy;
        try { copy = new java.util.ArrayList<Object>(users); } catch (Throwable t) { return; }
        getWorker().post(new Runnable() { @Override public void run() {
            try {
                PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());
                for (Object u : copy) {
                    if (u == null) continue;
                    try {
                        UserData ud = new UserData(u);
                        String id = ud.getUserId();
                        if (id == null || !id.matches("\\d{6,14}")) continue;
                        String name = ud.getUsername();
                        if (name == null || name.isEmpty()) name = ud.getFullName();
                        if (name != null && !name.isEmpty()) db.putUsername(id, name);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }});
    }

    private static String resolveOpenThreadId() {
        return (sCurrentThreadId != null && !sCurrentThreadId.isEmpty()) ? sCurrentThreadId : null;
    }

    /** Opens the deleted-messages screen for the current thread (or all if unknown). */
    public static void openDeletedMessages(Context ctx) {
        openDeletedMessages(ctx, true);
    }

    /**
     * Opens the deleted-messages screen.
     * @param scopeToCurrentThread when false (Piko Settings entry point), always shows all
     * chats, ignoring any stale current-thread state left over from the last opened DM.
     */
    public static void openDeletedMessages(Context ctx, boolean scopeToCurrentThread) {
        try {
            if (ctx == null) ctx = PikoUtils.getContext();
            if (ctx == null) return;
            String openThreadId = scopeToCurrentThread ? resolveOpenThreadId() : null;
            Intent intent = new Intent(ctx, DeletedMessagesActivity.class);
            if (openThreadId != null && !openThreadId.isEmpty()) {
                intent.putExtra("thread_id", openThreadId);
                if (sCurrentThreadTitle != null && !sCurrentThreadTitle.isEmpty()) {
                    intent.putExtra("thread_title", sCurrentThreadTitle);
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            piko("SavedMessagesHook.openDeletedMessages: " + e);
        }
    }

    // Our own user id, learned from messages whose is_sent_by_viewer flag is set (and persisted),
    // so even a freshly-sent message is recognised as own by comparing sender ids — no session reflection.
    private static volatile String sMyUserId;

    private static String myUserId() {
        if (sMyUserId == null) {
            try {
                Context ctx = PikoUtils.getContext();
                if (ctx != null) {
                    String v = ctx.getSharedPreferences("piko_dm", Context.MODE_PRIVATE)
                            .getString("my_user_id", null);
                    if (v != null && !v.isEmpty()) sMyUserId = v;
                }
            } catch (Exception ignored) {}
        }
        return sMyUserId;
    }

    private static void rememberMyUserId(String id) {
        if (id == null || id.isEmpty() || id.equals(sMyUserId)) return;
        sMyUserId = id;
        try {
            Context ctx = PikoUtils.getContext();
            if (ctx != null) {
                ctx.getSharedPreferences("piko_dm", Context.MODE_PRIVATE)
                        .edit().putString("my_user_id", id).apply();
            }
        } catch (Exception ignored) {}
    }

    /** True when senderId is the logged-in user (an own outgoing message). */
    private static boolean isOwnSender(String senderId) {
        String me = myUserId();
        return senderId != null && me != null && senderId.equals(me);
    }

    /**
     * The open chat's title, but only when it is the currently-open thread AND a 1:1 chat
     * (so the title names exactly one sender). In a group the title is the group name, which
     * would misattribute every sender — return null there so callers fall back to the directory.
     */
    private static String openChatTitleFor(PikoMessageDb db, String threadId) {
        if (threadId == null || sCurrentThreadTitle == null) return null;
        if (!threadId.equals(sCurrentThreadId)) return null;
        return db.getSoleSenderId(threadId) != null ? sCurrentThreadTitle : null;
    }

    // Hook 1 (REST): fires from LX/0gL;.parseFromJson (thread-history loads).
    // Hook 2 (MQTT): fires from LX/0gF;.A0P (real-time MSys delivery).
    // Both pass the item as Object; reflection extracts the fields via DirectItem.

    // Background thread so the MQTT delivery thread is never blocked.
    private static android.os.HandlerThread sWorkerThread;
    private static android.os.Handler sWorker;

    private static synchronized android.os.Handler getWorker() {
        if (sWorker == null) {
            sWorkerThread = new android.os.HandlerThread("piko-dm-hook");
            sWorkerThread.start();
            sWorker = new android.os.Handler(sWorkerThread.getLooper());
        }
        return sWorker;
    }

    // Dedup set of item_ids already queued (bounded to 2000 via eldest-entry eviction).
    private static final java.util.Map<String, Boolean> SEEN_ITEM_IDS =
        java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, Boolean>() {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> e) {
                return size() > 2000;
            }
        });

    // Notification dedup: a live unsend can surface via Hook 2 (re-delivery) and Hook 4 (DB hide).
    // Notify at most once per message_id (bounded, eldest-evicted).
    private static final java.util.Map<String, Boolean> NOTIFIED_IDS =
        java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, Boolean>() {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> e) {
                return size() > 1000;
            }
        });

    /** True the first time this message_id is offered for notification; false on repeats. */
    private static boolean claimNotification(String messageId) {
        if (messageId == null) return true; // can't dedup → allow
        return NOTIFIED_IDS.put(messageId, Boolean.TRUE) == null;
    }

    /** Hook 1 (REST): the parsed DirectItem carries its own thread_key, so no hint is needed. */
    public static void onMessageReceived(final Object item) {
        onMessageReceived(item, null);
    }

    /**
     * Hook 2 (MQTT/MSys): the item's thread_key is null here, so the patch passes the thread id
     * read from the MSys delta (A0P's p2) as {@code threadIdHint}.
     */
    public static void onMessageReceived(final Object item, final String threadIdHint) {
        // Runs on the MQTT thread — return instantly; all work is posted to sWorker.
        if (item == null) return;
        if (!Pref.saveDeletedMessages()) return;
        // Class guard: only X.* (obfuscated IG classes) are DirectItem candidates.
        if (!item.getClass().getName().startsWith("X.")) return;

        getWorker().post(new Runnable() { @Override public void run() {
            processReceivedItem(item, threadIdHint);
        }});
    }

    private static void processReceivedItem(Object item, String threadIdHint) {
        try {
            DirectItem di = new DirectItem(item);
            String senderId = di.getUserId();
            if (di.isSentByViewer()) {
                rememberMyUserId(senderId);
                return;
            }
            if (isOwnSender(senderId)) return;
            String messageId  = di.getItemId();
            boolean deleted = di.isHideInThread();
            // dedup key includes deletion state — alive vs unsent are different events
            if (messageId != null
                    && SEEN_ITEM_IDS.put(messageId + (deleted ? ":1" : ":0"), Boolean.TRUE) != null) return;
            String threadId   = di.getThreadId();
            PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());
            // sender name: id→handle directory first, then thread title, then open-chat title
            String senderUser = db.getUsername(senderId);
            if (senderUser == null) senderUser = db.getThreadUsername(threadId);
            if (senderUser == null) senderUser = openChatTitleFor(db, threadId);
            String content    = di.getText();
            String type       = di.getItemType();
            if (type != null) type = type.trim().toLowerCase();
            long   timestamp  = di.getTimestampMs();

            if ("action_log".equals(type) || "expired_placeholder".equals(type)
                    || "placeholder".equals(type)) return;

            // For non-text items, capture the CDN/permalink so media stays recoverable. A caption
            // must not block this: media-with-caption would otherwise store the caption (not a URL)
            // and become non-tappable — the root of the "media not available" reports.
            if (type != null && !type.equals("text")
                    && (content == null || content.isEmpty() || !content.startsWith("http"))) {
                // Media URL is resolved via the DirectItem entity (patch-time resolved field names).
                // Unsupported shapes (animated_media/story_share/xma/link) return null → "[type]" label.
                String url = di.getMediaUrl();
                // xma reshares carry no CDN media — recover the permalink instead.
                if (url == null && type != null && type.startsWith("xma")) url = di.xmaReshareLink();
                if (url != null && url.startsWith("http")) content = url;
            }

            if (messageId == null) {
                // MQTT subclass (X.0gF) may not resolve item_id — derive a synthetic key from
                // sender + timestamp so a later unsend maps back to the same row.
                if (senderId != null) {
                    messageId = "syn:" + senderId + ":" + timestamp;
                } else {
                    return;
                }
            }

            // MQTT path: thread_id comes from the MSys delta passed as threadIdHint.
            if ((threadId == null || threadId.isEmpty())
                    && threadIdHint != null && !threadIdHint.isEmpty()) {
                threadId = threadIdHint;
            }
            if (threadId == null) threadId = "";

            if (deleted) {
                // Only notify when we previously captured this message alive.
                boolean liveDeletion = db.isStoredAlive(messageId);
                db.insertOrIgnore(messageId, threadId, senderId, senderUser, content, type, timestamp);
                db.markDeleted(messageId);
                if (liveDeletion && claimNotification(messageId)) {
                    String notifySender = (senderUser != null) ? senderUser
                            : db.getThreadUsername(threadId);
                    if (notifySender == null) notifySender = openChatTitleFor(db, threadId);
                    if (notifySender == null) notifySender = db.getSenderDisplay(messageId);
                    String notifyBody   = (content != null && !content.isEmpty())
                            ? content : db.getStoredContent(messageId);
                    notifyDeletion(notifySender, notifyBody, type);
                }

                String storedContent = (content != null && !content.isEmpty())
                        ? content : db.getStoredContent(messageId);
                antiRevokeItem(di, storedContent);
            } else {
                db.insertOrIgnore(messageId, threadId, senderId, senderUser, content, type, timestamp);
            }

            // Backfill the 1:1 sender name into the directory (sole-sender guard prevents group misattribution).
            if (sCurrentThreadTitle != null && threadId.equals(sCurrentThreadId)
                    && senderId != null && senderId.equals(db.getSoleSenderId(threadId))) {
                db.putUsername(senderId, sCurrentThreadTitle);
            }
        } catch (Exception e) {
            piko("SavedMessagesHook.processReceivedItem: " + e);
        }
    }

    private static void antiRevokeItem(DirectItem di, String restoredContent) {
        di.setHideInThread(false);
        if (restoredContent != null && !restoredContent.isEmpty()) {
            di.setText(restoredContent);
        }
    }

    private static void notifyDeletion(String sender, String content, String type) {
        try {
            Context ctx = PikoUtils.getContext();
            if (ctx == null) return;

            android.app.NotificationManager nm =
                (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            String channelId = "piko_deleted_messages";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    channelId, str("piko_deleted_messages_channel"), android.app.NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription(str("piko_deleted_messages_channel_desc"));
                nm.createNotificationChannel(ch);
            }

            String who = (sender != null && !sender.isEmpty()) ? sender : str("piko_someone");
            String body = (content != null && !content.isEmpty())
                    ? content
                    : (type != null && !type.isEmpty()) ? "[" + type + "]" : str("piko_media_deleted_generic");

            Intent intent = new Intent(ctx, DeletedMessagesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int piFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                | (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                    ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0, intent, piFlags);

            int iconRes = ctx.getApplicationInfo().icon;
            android.app.Notification.Builder b =
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                    ? new android.app.Notification.Builder(ctx, channelId)
                    : new android.app.Notification.Builder(ctx);
            android.app.Notification n = b
                .setSmallIcon(iconRes != 0 ? iconRes : android.R.drawable.ic_dialog_info)
                .setContentTitle(String.format(str("piko_deleted_a_message"), who))
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

            nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
        } catch (Exception e) {
            piko("SavedMessagesHook.notifyDeletion: " + e);
        }
    }

    public static void onMessageHiddenFromDb(String serverId, String clientId) {
        try {
            if (!Pref.saveDeletedMessages()) return;

            String itemId = (serverId != null && !serverId.isEmpty()) ? serverId : clientId;
            if (itemId == null) return;

            PikoMessageDb vault = PikoMessageDb.getInstance(PikoUtils.getContext());
            if (!vault.isStored(itemId)) return;

            boolean wasReceived = vault.isStoredAlive(itemId);
            String messageType = vault.getMessageType(itemId);

            vault.markDeleted(itemId);

            if (wasReceived && claimNotification(itemId)) {
                String stored = vault.getStoredContent(itemId);
                boolean isMedia = stored == null || stored.isEmpty()
                        || stored.startsWith("http") || stored.startsWith("[");
                String notifBody = isMedia ? describeMediaType(messageType) : stored;
                String storedThreadId = vault.getThreadIdOf(itemId);
                String name = vault.getThreadUsername(storedThreadId);
                if (name == null) name = openChatTitleFor(vault, storedThreadId);
                if (name == null) name = vault.getSenderDisplay(itemId);
                notifyDeletion(name, notifBody, messageType);
            }
        } catch (Exception e) {
            piko("SavedMessagesHook.onMessageHiddenFromDb: " + e);
        }
    }

    private static String describeMediaType(String type) {
        if (type == null) return str("piko_media_deleted_generic");
        String label;
        switch (type) {
            case "media":
            case "image":           label = str("piko_media_photo"); break;
            case "raven_media":     label = str("piko_media_disappearing_photo"); break;
            case "video":           label = str("piko_media_video"); break;
            case "voice_media":
            case "audio":           label = str("piko_media_voice"); break;
            case "animated_media":  label = str("piko_media_gif"); break;
            case "reel_share":      label = str("piko_media_reel"); break;
            case "story_share":     label = str("piko_media_story"); break;
            case "media_share":     label = str("piko_media_post"); break;
            case "like":            label = str("piko_media_like"); break;
            case "link":            label = str("piko_media_link"); break;
            case "action_log":      label = str("piko_media_activity"); break;
            default:                label = type; break;
        }
        return String.format(str("piko_media_deleted"), label);
    }

}
