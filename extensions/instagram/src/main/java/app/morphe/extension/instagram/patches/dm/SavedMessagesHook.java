/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Field;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.entity.DirectItem;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;

/**
 * Runtime hooks for the "Save deleted messages" feature.
 *
 * <h2>Two delivery paths, both hooked</h2>
 * <ul>
 *   <li><b>REST/JSON</b> thread-history loads, and</li>
 *   <li><b>MQTT/MSys</b> real-time delivery (also the only path for an in-thread send+unsend).</li>
 * </ul>
 * Both hand the DirectItem to {@link #onMessageReceived(Object)}; all fields are read through
 * {@link DirectItem}, whose obfuscated names are resolved at patch time (see the
 * {@code directItemEntity} patch). Nothing discovers field/method names by reflection at runtime.
 *
 * <p>The open chat's thread id is recorded by {@link #noteOpenThreadId(String)}, injected from
 * the DM action-bar builder (which already holds the {@code DirectThreadKey}) — so scoping the
 * per-chat screen needs no runtime object-graph walking either.
 */
@SuppressWarnings("unused")
public class SavedMessagesHook {

    /** Silent logger — writes to logcat (tag "piko") only, never surfaces an on-screen toast. */
    private static void piko(String msg) {
        android.util.Log.e("piko", msg);
    }

    /** Most-recently-seen DM thread id; used by the action-bar button to scope the screen. */
    private static volatile String sCurrentThreadId;
    /** Title shown in the open chat's action bar = the participant's username (1:1 chats). */
    private static volatile String sCurrentThreadTitle;

    /** Called from DMActionBar with the chat title (the other user's name/username). The MQTT
     *  delivery path carries no username, so this on-screen title is our reliable source. */
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

    /** Records the open chat's thread id. Injected at patch time from the DM action-bar builder
     *  (which has the DirectThreadKey), so the open-chat scope is known without any runtime
     *  reflection. This is the single source of truth for sCurrentThreadId. */
    public static void noteOpenThreadId(String threadId) {
        if (threadId != null && !threadId.isEmpty()) sCurrentThreadId = threadId;
    }

    /**
     * Harvest participant id -> @handle pairs from a parsed DM thread's user list (injected from the
     * thread deserializer's "users" branch — see Hook 6). Each element is a
     * {@code com.instagram.user.model.User}; we read id + username through the patch-resolved
     * {@link UserData} accessors and store them via {@link PikoMessageDb#putUsername}, which backfills
     * stored rows and feeds the notification's {@code getSenderDisplay}. Runs as the inbox/threads
     * load, so a sender is named before any unsend arrives. Offloaded to the worker thread.
     */
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

    /** Most-recent DM action-bar controller (LX/2p9), held weakly so we don't leak the thread UI.
     *  Injected at the action-bar builder's entry — its object graph holds the open thread's id. */
    private static java.lang.ref.WeakReference<Object> sOpenThreadController;

    /** Records the open chat's action-bar controller. The thread id is extracted lazily (at button
     *  click) from its object graph, since a DirectThreadKey isn't materialised on the normal path.
     *  Also harvests participant usernames from the controller graph into the directory (off the UI
     *  thread) so received/unsent senders show a real @handle on the screen and in notifications. */
    public static void noteOpenThreadController(final Object controller) {
        if (controller == null) return;
        sOpenThreadController = new java.lang.ref.WeakReference<>(controller);
        getWorker().post(new Runnable() { @Override public void run() {
            try { deepHarvestUsers(controller); } catch (Throwable ignored) {}
        }});
    }

