/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.content.Intent;

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

            // Display name for the sender: a previously-persisted handle (populated from the
            // chat title when the thread is opened). Null is fine — the screen falls back to id.
            String senderUser = (senderId != null) ? db.getUsername(senderId) : null;

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
    // Hook 4: fires at entry of the SQLite DAO method that hides (unsends) a DirectItem.
    // We receive p2 (server_item_id) / p3 (client_item_id). Hook 1/2 already captured the
    // message into our vault as it arrived, so here we just mark it deleted and notify —
    // no need to read Instagram's own DB (which is empty under MSYS on v426 anyway).
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

            // Ensure a row exists (skeleton if Hook 1/2 somehow missed it) and mark it deleted.
            vault.insertOrIgnore(itemId, "", null, null, null, messageType, System.currentTimeMillis());
            vault.markDeleted(itemId);

            // Notify only for messages we actually received (skips our own unsends + uncaptured).
            if (wasReceived && claimNotification(itemId)) {
                String stored = vault.getStoredContent(itemId);
                boolean isMedia = stored == null || stored.isEmpty()
                        || stored.startsWith("http") || stored.startsWith("[");
                String notifBody = isMedia ? MediaLabel.deleted(messageType) : stored;
                String name = vault.getThreadUsername(vault.getThreadIdOf(itemId));
                if (name == null) name = vault.getSenderDisplay(itemId);
                notifyDeletion(name, notifBody, messageType);
            }
        } catch (Exception e) {
            piko("SavedMessagesHook.onMessageHiddenFromDb: " + e);
        }
    }
}
