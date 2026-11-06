package app.morphe.extension.instagram.patches.notification;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.patches.notification.state.ChatKey;
import app.morphe.extension.instagram.patches.notification.state.ChatStateCodec;
import app.morphe.extension.instagram.patches.notification.state.ChatStore;
import app.morphe.extension.instagram.patches.notification.state.Message;
import app.morphe.extension.instagram.patches.notification.state.StoredChat;
import app.morphe.extension.shared.Utils;

/**
 * Keeps the reducer's chat state in the vault (R10). Called only from the ChatExecutor thread (R2).
 * Any SQLite or IO error is logged as {@code FAILED store} and the store goes memory-only for the
 * rest of the process (R3): notifications keep working, nothing is persisted. Not gated by
 * Pref.saveDeletedMessages(): this is notification state, not saved messages.
 */
final class SqliteChatStore implements ChatStore {
    static final long MAX_AGE_MS = 7L * 24 * 60 * 60_000L;

    private boolean failed;
    private boolean purged;
    private final Map<ChatKey, StoredChat> lastSaved = new HashMap<>();

    /**
     * Debug (log builds only): keep the texts of read chats too, so a session can be replayed from
     * the database. Normal builds drop them once a chat has nothing unread.
     */
    private static boolean keepTexts() {
        try {
            return NotificationLog.isBuiltIn() && app.morphe.extension.instagram.utils.Pref.keepNotificationTexts();
        } catch (Throwable t) {
            return false;
        }
    }

    private SQLiteDatabase db() {
        return PikoMessageDb.getInstance(Utils.getContext()).getWritableDatabase();
    }

    private void fail(String what, Throwable t) {
        failed = true;
        NotificationLog.event("FAILED store " + what + ": " + t + " (memory only for this process)");
    }

    private void purge(SQLiteDatabase db, long now) {
        purged = true;
        String cutoff = String.valueOf(now - MAX_AGE_MS);
        db.execSQL("DELETE FROM chat_messages WHERE EXISTS (SELECT 1 FROM chat_state s WHERE s.viewer_id = "
                + "chat_messages.viewer_id AND s.thread_id = chat_messages.thread_id AND s.updated_at < ?)",
                new Object[]{cutoff});
        db.delete("chat_state", "updated_at < ?", new String[]{cutoff});
    }

    @Override
    public StoredChat load(ChatKey key) {
        if (failed) return null;
        try {
            SQLiteDatabase db = db();
            if (!purged) purge(db, System.currentTimeMillis());
            String[] args = {key.viewerId, key.threadId};
            StoredChat c = null;
            Cursor cur = db.rawQuery("SELECT read_up_to, reply_read_up_to, slots, digest_count, digest_text,"
                    + " digest_sender_id, digest_sender_name, digest_arrival, next_seq, has_plan, plan_clear,"
                    + " plan_alert, plan_more, plan_sender FROM chat_state WHERE viewer_id = ? AND thread_id = ?", args);
            try {
                if (cur.moveToFirst()) {
                    c = new StoredChat();
                    c.readUpTo = cur.getLong(0);
                    c.replyReadUpTo = cur.getLong(1);
                    c.slots = cur.getInt(2);
                    c.digestCount = cur.getInt(3);
                    c.digestText = cur.getString(4);
                    c.digestSenderId = cur.getString(5);
                    c.digestSenderName = cur.getString(6);
                    c.digestArrival = cur.getLong(7);
                    c.nextSeq = cur.getLong(8);
                    c.hasPlan = cur.getInt(9) != 0;
                    c.planClear = cur.getInt(10) != 0;
                    c.planAlert = cur.getInt(11) != 0;
                    c.planMore = cur.getInt(12);
                    c.planSender = cur.getString(13);
                }
            } finally {
                cur.close();
            }
            if (c == null) return null;
            cur = db.rawQuery("SELECT role, item_id, sender_id, sender_is_viewer, text, server_time, arrival_time,"
                    + " kind, edited, flags, seq FROM chat_messages WHERE viewer_id = ? AND thread_id = ?"
                    + " ORDER BY role, pos", args);
            try {
                while (cur.moveToNext()) {
                    Message.Kind kind;
                    try {
                        kind = Message.Kind.valueOf(cur.getString(7));
                    } catch (RuntimeException e) {
                        kind = Message.Kind.TEXT;
                    }
                    Message m = new Message(cur.getString(1), cur.getString(2), cur.getInt(3) != 0,
                            cur.getString(4), cur.getLong(5), cur.getLong(6), cur.getInt(8) != 0, kind);
                    if (cur.getInt(0) == 0) c.held.add(new StoredChat.Held(m, cur.getLong(10), cur.getInt(9)));
                    else c.planMessages.add(m);
                }
            } finally {
                cur.close();
            }
            lastSaved.put(key, c);
            NotificationLog.event("STORE loaded chat=" + key.threadId + " msgs=" + c.held.size()
                    + " readUpTo=" + c.readUpTo);
            return c;
        } catch (Throwable t) {
            fail("load", t);
            return null;
        }
    }