    /**
     * Bounded BFS over an object graph harvesting {@code sender_id -> username} pairs into the
     * directory. A user is recognised by duck-typing through {@link UserData} — whose getId /
     * getUsername method names are resolved at patch time (the Entity pattern), so this never
     * guesses obfuscated names at runtime. Non-user objects fail those reflective calls and are
     * skipped. This is how a sender gets a real @handle even though the MQTT message item carries
     * only the numeric id: the open chat's participant user objects supply it, and
     * {@link PikoMessageDb#putUsername} backfills every stored row + the notification path.
     */
    private static void deepHarvestUsers(Object root) {
        if (root == null) return;
        PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());
        java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
        java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
        q.add(root); seen.put(root, Boolean.TRUE);
        int budget = 8000;
        while (!q.isEmpty() && budget-- > 0) {
            Object o = q.poll();
            Class<?> k = o.getClass();
            if (k.isArray() && !k.getComponentType().isPrimitive()) {
                for (Object el : (Object[]) o) if (el != null && seen.put(el, Boolean.TRUE) == null) q.add(el);
                continue;
            }
            String kn = k.getName();
            if (!(kn.startsWith("X.") || kn.startsWith("com.instagram"))) continue;
            // Duck-type this object as an IG user via the patch-resolved UserData accessors.
            try {
                UserData ud = new UserData(o);
                String id = ud.getUserId();
                if (id != null && id.matches("\\d{6,14}")) {
                    String name = ud.getUsername();
                    if (name == null || name.isEmpty()) name = ud.getFullname();
                    if (name != null && !name.isEmpty()) db.putUsername(id, name);
                }
            } catch (Throwable ignored) {}
            for (Class<?> cc = k; cc != null && cc != Object.class; cc = cc.getSuperclass()) {
                for (Field f : cc.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getType().isPrimitive()) continue;
                    Object v;
                    try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                    if (v == null || seen.put(v, Boolean.TRUE) != null) continue;
                    if (v instanceof CharSequence || v instanceof Number || v instanceof Boolean) continue;
                    q.add(v);
                }
            }
        }
    }

    /** The open thread id, recovered from the most-recent action-bar controller's object graph. */
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
            // All field names are resolved at patch time and baked into DirectItem (see the
            // directItemEntity patch) — nothing is fingerprinted by name at runtime here.
            DirectItem di = new DirectItem(item);
            String senderId = di.getUserId();
            // Learn our own user id from messages flagged sent-by-viewer (reliable on inbox/thread
            // loads), then skip our own messages — by flag OR by sender id (the flag isn't set yet
            // on a just-sent message, but the sender id still matches). So unsending your own
            // message never captures or notifies.
            if (di.isSentByViewer()) {
                rememberMyUserId(senderId);
                return;
            }
            if (isOwnSender(senderId)) return;
            String messageId  = di.getItemId();
            // Deletion state participates in the dedup key: an item first seen alive and later
            // re-delivered as unsent (live in-thread unsend) is a DIFFERENT key, so it is NOT
            // dropped — we still need to mark it deleted and (conditionally) notify.
            boolean deleted = di.isHideInThread();
            // Dedup: A0P is called for every historical inbox item on each sync.
            if (messageId != null
                    && SEEN_ITEM_IDS.put(messageId + (deleted ? ":1" : ":0"), Boolean.TRUE) != null) return;
            String threadId   = di.getThreadId();
            PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());
            // Display name = the chat title recorded for this thread (the participant's name for a
            // 1:1 chat), so it's stored on the row and shown by both the screen and the unsend
            // notification. Falls back to a name already on the thread, else null (→ sender id).
            // Per-sender username — why this is an id->name lookup, not a patch-time item field:
            //   RE of v426 (piko-re/re-notes/save-deleted-messages-v426.md) shows the DirectItem
            //   dispatch parses only the numeric sender `user_id` (A1M); MQTT items (LX/0gF) carry
            //   NO UserInfo/profile object at all — so there is no reliable per-item profile field
            //   to read for the live/unsend path. A real @handle therefore comes from the
            //   sender_id -> username directory (PikoMessageDb): populated from confident 1:1 chats
            //   below, consulted first in the resolution chain above, and backfilled across every
            //   stored row + notification by PikoMessageDb.putUsername. See PR #1387.
            // Display name resolution, most-specific first:
            //   1) the sender_id -> @handle directory (learned from 1:1 chats, see below) — this is
            //      the only source that names a sender correctly in GROUP chats and in threads that
            //      aren't currently open;
            //   2) any username already recorded on this thread (1:1 chat title);
            //   3) the open chat's action-bar title, when this message is in the open thread.
            String senderUser = db.getUsername(senderId);
            if (senderUser == null) senderUser = db.getThreadUsername(threadId);
            if (senderUser == null) senderUser = openChatTitleFor(db, threadId);
            // content (base text + MQTT subclass payload), item_type enum, and timestamp are all
            // read via DirectItem with patch-resolved field names.
            String content    = di.getText();
            String type       = di.getItemType();
            if (type != null) type = type.trim().toLowerCase();
            long   timestamp  = di.getTimestampMs();

            // Activity logs / expired disappearing-media placeholders aren't real messages — never
            // capture or notify for them (avoids bogus "[action_log] deleted" notifications).
            if ("action_log".equals(type) || "expired_placeholder".equals(type)) return;

            // Media messages (photo/video/voice/etc.) carry no text — capture the media URL so the
            // deleted-messages screen can still display/open it after the sender unsends.
            if ((content == null || content.isEmpty()) && type != null && !type.equals("text")) {
                String url = deepFindMediaUrl(item);
                if (url != null) content = url;
            }

            if (messageId == null) {
                // The legacy obfuscated id fields (A13/A0l) do NOT resolve on the MQTT
                // subclass (X.0gF) — there A13 is a boolean, not item_id — so modern
                // (E2EE) DMs would silently fall through here and never be stored,
                // leaving the deleted-messages list permanently empty.
                //
                // Instead of dropping the message, derive a stable synthetic key from
                // sender + timestamp, so a later unsend maps back to the same row.
                // This guarantees every received item is captured and, critically, that a
                // later unsend of the same item maps back to the same row to mark deleted.
                if (senderId != null) {
                    messageId = "syn:" + senderId + ":" + timestamp;
                } else {
                    // No id and no sender — nothing we can key on reliably; skip.
                    return;
                }
            }

            // thread_id is NOT NULL in the schema; fall back to empty when unknown. The open-chat
            // scope (sCurrentThreadId) is set authoritatively by noteOpenThreadId from the
            // action-bar builder — we intentionally do NOT set it from the message stream, since
            // an inbox load touches every thread and would pollute the scope.
            // On v426/MSys the item's direct thread_key field is null on the MQTT path, so
            // di.getThreadId() returns null. The thread id then comes from the MSys delta (A0P's
            // p2), read at patch time and passed in as threadIdHint — keeping capture patch-time.
            if ((threadId == null || threadId.isEmpty())
                    && threadIdHint != null && !threadIdHint.isEmpty()) {
                threadId = threadIdHint;
            }
            if (threadId == null) threadId = "";

            // Deletion (unsend) detection.
            // hideInThread field on the domain DirectItem object is ProGuard-obfuscated:
            //   v426 (LX/9ZA): A1Y:Z  ← confirmed by static RE of v426 smali
            //   v408 (LX/5jI): A2V:Z  ← confirmed from reference APK analysis
            // Try obfuscated names first (fast path), then fall back to the stable
            // protobuf field name "hideInThread_" in case a future build de-obfuscates.
            if (deleted) {
                // Live-deletion gate: only notify when we previously saw THIS message alive.
                // A historical unsent item arriving already-hidden during an inbox sync is
                // stored (so the screen still lists it) but must NOT raise a notification.
                boolean liveDeletion = db.isStoredAlive(messageId);
                // Capture content first (so the row exists), then mark + notify.
                db.insertOrIgnore(messageId, threadId, senderId, senderUser, content, type, timestamp);
                db.markDeleted(messageId);
                // liveDeletion already implies the message was received (stored alive), so our own
                // outgoing unsends never reach here.
                if (liveDeletion && claimNotification(messageId)) {
                    String notifySender = (senderUser != null) ? senderUser
                            : db.getThreadUsername(threadId);
                    if (notifySender == null) notifySender = openChatTitleFor(db, threadId);
                    if (notifySender == null) notifySender = db.getSenderDisplay(messageId);
                    String notifyBody   = (content != null && !content.isEmpty())
                            ? content : db.getStoredContent(messageId);
                    notifyDeletion(notifySender, notifyBody, type);
                }

                // Anti-revoke in-place: undo the deletion flag on the item object so IG
                // keeps the message visible in the thread with its original text.
                // content is null on the unsend event (text is stripped before delivery),
                // so we look up the previously-stored text from the DB vault.
                String storedContent = (content != null && !content.isEmpty())
                        ? content : db.getStoredContent(messageId);
                antiRevokeItem(di, storedContent);
            } else {
                db.insertOrIgnore(messageId, threadId, senderId, senderUser, content, type, timestamp);
            }

            // Learn this sender's real handle from a 1:1 chat. The MQTT/REST item only carries the
            // numeric sender_id, never a username — but if the OPEN thread has only this one stored
            // sender (i.e. it's a 1:1, not a group), the action-bar title IS that sender's name.
            // Persist sender_id -> name into the directory so the SAME sender shows a real @handle
            // everywhere afterwards: other threads, group chats, the all-messages screen, and
            // notifications. The sole-sender guard is what prevents mapping a group's title (which
            // names no single person) onto an arbitrary sender. (Runs after the row above is stored
            // so the current message is counted by getSoleSenderId.)
            if (sCurrentThreadTitle != null && threadId.equals(sCurrentThreadId)
                    && senderId != null && senderId.equals(db.getSoleSenderId(threadId))) {
                db.putUsername(senderId, sCurrentThreadTitle);
            }
        } catch (Exception e) {
            piko("SavedMessagesHook.processReceivedItem: " + e);
        }
    }

    /**
     * Anti-revoke in-place: reset the hide_in_thread flag and restore text so IG's thread
     * UI renders the message normally instead of hiding it. The item object is mutated
     * in place — the caller's return-object smali instruction sees the modified state.
     *
     * Two text paths must be restored on v426:
     *   REST path (LX/9ZA; base class): text at A1I:String
     *   MQTT path (LX/0gF; subclass):   text at A0o:Object (holds the String directly)
     * Both must be set; if only A1I is set, MQTT-delivered items still appear unsent
     * because Instagram reads from A0o when the runtime type is the MQTT subclass.
     */
    private static void antiRevokeItem(DirectItem di, String restoredContent) {
        // Clear hide_in_thread and restore the text (both base + MQTT subclass fields) using the
        // patch-resolved field names in DirectItem.
        di.setHideInThread(false);
        if (restoredContent != null && !restoredContent.isEmpty()) {
            di.setText(restoredContent);
        }
    }

    /** Post a system notification when a received message is detected as unsent. */
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
                    channelId, "Deleted messages", android.app.NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Notifies when a received message is unsent");
                nm.createNotificationChannel(ch);
            }

            String who = (sender != null && !sender.isEmpty()) ? sender : "Someone";
            String body = (content != null && !content.isEmpty())
                    ? content
                    : (type != null && !type.isEmpty()) ? "[" + type + "]" : "[deleted]";

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
                .setContentTitle(who + " deleted a message")
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

            nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
        } catch (Exception e) {
            piko("SavedMessagesHook.notifyDeletion: " + e);
        }
    }

    // -------------------------------------------------------------------------
    // Hook 2: called when MQTT "item_removed" event arrives.
    // p1 = threadId, p2 = itemId (Instagram's canonical order in the unsend handler).
    // -------------------------------------------------------------------------
    public static void onMessageDeleted(String threadId, String itemId) {
        try {
            if (!Pref.saveDeletedMessages()) return;
            if (itemId == null) return;

            PikoMessageDb.getInstance(PikoUtils.getContext()).markDeleted(itemId);

        } catch (Exception e) {
            piko("SavedMessagesHook.onMessageDeleted: " + e);
        }
    }

    // -------------------------------------------------------------------------
    // Hook 4: fires at entry of the SQLite DAO method that hides (unsends) a DirectItem.
    // We get p2 (server_item_id) / p3 (client_item_id). Hook 1/2 already captured the message
    // into our own vault as it arrived, so here we just mark it deleted and notify — no need to
    // read Instagram's own DB (which is empty under MSYS on v426 anyway).
    // -------------------------------------------------------------------------
    public static void onMessageHiddenFromDb(Object dao, String serverId, String clientId) {
        try {
            if (!Pref.saveDeletedMessages()) return;

            String itemId = (serverId != null && !serverId.isEmpty()) ? serverId : clientId;
            if (itemId == null) return;

            PikoMessageDb vault = PikoMessageDb.getInstance(PikoUtils.getContext());
            // Was this message previously RECEIVED (captured alive by Hook 1/2)? If not, this is
            // almost certainly OUR OWN outgoing message being unsent — record it but don't notify.
            boolean wasReceived = vault.isStoredAlive(itemId);
            String messageType = vault.getMessageType(itemId);
            // Never notify for our own unsends (covers stale own rows from older builds too).
            boolean own = isOwnSender(vault.getSenderId(itemId));

            // Ensure a row exists (skeleton if Hook 1/2 somehow missed it) and mark it deleted.
            vault.insertOrIgnore(itemId, "", null, null, null, messageType, System.currentTimeMillis());
            vault.markDeleted(itemId);

            // Notify only for messages we actually received (skips our own unsends + uncaptured).
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
        if (type == null) return "[deleted]";
        switch (type) {
            case "media":
            case "image":           return "[photo deleted]";
            case "raven_media":     return "[disappearing photo deleted]";
            case "video":           return "[video deleted]";
            case "voice_media":
            case "audio":           return "[voice message deleted]";
            case "animated_media":  return "[GIF deleted]";
            case "reel_share":      return "[reel deleted]";
            case "story_share":     return "[story reply deleted]";
            case "media_share":     return "[post share deleted]";
            case "like":            return "[like deleted]";
            case "link":            return "[link deleted]";
            case "action_log":      return "[activity deleted]";
            default:                return "[" + type + " deleted]";
        }
    }


    /**
     * Bounded BFS over the item graph for the thread id. On v426/MSys the DirectItem's direct
     * thread_key field is null on the MQTT path, but a {@code DirectThreadKey} (a stable,
     * non-obfuscated class) is usually still reachable elsewhere in the item's object graph.
     * We locate that instance and read its thread-id String (a long numeric, the only digit-only
     * String field on the key). Returns null if none found.
     */
    private static String deepFindThreadId(Object root) {
        try {
            java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            q.add(root); seen.put(root, Boolean.TRUE);
            int budget = 6000;
            // Fallback when no DirectThreadKey object is in the graph (e.g. the action-bar
            // controller, where the key is built on-demand): a v426 thread id is a ~39-digit
            // decimal, distinctly longer than message ids (~35) and user ids (~11). Track the
            // longest all-digit String of length >= 38 as a candidate.
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
                // Found a DirectThreadKey: read its digit-only String field (the thread id).
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
