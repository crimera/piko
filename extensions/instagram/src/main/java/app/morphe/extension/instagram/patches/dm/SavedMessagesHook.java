/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.entity.DirectItem;
import app.morphe.extension.instagram.utils.Pref;

/**
 * Runtime hooks for the "Save deleted messages" feature.
 *
 * <h2>Two delivery paths, both hooked</h2>
 *
 * <ul>
 *   <li><b>REST / JSON</b> (thread history load): hooked before the parser returns the
 *       fully-populated DirectItem.</li>
 *   <li><b>MQTT / MSys</b> real-time delivery: hooked at the post-processing step that runs for
 *       every incoming DirectItem. Real-time messages never pass through the JSON parser, so
 *       without this path an in-thread send+unsend would be missed entirely.</li>
 * </ul>
 *
 * Both paths hand the DirectItem to {@link #onMessageReceived(Object)} as an {@code Object}; all
 * field access goes through {@link DirectItem}, whose obfuscated field names were resolved at
 * patch time (see the {@code directItemEntity} patch). Nothing here discovers field or method
 * names by reflection at runtime — {@link DirectItem} also defeats the MQTT subclass's field
 * shadowing by reading every field off the resolved base class.
 *
 * <p>The fingerprints that locate the two injection points live in
 * {@code SaveDeletedMessagesPatch} / its {@code Fingerprint.kt}.
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

    /** Called when a chat is opened (from the action bar) with the resolved thread_id + title.
     *  Records both so unsend notifications in this chat can show the sender's name, even if the
     *  user never opens the deleted-messages screen. */
    public static void noteThreadOpen(String threadId, String title) {
        if (threadId != null && !threadId.isEmpty()) sCurrentThreadId = threadId;
        if (title != null && !title.trim().isEmpty()) {
            sCurrentThreadTitle = title.trim();
            try {
                String tid = (threadId != null && !threadId.isEmpty()) ? threadId : sCurrentThreadId;
                if (tid != null && !tid.isEmpty()) {
                    PikoMessageDb.getInstance(PikoUtils.getContext()).setThreadUsername(tid, sCurrentThreadTitle);
                }
            } catch (Exception ignored) {}
        }
    }

    /** Opens the deleted-messages screen scoped to a specific thread (resolved at click time). */
    public static void openDeletedMessagesFor(Context ctx, String threadId, String title) {
        try {
            if (ctx == null) ctx = PikoUtils.getContext();
            if (ctx == null) return;
            if (threadId != null && !threadId.isEmpty()) sCurrentThreadId = threadId;
            if (title != null && !title.trim().isEmpty()) {
                sCurrentThreadTitle = title.trim();
                if (threadId != null && !threadId.isEmpty()) {
                    PikoMessageDb.getInstance(ctx).setThreadUsername(threadId, sCurrentThreadTitle);
                }
            }
            Intent intent = new Intent(ctx, DeletedMessagesActivity.class);
            if (sCurrentThreadId != null && !sCurrentThreadId.isEmpty()) intent.putExtra("thread_id", sCurrentThreadId);
            if (sCurrentThreadTitle != null && !sCurrentThreadTitle.isEmpty()) intent.putExtra("thread_title", sCurrentThreadTitle);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            piko("SavedMessagesHook.openDeletedMessagesFor: " + e);
        }
    }

    /** Opens the deleted-messages screen for the currently-open thread (or all if unknown). */
    public static void openDeletedMessages(Context ctx) {
        try {
            if (ctx == null) ctx = PikoUtils.getContext();
            if (ctx == null) return;
            Intent intent = new Intent(ctx, DeletedMessagesActivity.class);
            if (sCurrentThreadId != null && !sCurrentThreadId.isEmpty()) {
                intent.putExtra("thread_id", sCurrentThreadId);
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

    public static void onMessageReceived(final Object item) {
        // Called from the MQTT thread — must return instantly. All reflection/DB work
        // is posted to sWorker (background HandlerThread).
        if (item == null) return;
        if (!Pref.saveDeletedMessages()) return;
        // Class guard: only X.* (obfuscated IG classes) are DirectItem candidates.
        if (!item.getClass().getName().startsWith("X.")) return;

        getWorker().post(new Runnable() { @Override public void run() {
            processReceivedItem(item);
        }});
    }

    private static void processReceivedItem(Object item) {
        try {
            // All field names below were resolved at patch time and baked into DirectItem — no
            // obfuscated name is discovered here. DirectItem reads every field off the resolved
            // base class, so the REST base type and the MQTT subclass (which shadows some base
            // fields with different types) both work without per-type special-casing.
            DirectItem di = new DirectItem(item);

            String messageId = di.getItemId();
            // Deletion state participates in the dedup key: an item first seen alive and later
            // re-delivered as unsent (live in-thread unsend) is a DIFFERENT key, so it is NOT
            // dropped — we still need to mark it deleted and (conditionally) notify.
            boolean deleted = di.isHideInThread();
            // Dedup: the post-process hook is called for every historical inbox item on each sync.
            if (messageId != null
                    && SEEN_ITEM_IDS.put(messageId + (deleted ? ":1" : ":0"), Boolean.TRUE) != null) return;

            String threadId  = di.getThreadId();
            String senderId  = di.getUserId();
            String content   = di.getText();
            String type      = di.getItemType();
            long   timestamp = di.getTimestampMs();
            // Enum constant names mirror the server tokens ("TEXT", "MEDIA", …); normalise to the
            // lowercase form describeMediaType / the screen expect.
            if (type != null) type = type.trim().toLowerCase();

            PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());

            // Resolve a display name for the sender: a previously-persisted handle first, then
            // Instagram's own local user cache (queried by column, not by reflected field name).
            String senderUser = (senderId != null) ? db.getUsername(senderId) : null;
            if (senderUser == null && senderId != null) {
                senderUser = resolveUsernameFromCache(senderId);
                if (senderUser != null) db.putUsername(senderId, senderUser);
            }

            if (messageId == null) {
                // Some modern (E2EE) DMs do not expose a server item_id on the received object.
                // Derive a stable synthetic key from sender + timestamp so a later unsend of the
                // same item maps back to the same row to mark it deleted.
                if (senderId != null) {
                    messageId = "syn:" + senderId + ":" + timestamp;
                } else {
                    // No id and no sender — nothing we can key on reliably; skip.
                    return;
                }
            }

            // thread_id is NOT NULL in the schema; fall back to empty when unknown.
            if (threadId == null) threadId = "";
            // Track the most-recently-seen thread so the DM action-bar button can open this
            // chat's deleted messages. Opening a thread triggers a burst of post-process calls
            // for its items, so the last non-empty thread id is a good proxy for "the open chat".
            if (!threadId.isEmpty() && !threadId.equals(sCurrentThreadId)) {
                sCurrentThreadId = threadId;
            }

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
                    if (notifySender == null) notifySender = db.getSenderDisplay(messageId);
                    String notifyBody   = (content != null && !content.isEmpty())
                            ? content : db.getStoredContent(messageId);
                    notifyDeletion(notifySender, notifyBody, type);
                }

                // Anti-revoke in-place: undo the deletion flag on the item object so IG keeps the
                // message visible with its original text. content is null on the unsend event
                // (text is stripped before delivery), so look up the stored text from the vault.
                String storedContent = (content != null && !content.isEmpty())
                        ? content : db.getStoredContent(messageId);
                antiRevokeItem(di, storedContent);
            } else {
                db.insertOrIgnore(messageId, threadId, senderId, senderUser, content, type, timestamp);
            }
        } catch (Exception e) {
            piko("SavedMessagesHook.processReceivedItem: " + e);
        }
    }

    /**
     * Anti-revoke in-place: clear the hide_in_thread flag and restore the text so IG renders the
     * message normally instead of hiding it. The item is mutated in place — the caller's
     * return-object instruction sees the modified state. Both the flag and the text field are
     * patch-resolved base-class fields (see {@link DirectItem}).
     */
    private static void antiRevokeItem(DirectItem di, String restoredContent) {
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
                    channelId, str("piko_deleted_messages_channel"),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription(str("piko_deleted_messages_channel_desc"));
                nm.createNotificationChannel(ch);
            }

            String who = (sender != null && !sender.isEmpty()) ? sender : str("piko_someone");
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
                .setContentTitle(str("piko_deleted_a_message", who))
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
    // Hook 4: fires at entry of the SQLite DAO method that hides a DirectItem.
    //
    // We receive p0 (the DAO), p2 (server_item_id), p3 (client_item_id).
    // The hook fires BEFORE the DELETE, so Instagram's "messages" table still
    // has the row with text, thread_id, timestamp, message_type, etc.
    //
    // Instagram DB access (smali analysis of LX/0HR;, the direct-msg DB helper):
    //   LX/0HR; extends SQLiteOpenHelper; holds A00:SQLiteDatabase (instance field).
    //   LX/0HR;.A06 = static connection-manager (LX/0HS;) → .A00() → LX/0HR; instance.
    //   Table: "messages"; columns: server_item_id, client_item_id, text,
    //                               thread_id, user_id, timestamp, message_type.
    // -------------------------------------------------------------------------
    public static void onMessageHiddenFromDb(Object dao, String serverId, String clientId) {
        try {
            if (!Pref.saveDeletedMessages()) return;

            String itemId = (serverId != null && !serverId.isEmpty()) ? serverId : clientId;
            if (itemId == null) return;

            PikoMessageDb vault = PikoMessageDb.getInstance(PikoUtils.getContext());
            // Was this message previously RECEIVED (captured alive by Hook 1/2)? If not, this is
            // almost certainly OUR OWN outgoing message being unsent — we still record it but must
            // NOT raise a notification for it.
            boolean wasReceived = vault.isStoredAlive(itemId);

            String content     = null;
            String threadId    = "";
            String senderId    = null;
            // Prefer the message_type captured at receive time (Instagram's own "messages" table is
            // empty under MSYS on v426, so a DB read here usually can't tell text from media).
            String storedType  = vault.getMessageType(itemId);
            String messageType = (storedType != null) ? storedType : "text";
            long   timestamp   = System.currentTimeMillis();

            // --- Read from Instagram's own "messages" table before the DELETE fires ---
            SQLiteDatabase igDb = getInstagramDb(dao);
            boolean openedFresh = false; // true if WE opened the handle (must close after)
            if (igDb == null) {
                igDb = openInstagramDbFile(PikoUtils.getContext());
                openedFresh = (igDb != null);
            }
            if (igDb != null) {
                try {
                    String   where;
                    String[] args;
                    if (serverId != null && !serverId.isEmpty() && clientId != null && !clientId.isEmpty()) {
                        where = "server_item_id = ? OR client_item_id = ?";
                        args  = new String[]{serverId, clientId};
                    } else if (serverId != null && !serverId.isEmpty()) {
                        where = "server_item_id = ?";
                        args  = new String[]{serverId};
                    } else {
                        where = "client_item_id = ?";
                        args  = new String[]{clientId};
                    }
                    Cursor c = igDb.query(
                        "messages",
                        new String[]{"text", "thread_id", "user_id", "timestamp", "message_type"},
                        where, args, null, null, null, "1");
                    if (c != null) {
                        if (c.moveToFirst()) {
                            content     = c.getString(0);
                            String tId  = c.getString(1);
                            if (tId  != null && !tId.isEmpty())  threadId    = tId;
                            senderId    = c.getString(2);
                            String ts   = c.getString(3);
                            if (ts   != null && !ts.isEmpty()) {
                                try { timestamp = Long.parseLong(ts) / 1000L; } catch (Exception ignored) {}
                            }
                            String mt   = c.getString(4);
                            if (mt   != null) messageType = mt;
                        }
                        c.close();
                    }
                } finally {
                    if (openedFresh) igDb.close();
                }
            }

            if (content != null && !content.isEmpty()) {
                vault.insertOrIgnore(itemId, threadId, senderId, null, content, messageType, timestamp);
            } else {
                // Media / unsupported type — fall back to what Hook 1/2 may have stored
                // (text, or a media URL captured at receive time).
                String stored = vault.getStoredContent(itemId);
                if (stored != null) content = stored;
                // Still insert a skeleton row so markDeleted has a row to update.
                vault.insertOrIgnore(itemId, threadId, senderId, null,
                        describeMediaType(messageType), messageType, timestamp);
            }
            vault.markDeleted(itemId);

            String pikoContent = vault.getStoredContent(itemId);

            // Notify only for messages we actually received (skips our own unsends + uncaptured).
            if (wasReceived && claimNotification(itemId)) {
                // For media the stored content is a URL/label — show a friendly type label, not the URL.
                boolean isMedia = pikoContent == null || pikoContent.isEmpty()
                        || pikoContent.startsWith("http") || pikoContent.startsWith("[");
                String notifBody = isMedia ? describeMediaType(messageType) : pikoContent;
                // Name = the chat title (display name) recorded for this thread; fall back to
                // any sender display we have. Matches the "<name> deleted a message" UX.
                String storedThreadId = vault.getThreadIdOf(itemId);
                String name = vault.getThreadUsername(storedThreadId);
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
     * Returns Instagram's live SQLiteDatabase by scanning the DB helper's static singleton.
     *
     * Access chain (confirmed from smali of Instagram's direct-message DB helper, LX/0HR;):
     *   - LX/0HR; extends SQLiteOpenHelper and holds A00:SQLiteDatabase (instance field)
     *   - LX/0HR;.A06 is a static field holding the connection-manager (LX/0HS;)
     *   - LX/0HS;.A00() returns the LX/0HR; open-helper instance
     *
     * We load the class by binary name ("X.0HR") and scan its fields by TYPE rather than
     * by hardcoded names so this survives minor ProGuard rename variations.
     */
    private static SQLiteDatabase getInstagramDb(Object dao) {
        try {
            ClassLoader cl = dao.getClass().getClassLoader();
            Class<?> helperClass = null;
            try { helperClass = cl.loadClass("X.0HR"); } catch (Exception ignored) {}

            // If name-load failed, walk DAO superclass static fields for the same pattern.
            if (helperClass == null) {
                SQLiteDatabase db = scanForDb(dao.getClass());
                if (db != null) return db;
                return null;
            }

            return scanForDb(helperClass);
        } catch (Exception e) {
            piko("SavedMessagesHook.getInstagramDb: " + e);
            return null;
        }
    }

    /** Scans static fields of cls for a singleton that, via a no-arg method, yields
     *  an object holding a SQLiteDatabase instance field. */
    private static SQLiteDatabase scanForDb(Class<?> cls) {
        for (Field sf : cls.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(sf.getModifiers())) continue;
            if (sf.getType().isPrimitive()) continue;
            sf.setAccessible(true);
            Object mgr;
            try { mgr = sf.get(null); } catch (Exception e) { continue; }
            if (mgr == null) continue;
            for (Method m : mgr.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() == Void.TYPE) continue;
                m.setAccessible(true);
                Object helper;
                try { helper = m.invoke(mgr); } catch (Exception e) { continue; }
                if (helper == null) continue;
                for (Field df : helper.getClass().getDeclaredFields()) {
                    if (!SQLiteDatabase.class.isAssignableFrom(df.getType())) continue;
                    df.setAccessible(true);
                    Object db;
                    try { db = df.get(helper); } catch (Exception e) { continue; }
                    if (db instanceof SQLiteDatabase && ((SQLiteDatabase) db).isOpen())
                        return (SQLiteDatabase) db;
                }
            }
        }
        return null;
    }

    /**
     * Fallback: open a read-only handle to Instagram's direct-message DB file.
     * Scans the app's databases dir and verifies the "messages" table exists.
     * WAL mode (enabled by Instagram) allows concurrent readers — safe to open.
     * Caller must close() the returned database.
     */
    private static SQLiteDatabase openInstagramDbFile(Context ctx) {
        if (ctx == null) return null;
        File dbDir = ctx.getDatabasePath("x").getParentFile();
        if (dbDir == null || !dbDir.exists()) return null;
        java.util.List<File> candidates = new java.util.ArrayList<>();
        for (String n : new String[]{"direct.db", "direct_side_panel.db", "direct_bootstrap.db"}) {
            File f = new File(dbDir, n); if (f.exists()) candidates.add(f);
        }
        File[] all = dbDir.listFiles();
        if (all != null) {
            for (File f : all) {
                if (f.getName().endsWith(".db") && !candidates.contains(f)) candidates.add(f);
            }
        }
        for (File f : candidates) {
            try {
                SQLiteDatabase db = SQLiteDatabase.openDatabase(
                        f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor chk = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='messages'", null);
                boolean ok = chk.moveToFirst(); chk.close();
                if (ok) return db;
                db.close();
            } catch (Exception ignored) {}
        }
        piko("SavedMessagesHook: Instagram DM database not found in " + dbDir);
        return null;
    }

    /**
     * Tries to resolve a real username for senderId by querying Instagram's local user
     * cache databases. IG stores DM participant profiles in SQLite (user.db or similar).
     * Returns null if the DB isn't found or the user isn't cached.
     */
    private static String resolveUsernameFromCache(String senderId) {
        try {
            Context ctx = PikoUtils.getContext();
            if (ctx == null) return null;
            File dbDir = ctx.getDatabasePath("x").getParentFile();
            if (dbDir == null) return null;
            String[] candidates = {"user.db", "users.db", "igdb.db", "instagram.db",
                                   "user_cache.db", "profile.db", "direct_bootstrap.db"};
            for (String name : candidates) {
                File f = new File(dbDir, name);
                if (!f.exists()) continue;
                try {
                    SQLiteDatabase db = SQLiteDatabase.openDatabase(
                            f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                    for (String[] spec : new String[][]{
                            {"users", "username", "pk"},
                            {"users", "username", "id"},
                            {"user", "username", "pk"},
                            {"user", "username", "user_id"},
                            {"participants", "username", "pk"},
                    }) {
                        try {
                            Cursor c = db.query(spec[0], new String[]{spec[1]},
                                    spec[2] + " = ?", new String[]{senderId},
                                    null, null, null, "1");
                            if (c != null) {
                                String uname = null;
                                if (c.moveToFirst()) uname = c.getString(0);
                                c.close();
                                if (uname != null && !uname.isEmpty()) { db.close(); return uname; }
                            }
                        } catch (Exception ignored) {}
                    }
                    db.close();
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            piko("resolveUsernameFromCache: " + e);
        }
        return null;
    }

}