    @Override
    public void save(Map<ChatKey, StoredChat> chats, long now) {
        if (failed) return;
        try {
            SQLiteDatabase db = db();
            db.beginTransaction();
            try {
                for (Map.Entry<ChatKey, StoredChat> en : chats.entrySet()) {
                    StoredChat disk = ChatStateCodec.forDisk(en.getValue(), keepTexts());
                    if (disk.equals(lastSaved.get(en.getKey()))) continue;
                    write(db, en.getKey(), disk, now);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            for (Map.Entry<ChatKey, StoredChat> en : chats.entrySet()) {
                lastSaved.put(en.getKey(), ChatStateCodec.forDisk(en.getValue(), keepTexts()));
            }
        } catch (Throwable t) {
            fail("save", t);
        }
    }

    @Override
    public void forget(String viewerId) {
        lastSaved.keySet().removeIf(k -> viewerId.equals(k.viewerId));
        if (failed) return;
        try {
            SQLiteDatabase db = db();
            String[] args = {viewerId};
            int chats = db.delete("chat_state", "viewer_id = ?", args);
            db.delete("chat_messages", "viewer_id = ?", args);
            NotificationLog.event("STORE forgot " + chats + " chat(s) of a logged out account");
        } catch (Throwable t) {
            fail("forget", t);
        }
    }

    private static void write(SQLiteDatabase db, ChatKey key, StoredChat c, long now) {
        String[] args = {key.viewerId, key.threadId};
        db.delete("chat_messages", "viewer_id = ? AND thread_id = ?", args);
        ContentValues cv = new ContentValues();
        cv.put("viewer_id", key.viewerId);
        cv.put("thread_id", key.threadId);
        cv.put("read_up_to", c.readUpTo);
        cv.put("reply_read_up_to", c.replyReadUpTo);
        cv.put("slots", c.slots);
        cv.put("digest_count", c.digestCount);
        cv.put("digest_text", c.digestText);
        cv.put("digest_sender_id", c.digestSenderId);
        cv.put("digest_sender_name", c.digestSenderName);
        cv.put("digest_arrival", c.digestArrival);
        cv.put("next_seq", c.nextSeq);
        cv.put("has_plan", c.hasPlan ? 1 : 0);
        cv.put("plan_clear", c.planClear ? 1 : 0);
        cv.put("plan_alert", c.planAlert ? 1 : 0);
        cv.put("plan_more", c.planMore);
        cv.put("plan_sender", c.planSender);
        cv.put("updated_at", now);
        db.insertWithOnConflict("chat_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        int pos = 0;
        for (StoredChat.Held h : c.held) insert(db, key, 0, pos++, h.msg, h.flags, h.seq);
        pos = 0;
        List<Message> plan = c.planMessages;
        for (Message m : plan) insert(db, key, 1, pos++, m, 0, 0);
    }

    private static void insert(SQLiteDatabase db, ChatKey key, int role, int pos, Message m, int flags, long seq) {
        ContentValues cv = new ContentValues();
        cv.put("viewer_id", key.viewerId);
        cv.put("thread_id", key.threadId);
        cv.put("role", role);
        cv.put("pos", pos);
        cv.put("item_id", m.itemId);
        cv.put("sender_id", m.senderId);
        cv.put("sender_is_viewer", m.senderIsViewer ? 1 : 0);
        cv.put("text", m.text);
        cv.put("server_time", m.serverTime);
        cv.put("arrival_time", m.arrivalTime);
        cv.put("kind", m.kind.name());
        cv.put("edited", m.edited ? 1 : 0);
        cv.put("flags", flags);
        cv.put("seq", seq);
        db.insert("chat_messages", null, cv);
    }
}
