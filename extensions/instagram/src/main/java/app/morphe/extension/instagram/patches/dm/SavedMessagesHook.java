/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

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
import app.morphe.extension.instagram.utils.Pref;

/**
 * Runtime hooks for the "Save deleted messages" feature.
 *
 * <h2>v426 Architecture — TWO delivery paths, both must be hooked</h2>
 *
 * <pre>
 * REST / JSON (thread history load)
 *   LX/0gL;.parseFromJson(LX/R0r;)LX/9ZA;    ← classes.dex
 *     └─ LX/AtQ;.parse → LX/0gG;.unsafeParseFromJson (creates LX/0gF; instance)
 *   Hook 1 is injected before RETURN_OBJECT in parseFromJson.
 *
 * MQTT / MSys real-time delivery
 *   LX/0gF;.A02(LX/1kP;, ..., LX/02L;, ...)  ← 24-param constructor, builds from delta
 *   LX/0gF;.A0P(UserSession, LX/02L;)LX/0gF;  ← classes12.dex, post-processing step
 *   Hook 2 is injected before the success RETURN_OBJECT in A0P (offset 0351/0352).
 * </pre>
 *
 * MQTT messages (including real-time send + unsend while in-thread) NEVER go through
 * parseFromJson. Without Hook 2, any message sent and unsent while the user is actively
 * in the thread would be missed entirely.
 *
 * <h2>Class hierarchy</h2>
 *
 * {@code LX/0gF;} (PUBLIC FINAL) extends {@code LX/9ZA;} (DirectItem base class).
 * {@code LX/0gF;} has NO additional instance fields — all data fields are declared on
 * {@code LX/9ZA;}. Every {@code getDeclaredField} call must walk the superclass chain
 * (see {@link #getFieldValue}) or it will silently fail when the runtime type is
 * {@code LX/0gF;} and the field is actually on {@code LX/9ZA;}.
 *
 * <h2>v426 field mapping (confirmed from dexdump classes12.dex)</h2>
 *
 * <pre>
 * JSON key        Obfuscated field   Type                   Class
 * item_id         A13                String                 LX/9ZA;
 * hide_in_thread  A1Y                Z (boolean)            LX/9ZA;
 * user_id         A1M                String                 LX/9ZA;
 * timestamp       A1J                String (microseconds)  LX/9ZA;
 * text            A1I                String                 LX/9ZA;
 * item_type       A0Y                LX/8ot; (enum)         LX/9ZA;
 * thread_key      A0W                DirectThreadKey        LX/9ZA;
 * threadId (key)  A00                String                 DirectThreadKey
 * MSys delta ref  A0V                LX/02L;                LX/9ZA;
 * </pre>
 *
 * v408 fallbacks: item_id via getter {@code A0l()}, hide_in_thread as {@code A2V:Z},
 * thread_key fields {@code A16/A18/A15}.
 *
 * <h2>How to update for a new Instagram version</h2>
 *
 * 1. Install the patched APK (pref on). Open any DM thread.
 * 2. In logcat (tag: piko), find "SavedMessagesHook ObjectBrowser dump" — this lists all
 *    fields on the runtime item, including inherited ones from superclasses.
 * 3. If field names differ from the table above, update the constants in
 *    {@link #onMessageReceived} and the table in Fingerprint.kt.
 * 4. If the hook doesn't fire at all, the fingerprint anchors may have changed:
 *    - Hook 1: grep classes.dex for methods with "item_id" + "hide_in_thread" + returnType Z
 *    - Hook 2: grep classes12.dex for "DirectMessage.postprocess" + "null type" string pair
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
            // v426 field names (confirmed from dexdump classes12.dex LX/0gL;.A00):
            //   item_id        → A13:String
            //   user_id        → A1M:String (sender ID)
            //   item_type      → A0Y:enum (toString() for value)
            //   timestamp      → A1J:String (microseconds)
            //   text           → A1I:String
            //   thread_key     → A0W:DirectThreadKey, .A00:String
            // v408 field names (fallback):
            //   item_id        → getter A0l(), thread_key → A16/A18
            String messageId  = reflectString(item, "item_id", "A13");
            if (messageId == null) messageId = reflectStringOrInvoke(item, "item_id", "A0l");
            // Deletion state participates in the dedup key: an item first seen alive and later
            // re-delivered as unsent (live in-thread unsend) is a DIFFERENT key, so it is NOT
            // dropped — we still need to mark it deleted and (conditionally) notify.
            boolean deleted = isHideInThread(item);
            // Dedup: A0P is called for every historical inbox item on each sync.
            if (messageId != null
                    && SEEN_ITEM_IDS.put(messageId + (deleted ? ":1" : ":0"), Boolean.TRUE) != null) return;
            String threadId   = reflectThreadIdFromItem(item);
            String senderId   = reflectString(item, "user_id", "A1M");
            PikoMessageDb db = PikoMessageDb.getInstance(PikoUtils.getContext());
            // 1. REST items may carry a full UserInfo — extract + persist the handle for reuse.
            String senderUser = resolveSenderUsername(item, senderId);
            if (senderUser != null) db.putUsername(senderId, senderUser);
            // 2. MQTT items carry only sender_id. Reuse a previously-persisted handle…
            if (senderUser == null && senderId != null) {
                senderUser = db.getUsername(senderId);
            }
            // 3. …or, as a last resort, probe IG's local user cache DBs.
            if (senderUser == null && senderId != null) {
                senderUser = resolveUsernameFromCache(senderId);
                if (senderUser != null) db.putUsername(senderId, senderUser);
            }
            String content    = null;
            String type       = null;
            long   timestamp  = System.currentTimeMillis();

            // item_type: v426 stores it as an enum in A0Y — but A0Y is SHADOWED on the MQTT
            // subclass (X.0gF.A0Y:Media is usually null), so it must be read from the base class
            // (X.9ZA, the same class that declares the String item_id A13), not via a plain
            // superclass-chain walk which returns the subclass's null field first.
            try {
                Object typeObj = null;
                Class<?> base = declaringClassOfStringField(item, "A13");
                if (base != null) typeObj = readFieldOnClass(base, item, "A0Y");
                if (typeObj == null) typeObj = reflectRaw(item, "item_type", "A0Y");
                if (typeObj != null) type = typeObj.toString();
            } catch (Exception ignored) {}
            if (type == null) type = reflectString(item, "item_type", "A0R");
            // Normalise the enum's toString() to a stable lowercase token when possible.
            if (type != null) type = type.trim();

            // timestamp: v426 stores as String microseconds (A1J), v408 as Long (A03)
            try {
                String tsStr = reflectString(item, "timestamp", "A1J");
                if (tsStr != null && !tsStr.isEmpty()) {
                    timestamp = Long.parseLong(tsStr) / 1000L; // µs → ms
                } else {
                    Object ts = reflectRaw(item, "timestamp", "A03");
                    if (ts instanceof Long)   timestamp = (Long) ts;
                    if (ts instanceof Number) timestamp = ((Number) ts).longValue();
                }
            } catch (Exception ignored) {}

            // text content:
            //   REST path (X.9ZA base): A1I:String
            //   MQTT path (X.0gF wrapper): A0o:Object holds the text String directly
            //   v408: nested text object with A00:String
            try {
                content = reflectString(item, "text", "A1I");
                if (content == null) {
                    // MQTT wrapper (X.0gF) stores text in A0o on the subclass, not on X.9ZA
                    Object mqttText = getFieldValue(item, "A0o");
                    if (mqttText instanceof String) content = (String) mqttText;
                }
                if (content == null) {
                    Object textObj = findFieldByNameHint(item, "text");
                    if (textObj instanceof String) {
                        content = (String) textObj;
                    } else if (textObj != null) {
                        content = reflectString(textObj, "text", "A00");
                    }
                }
            } catch (Exception ignored) {}

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

            // thread_id is NOT NULL in the schema; fall back to empty when unknown.
            if (threadId == null) threadId = "";
            // Track the most-recently-seen thread so the DM action-bar button can open this
            // chat's deleted messages. Opening a thread triggers a burst of A0P calls for its
            // items, so the last non-empty thread id is a good proxy for "the open chat".
            if (!threadId.isEmpty() && !threadId.equals(sCurrentThreadId)) {
                sCurrentThreadId = threadId;
            }

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
                antiRevokeItem(item, storedContent);
            } else {
                db.insertOrIgnore(messageId, threadId, senderId, senderUser, content, type, timestamp);
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
    private static void antiRevokeItem(Object item, String restoredContent) {
        // Reset hide_in_thread (all known obfuscated names + proto stable name).
        setField(item, "A1Y", false);           // v426 LX/9ZA; / v4xx LX/9wl;
        setField(item, "A2V", false);           // v408 LX/5jI;
        setField(item, "hideInThread_", false); // proto model stable name

        // Restore original text to BOTH text fields.
        if (restoredContent != null) {
            setField(item, "A1I", restoredContent); // v426 REST text field (base class)
            setField(item, "A0o", restoredContent); // v426 MQTT text field (subclass A0o:Object)
        }
    }

    /** Set a field by name on obj, walking the superclass chain. Silently ignores missing fields. */
    private static void setField(Object obj, String fieldName, Object value) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(obj, value);
                return;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Exception ignored) {
                return;
            }
        }
    }

    /**
     * Authoritative hide_in_thread (unsend) check.
     *
     * <p>The real {@code hide_in_thread} flag is {@code A1Y:Z}, declared on the base
     * DirectItem class {@code LX/9ZA;} — the SAME class that declares the String
     * {@code item_id} ({@code A13:String}). The MQTT subclass {@code LX/0gF;} declares its
     * own unrelated same-named fields (confirmed for {@code A13}, which is a boolean there),
     * so a naive {@code readBool(item,"A1Y")} can return a shadowing concrete-class boolean
     * that is {@code true} on perfectly normal messages — firing a bogus "unsent" notification
     * with null content (rendered as "[null]").
     *
     * <p>Fix: locate the base class by the declaring class of {@code A13:String}, then read
     * {@code A1Y} from THAT class only. If the base class can't be located we deliberately do
     * NOT fall back to a shadowing-prone plain read — we only trust de-obfuscated proto names.
     */
    private static boolean isHideInThread(Object item) {
        Class<?> base = declaringClassOfStringField(item, "A13");
        if (base != null) {
            Boolean v = readBoolOnClass(base, item, "A1Y"); // v426 hide_in_thread on LX/9ZA;
            if (v != null) return v;
        }
        // Stable / de-obfuscated names are not subject to subclass shadowing — safe to read directly.
        return readBool(item, "A2V")                  // v408 / reference APK
            || readBool(item, "hideInThread_")        // proto-model stable name
            || readBool(item, "is_deleted_for_self"); // sibling flag
    }

    /** Returns the class in obj's hierarchy that declares a field named {@code fieldName} of type String. */
    private static Class<?> declaringClassOfStringField(Object obj, String fieldName) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                if (f.getType() == String.class) return cls;
            } catch (NoSuchFieldException ignored) {}
            cls = cls.getSuperclass();
        }
        return null;
    }

    /** Read a boolean field declared on a SPECIFIC class (no chain walk). null = absent/non-boolean. */
    private static Boolean readBoolOnClass(Class<?> cls, Object obj, String fieldName) {
        try {
            Field f = cls.getDeclaredField(fieldName);
            if (f.getType() != boolean.class && f.getType() != Boolean.class) return null;
            f.setAccessible(true);
            Object v = f.get(obj);
            return v instanceof Boolean && (Boolean) v;
        } catch (Exception e) {
            return null;
        }
    }

    /** Read a field declared on a SPECIFIC class (no superclass-chain walk), or null. Used to read
     *  shadowed fields (e.g. item_type A0Y) from the base class rather than the MQTT subclass. */
    private static Object readFieldOnClass(Class<?> cls, Object obj, String fieldName) {
        try {
            Field f = cls.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /** Read a boolean field by exact name, tolerating Boolean/boolean. Walks the superclass chain. */
    private static boolean readBool(Object obj, String fieldName) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object v = f.get(obj);
                return v instanceof Boolean && (Boolean) v;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
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

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve the sender's username by reflecting the sender UserInfo entity off the DirectItem.
     *
     * Historic mapping:
     *   DirectItem.A02 → sender UserInfo entity, with sub-fields A00 = user_id, A01 = username.
     * Obfuscated names rotate per build, so we DON'T rely on a single hardcoded name. Strategy:
     *   1. Try known/candidate UserInfo field names on the item (walking the superclass chain).
     *   2. If none resolve, scan fields by type for a "UserInfo"/"User" object near the user_id.
     *   3. On the UserInfo object, try candidate username sub-fields, then fall back to a
     *      heuristic: a String field that is NOT all-digits (user_id is numeric) and looks like
     *      a handle. The matching user_id confirms we picked the right entity.
     *
     * Returns null if no username can be found (caller stores empty; UI falls back to sender_id).
     */
    private static String resolveSenderUsername(Object item, String senderId) {
        try {
            if (item == null) return null;

            // 1 + 2: locate the sender UserInfo entity.
            Object userInfo = null;
            for (String f : CANDIDATE_USERINFO_FIELDS) {
                Object v = getFieldValue(item, f);
                if (v != null && looksLikeUserInfo(v, senderId)) { userInfo = v; break; }
            }
            if (userInfo == null) {
                Object byType = findFieldByType(item, "userinfo");
                if (looksLikeUserInfo(byType, senderId)) userInfo = byType;
            }
            if (userInfo == null) {
                Object byType = findFieldByType(item, "user");
                if (looksLikeUserInfo(byType, senderId)) userInfo = byType;
            }
            if (userInfo == null) return null;

            // 3: read the username sub-field.
            for (String f : CANDIDATE_USERNAME_FIELDS) {
                Object v = getFieldValue(userInfo, f);
                if (isUsernameLike(v)) return (String) v;
            }
            // Heuristic fallback: first non-numeric String field on the UserInfo object.
            Class<?> cls = userInfo.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType() != String.class) continue;
                    f.setAccessible(true);
                    Object v;
                    try { v = f.get(userInfo); } catch (Exception e) { continue; }
                    if (isUsernameLike(v)) return (String) v;
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // v426 candidate field names — confirm/extend from the ObjectBrowser dump in logcat.
    private static final String[] CANDIDATE_USERINFO_FIELDS = {"A02", "A1L", "A0X", "A0Z"};
    private static final String[] CANDIDATE_USERNAME_FIELDS = {"A01", "A0a", "A0Y", "username"};

    /** A UserInfo-like object exposes the matching user_id (when known) on a sub-field. */
    private static boolean looksLikeUserInfo(Object obj, String senderId) {
        if (obj == null) return false;
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) return false;
        if (senderId == null || senderId.isEmpty()) return true; // can't cross-check; accept candidate
        for (String f : new String[]{"A00", "A01", "id", "pk", "user_id"}) {
            Object v = getFieldValue(obj, f);
            if (v != null && senderId.equals(v.toString())) return true;
        }
        return false;
    }

    /** A plausible username: non-empty String that is not purely numeric (those are IDs). */
    private static boolean isUsernameLike(Object v) {
        if (!(v instanceof String)) return false;
        String s = ((String) v).trim();
        return !s.isEmpty() && !s.matches("\\d+");
    }

    /** Try known JSON key name first, fall back to ProGuard obfuscated name. */
    private static String reflectString(Object obj, String jsonKey, String obfName) {
        // Use type-aware lookup: if the obfuscated name maps to a boolean on the concrete
        // class but a String on a superclass (e.g. A13 on LX/0gF; vs LX/9ZA;), skip the
        // wrong-type field and find the String. Falls back to plain lookup for the jsonKey.
        Object val = getFieldValueByType(obj, obfName, String.class);
        if (val == null) val = getFieldValue(obj, jsonKey);
        return val instanceof String ? (String) val : null;
    }

    /**
     * Try a field by name first (walking superclass chain), then invoke a zero-arg getter method.
     * Used for message-id which in v408+ is behind getter A0l() not a direct field.
     */
    private static String reflectStringOrInvoke(Object obj, String fieldName, String methodName) {
        Object v = getFieldValue(obj, fieldName);
        if (v instanceof String) return (String) v;
        // Try getter method (walk superclass chain for method too)
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                java.lang.reflect.Method m = cls.getDeclaredMethod(methodName);
                m.setAccessible(true);
                Object r = m.invoke(obj);
                if (r instanceof String) return (String) r;
            } catch (NoSuchMethodException ignored) {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extract the thread-id string from the DirectItem.
     * v426: thread_key stored in field A0W (DirectThreadKey), threadId is A00:String.
     * v408: thread_key in field A16/A18/A15, threadId is A00:String.
     * Uses getFieldValue to walk the superclass chain (needed when item is LX/0gF;).
     */
    private static String reflectThreadIdFromItem(Object item) {
        for (String keyField : new String[]{"A0W", "A16", "A18", "A15"}) {
            Object key = getFieldValue(item, keyField);
            if (key == null) continue;
            Object v = getFieldValue(key, "A00");
            if (v instanceof String && ((String) v).matches("\\d{15,}")) return (String) v;
        }
        // Fallback: the thread_id is a long numeric "thread fbid" (~39 digits, near 2^128) that
        // lives deeper in the item graph than the fixed field names above. Deep-search for the
        // LONGEST all-digit string — thread_id (≈39) outranks message_id (≈35) and user_id (≈11).
        return deepLongestNumericId(item, 30);
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

    /** Bounded BFS over an IG/obfuscated object graph; returns the longest all-digit String
     *  with length >= minLen (the thread fbid), or null. */
    static String deepLongestNumericId(Object root, int minLen) {
        try {
            java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            q.add(root); seen.put(root, Boolean.TRUE);
            String best = null;
            int budget = 4000;
            while (!q.isEmpty() && budget-- > 0) {
                Object o = q.poll();
                Class<?> k = o.getClass();
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
                            if (s.length() >= minLen && s.matches("\\d+")
                                    && (best == null || s.length() > best.length())) best = s;
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

    private static Object reflectRaw(Object obj, String jsonKey, String obfName) {
        Object v = getFieldValue(obj, jsonKey);
        if (v != null) return v;
        return getFieldValue(obj, obfName);
    }

    /** Walk the full superclass chain to find and read a field by name. */
    private static Object getFieldValue(Object obj, String fieldName) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Like getFieldValue but skips fields whose declared type is not assignable from
     * expectedType. Needed when the same obfuscated field name appears on both the
     * concrete class (wrong type, e.g. boolean A13 on LX/0gF;) and a superclass
     * (correct type, e.g. String A13 on LX/9ZA;). Plain getFieldValue would return
     * the concrete-class version first, giving the wrong value.
     */
    private static Object getFieldValueByType(Object obj, String fieldName, Class<?> expectedType) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                if (expectedType.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try { return f.get(obj); } catch (IllegalAccessException ignored) {}
                }
                // Wrong declared type or inaccessible — skip to superclass
            } catch (NoSuchFieldException ignored) {}
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static Object findFieldByType(Object obj, String typeNameHint) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().getSimpleName().toLowerCase().contains(typeNameHint.toLowerCase())) {
                    f.setAccessible(true);
                    try { return f.get(obj); } catch (Exception ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static Object findFieldByNameHint(Object obj, String nameHint) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getName().toLowerCase().contains(nameHint.toLowerCase())) {
                    f.setAccessible(true);
                    try { return f.get(obj); } catch (Exception ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }
}
