/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.content.Context;
import android.content.Intent;

import static app.morphe.extension.instagram.utils.IgStr.str;

import java.lang.reflect.Field;

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
                        if (name == null || name.isEmpty()) name = ud.getFullname();
                        if (name != null && !name.isEmpty()) db.putUsername(id, name);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }});
    }

    /** Most-recent DM action-bar controller, held weakly. Thread id is extracted lazily via BFS. */
    private static java.lang.ref.WeakReference<Object> sOpenThreadController;

    /** Hook 5: records the action-bar controller so its thread id can be read on button click. */
    public static void noteOpenThreadController(final Object controller) {
        if (controller == null) return;
        sOpenThreadController = new java.lang.ref.WeakReference<>(controller);
    }

    private static String resolveOpenThreadId() {
        if (sCurrentThreadId != null && !sCurrentThreadId.isEmpty()) return sCurrentThreadId;
        java.lang.ref.WeakReference<Object> ref = sOpenThreadController;
        Object controller = (ref != null) ? ref.get() : null;
        if (controller == null) return null;
        return deepFindThreadId(controller);
    }

    /** Opens the deleted-messages screen for the currently-open thread (or all if unknown). */
    public static void openDeletedMessages(Context ctx) {
        try {
            if (ctx == null) ctx = PikoUtils.getContext();
            if (ctx == null) return;
            String openThreadId = resolveOpenThreadId();
            Intent intent = new Intent(ctx, DeletedMessagesActivity.class);
            if (openThreadId != null && !openThreadId.isEmpty()) {
                intent.putExtra("thread_id", openThreadId);
            }
            if (sCurrentThreadTitle != null && !sCurrentThreadTitle.isEmpty()) {
                intent.putExtra("thread_title", sCurrentThreadTitle);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            piko("SavedMessagesHook.openDeletedMessages: " + e);
        }
    }

    // The logged-in user's id, used to recognise our own messages. Learned from messages whose
    // is_sent_by_viewer flag is set (own messages on inbox/thread loads), then persisted — so
    // even a freshly-sent message (whose flag isn't set yet at capture) is recognised as own by
    // comparing its sender id. This avoids any UserSession reflection.
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
     * The open chat's action-bar title, but ONLY when that thread is the currently-open one AND it
     * is a 1:1 chat (so the title names exactly one sender). In a group the title is the group name,
     * which names nobody specific — returning it would misattribute every sender to the same name.
     * Returns null in that case so callers fall through to the per-sender directory / numeric id.
     */
    private static String openChatTitleFor(PikoMessageDb db, String threadId) {
        if (threadId == null || sCurrentThreadTitle == null) return null;
        if (!threadId.equals(sCurrentThreadId)) return null;
        return db.getSoleSenderId(threadId) != null ? sCurrentThreadTitle : null;
    }

    // -------------------------------------------------------------------------
    // Hook 1 (REST) + Hook 2 (MQTT): called when any DirectItem is finalized.
    // Hook 1 fires from LX/0gL;.parseFromJson (REST thread-history loads).
    // Hook 2 fires from LX/0gF;.A0P (MQTT/MSys real-time delivery).
    // Both pass the item as Object; reflection extracts v426 fields listed above.
    // -------------------------------------------------------------------------
    // Background thread for all Hook 1/2 work so the MQTT delivery thread is never blocked.
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

    // Dedup set: item_ids already queued. Checked on calling thread (cheap) to avoid
    // posting duplicate Runnables. Bounded to 2000 entries via eldest-entry eviction.
    private static final java.util.Map<String, Boolean> SEEN_ITEM_IDS =
        java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, Boolean>() {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> e) {
                return size() > 2000;
            }
        });

    // Notification dedup: a single live unsend can surface via BOTH Hook 2 (item re-delivery)
    // and Hook 4 (DB hide). Notify at most once per message_id (bounded, eldest-evicted).
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
     * Hook 2 (MQTT/MSys): the item's thread_key field is null on this path, so the patch passes the
     * thread id read from the MSys delta (A0P's p2) at patch time as {@code threadIdHint}.
     */
    public static void onMessageReceived(final Object item, final String threadIdHint) {
        // Called from the MQTT thread — must return instantly. All reflection/DB work
        // is posted to sWorker (background HandlerThread).
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

            if ("action_log".equals(type) || "expired_placeholder".equals(type)) return;

            if ((content == null || content.isEmpty()) && type != null && !type.equals("text")) {
                String url = di.getMediaUrl();
                if (url == null) url = deepFindMediaUrl(item);
                if (url != null) content = url;
            }

            if (messageId == null) {
                // MQTT subclass (X.0gF) may not resolve item_id — derive a stable synthetic key
                // from sender + timestamp so a later unsend maps back to the same row.
                if (senderId != null) {
                    messageId = "syn:" + senderId + ":" + timestamp;
                } else {
                    return;
                }
            }

            // MQTT path: thread_id comes from MSys delta passed as threadIdHint
            if ((threadId == null || threadId.isEmpty())
                    && threadIdHint != null && !threadIdHint.isEmpty()) {
                threadId = threadIdHint;
            }
            if (threadId == null) threadId = "";

            if (deleted) {
                // only notify when we previously captured this message alive
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

            // backfill 1:1 sender name into directory (sole-sender guard prevents group misattribution)
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

    public static void onMessageHiddenFromDb(Object dao, String serverId, String clientId) {
        try {
            if (!Pref.saveDeletedMessages()) return;

            String itemId = (serverId != null && !serverId.isEmpty()) ? serverId : clientId;
            if (itemId == null) return;

            PikoMessageDb vault = PikoMessageDb.getInstance(PikoUtils.getContext());
            boolean wasReceived = vault.isStoredAlive(itemId);
            String messageType = vault.getMessageType(itemId);
            boolean own = isOwnSender(vault.getSenderId(itemId));

            vault.insertOrIgnore(itemId, "", null, null, null, messageType, System.currentTimeMillis());
            vault.markDeleted(itemId);

            if (wasReceived && !own && claimNotification(itemId)) {
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


    /** BFS over the controller graph to find a DirectThreadKey and read the thread id string. */
    private static String deepFindThreadId(Object root) {
        try {
            java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            q.add(root); seen.put(root, Boolean.TRUE);
            int budget = 6000;
            String digitCandidate = null;
            while (!q.isEmpty() && budget-- > 0) {
                Object o = q.poll();
                Class<?> k = o.getClass();
                if (k.isArray() && !k.getComponentType().isPrimitive()) {
                    for (Object el : (Object[]) o) if (el != null && seen.put(el, Boolean.TRUE) == null) q.add(el);
                    continue;
                }
                String kn = k.getName();
                if (!(kn.startsWith("X.") || kn.startsWith("com.instagram"))) continue;
                if (kn.equals("com.instagram.model.direct.DirectThreadKey")) {
                    for (Class<?> cc = k; cc != null && cc != Object.class; cc = cc.getSuperclass()) {
                        for (Field f : cc.getDeclaredFields()) {
                            if (f.getType() != String.class) continue;
                            try {
                                f.setAccessible(true);
                                Object fv = f.get(o);
                                if (fv instanceof String) {
                                    String s = (String) fv;
                                    if (s.length() >= 15 && s.matches("\\d+")) return s;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                    continue;
                }
                for (Class<?> cc = k; cc != null && cc != Object.class; cc = cc.getSuperclass()) {
                    for (Field f : cc.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        if (f.getType().isPrimitive()) continue;
                        Object v;
                        try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                        if (v == null || seen.put(v, Boolean.TRUE) != null) continue;
                        if (v instanceof String) {
                            String s = (String) v;
                            if (s.length() >= 38 && s.matches("\\d+")
                                    && (digitCandidate == null || s.length() > digitCandidate.length())) {
                                digitCandidate = s;
                            }
                            continue;
                        }
                        if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) continue;
                        q.add(v);
                    }
                }
            }
            return digitCandidate;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Bounded BFS over the item graph for a media URL (image/video/audio CDN link). Prefers the
     *  longest match (usually the highest-resolution variant). Returns null if none found. */
    private static String deepFindMediaUrl(Object root) {
        try {
            java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            q.add(root); seen.put(root, Boolean.TRUE);
            String best = null;
            int budget = 6000;
            while (!q.isEmpty() && budget-- > 0) {
                Object o = q.poll();
                Class<?> k = o.getClass();
                if (k.isArray() && !k.getComponentType().isPrimitive()) {
                    for (Object el : (Object[]) o) if (el != null && seen.put(el, Boolean.TRUE) == null) q.add(el);
                    continue;
                }
                String kn = k.getName();
                if (!(kn.startsWith("X.") || kn.startsWith("com.instagram"))) continue;
                for (Class<?> cc = k; cc != null && cc != Object.class; cc = cc.getSuperclass()) {
                    for (Field f : cc.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        if (f.getType().isPrimitive()) continue;
                        Object v;
                        try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                        if (v == null || seen.put(v, Boolean.TRUE) != null) continue;
                        if (v instanceof String) {
                            String s = (String) v;
                            boolean looksMedia = s.startsWith("http") && (s.contains("cdninstagram") || s.contains("fbcdn")
                                    || s.matches("(?i).*\\.(jpg|jpeg|webp|heic|mp4|mov|m4a|aac|gif).*"));
                            // Exclude the sender's profile picture (IG profile-pic CDN namespace /
                            // small square thumbnails) so we capture the actual message media.
                            boolean profilePic = s.contains("t51.2885-19") || s.contains("/profile")
                                    || s.contains("s150x150") || s.contains("s320x320");
                            if (looksMedia && !profilePic && (best == null || s.length() > best.length())) best = s;
                            continue;
                        }
                        if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) continue;
                        q.add(v);
                    }
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }


}
