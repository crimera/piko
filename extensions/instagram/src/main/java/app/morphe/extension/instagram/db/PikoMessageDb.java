/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class PikoMessageDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "piko_dm_vault.db";
    private static final int DB_VERSION = 2;
    private static final String TABLE = "saved_messages";
    // sender_id → username directory. MQTT-delivered items carry only a numeric sender_id;
    // the REST path occasionally carries a full UserInfo. Every time we resolve a username we
    // persist it here so later MQTT-only items (and notifications) can show the real handle.
    private static final String DIR_TABLE = "user_directory";

    private static volatile PikoMessageDb instance;

    public static PikoMessageDb getInstance(Context context) {
        if (instance == null) {
            synchronized (PikoMessageDb.class) {
                if (instance == null) {
                    instance = new PikoMessageDb(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private PikoMessageDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "message_id TEXT UNIQUE NOT NULL," +
            "thread_id TEXT NOT NULL," +
            "sender_id TEXT," +
            "sender_username TEXT," +
            "content TEXT," +
            "message_type TEXT," +
            "timestamp INTEGER NOT NULL," +
            "is_deleted INTEGER DEFAULT 0" +
            ")"
        );
        db.execSQL("CREATE INDEX idx_thread_id ON " + TABLE + "(thread_id)");
        db.execSQL("CREATE INDEX idx_is_deleted ON " + TABLE + "(is_deleted)");
        createDirTable(db);
    }

    private void createDirTable(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS " + DIR_TABLE + " (" +
            "sender_id TEXT PRIMARY KEY," +
            "username TEXT NOT NULL" +
            ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive upgrade: keep captured messages, just add the new directory table.
        if (oldVersion < 2) createDirTable(db);
    }

    /**
     * Insert a captured message. If the row already exists (same message_id), the insert is
     * ignored — EXCEPT that any newly-arriving non-empty content / username / sender_id is
     * written into the existing row when its corresponding column is still empty.
     *
     * This matters because the feature captures the same message from multiple hooks at
     * different times: an early hook may store an empty-content placeholder, and the later
     * authoritative source (e.g. Hook 4 reading Instagram's own "messages" table at delete
     * time, or a username resolved after the fact) must be able to fill those blanks in.
     * A plain CONFLICT_IGNORE would silently drop that better data, leaving the UI showing
     * "[text]" / "Unknown".
     */
    public void insertOrIgnore(String messageId, String threadId, String senderId,
                               String senderUsername, String content, String type, long timestamp) {
        if (messageId == null || threadId == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("message_id", messageId);
        cv.put("thread_id", threadId);
        cv.put("sender_id", senderId);
        cv.put("sender_username", senderUsername != null ? senderUsername : "");
        cv.put("content", content != null ? content : "");
        cv.put("message_type", type != null ? type : "unknown");
        cv.put("timestamp", timestamp);
        long rowId = db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE);

        // rowId == -1 → conflict, row already existed. Backfill any empty columns with the
        // newly-supplied non-empty values.
        if (rowId == -1) {
            fillIfEmpty(db, messageId, "content", content);
            fillIfEmpty(db, messageId, "sender_username", senderUsername);
            fillIfEmpty(db, messageId, "sender_id", senderId);
        }
    }

    /** Update a single column on the existing row only when the new value is non-empty AND
     *  the stored value is currently null/empty. Preserves is_deleted, timestamp, etc. */
    private void fillIfEmpty(SQLiteDatabase db, String messageId, String column, String value) {
        if (value == null || value.isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put(column, value);
        db.update(TABLE, cv,
            "message_id = ? AND (" + column + " IS NULL OR " + column + " = '')",
            new String[]{messageId});
    }

    /** Persist a sender_id → username mapping (no-op for empty input). Latest non-empty wins. */
    public void putUsername(String senderId, String username) {
        if (senderId == null || senderId.isEmpty() || username == null || username.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("sender_id", senderId);
        cv.put("username", username);
        db.insertWithOnConflict(DIR_TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        // Backfill any stored messages from this sender that still lack a username.
        ContentValues mv = new ContentValues();
        mv.put("sender_username", username);
        db.update(TABLE, mv,
            "sender_id = ? AND (sender_username IS NULL OR sender_username = '')",
            new String[]{senderId});
    }

    /** Backfill sender_username for every row in a thread that still lacks one (1:1 chat title). */
    public void setThreadUsername(String threadId, String username) {
        if (threadId == null || threadId.isEmpty() || username == null || username.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("sender_username", username);
        db.update(TABLE, cv,
            "thread_id = ? AND (sender_username IS NULL OR sender_username = '')",
            new String[]{threadId});
    }

    /** Look up a previously-resolved username for a sender_id, or null. */
    public String getUsername(String senderId) {
        if (senderId == null || senderId.isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(DIR_TABLE, new String[]{"username"},
                "sender_id = ?", new String[]{senderId}, null, null, null);
        String result = null;
        if (c.moveToFirst()) result = c.getString(0);
        c.close();
        return (result != null && !result.isEmpty()) ? result : null;
    }

    /** True if a row for messageId already exists and is NOT yet marked deleted. Used to
     *  distinguish a live unsend (we saw the message alive first) from a historical unsent
     *  item that arrives already-hidden during a sync (never seen alive → don't notify). */
    public boolean isStoredAlive(String messageId) {
        if (messageId == null) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, new String[]{"is_deleted"}, "message_id = ?",
                new String[]{messageId}, null, null, null);
        boolean alive = false;
        if (c.moveToFirst()) alive = c.getInt(0) == 0;
        c.close();
        return alive;
    }

    /** Permanently remove one saved message from the vault. */
    public void deleteSaved(String messageId) {
        if (messageId == null) return;
        getWritableDatabase().delete(TABLE, "message_id = ?", new String[]{messageId});
    }

    /** Remove all saved messages (optionally just one thread). Pass null threadId to clear all. */
    public int clearSaved(String threadId) {
        if (threadId != null && !threadId.isEmpty()) {
            return getWritableDatabase().delete(TABLE, "thread_id = ?", new String[]{threadId});
        }
        return getWritableDatabase().delete(TABLE, null, null);
    }

    public void markDeleted(String messageId) {
        if (messageId == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_deleted", 1);
        db.update(TABLE, cv, "message_id = ?", new String[]{messageId});
    }

    /** Returns sender_username if non-empty, else sender_id (numeric), else null. */
    public String getSenderDisplay(String messageId) {
        if (messageId == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, new String[]{"sender_username", "sender_id"},
                "message_id = ?", new String[]{messageId}, null, null, null);
        String result = null;
        String uid = null;
        if (c.moveToFirst()) {
            String uname = c.getString(0);
            uid          = c.getString(1);
            if (uname != null && !uname.isEmpty()) result = uname;
        }
        c.close();
        // Row had no username — consult the directory before falling back to the numeric id.
        if (result == null && uid != null && !uid.isEmpty()) {
            String dir = getUsername(uid);
            result = (dir != null) ? dir : uid;
        }
        return result;
    }

    /** thread_id stored for a message, or null. */
    public String getThreadIdOf(String messageId) {
        if (messageId == null) return null;
        Cursor c = getReadableDatabase().query(TABLE, new String[]{"thread_id"},
                "message_id = ?", new String[]{messageId}, null, null, null);
        String r = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return (r != null && !r.isEmpty()) ? r : null;
    }

    /** message_type stored for a message (e.g. image/video/voice_media), or null. */
    public String getMessageType(String messageId) {
        if (messageId == null) return null;
        Cursor c = getReadableDatabase().query(TABLE, new String[]{"message_type"},
                "message_id = ?", new String[]{messageId}, null, null, null);
        String r = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return (r != null && !r.isEmpty() && !"unknown".equals(r)) ? r : null;
    }

    /** sender_id stored for a message, or null. */
    public String getSenderId(String messageId) {
        if (messageId == null) return null;
        Cursor c = getReadableDatabase().query(TABLE, new String[]{"sender_id"},
                "message_id = ?", new String[]{messageId}, null, null, null);
        String r = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return (r != null && !r.isEmpty()) ? r : null;
    }

    /** Any non-empty sender_username recorded for a thread (set from the chat title), or null. */
    public String getThreadUsername(String threadId) {
        if (threadId == null || threadId.isEmpty()) return null;
        Cursor c = getReadableDatabase().query(TABLE, new String[]{"sender_username"},
                "thread_id = ? AND sender_username IS NOT NULL AND sender_username != ''",
                new String[]{threadId}, null, null, null, "1");
        String r = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return (r != null && !r.isEmpty()) ? r : null;
    }

    public String getStoredContent(String messageId) {
        if (messageId == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, new String[]{"content"}, "message_id = ?",
                new String[]{messageId}, null, null, null);
        String result = null;
        if (c.moveToFirst()) result = c.getString(0);
        c.close();
        return (result != null && !result.isEmpty()) ? result : null;
    }

    // Returns [messageId, threadId, senderUsername, content, messageType, timestamp, senderId]
    public List<String[]> getDeletedMessages() {
        List<String[]> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, "is_deleted = 1", null, null, null, "timestamp DESC");
        while (c.moveToNext()) {
            result.add(rowToStringArray(c));
        }
        c.close();
        return result;
    }

    public List<String[]> getDeletedMessagesForThread(String threadId) {
        List<String[]> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, "is_deleted = 1 AND thread_id = ?",
            new String[]{threadId}, null, null, "timestamp DESC");
        while (c.moveToNext()) {
            result.add(rowToStringArray(c));
        }
        c.close();
        return result;
    }

    // Returns [messageId, threadId, senderUsername, content, messageType, timestamp, isDeleted]
    public List<String[]> getAllMessages() {
        List<String[]> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, "timestamp DESC");
        while (c.moveToNext()) {
            result.add(rowToStringArrayFull(c));
        }
        c.close();
        return result;
    }

    private String[] rowToStringArray(Cursor c) {
        return new String[]{
            c.getString(c.getColumnIndexOrThrow("message_id")),
            c.getString(c.getColumnIndexOrThrow("thread_id")),
            c.getString(c.getColumnIndexOrThrow("sender_username")),
            c.getString(c.getColumnIndexOrThrow("content")),
            c.getString(c.getColumnIndexOrThrow("message_type")),
            String.valueOf(c.getLong(c.getColumnIndexOrThrow("timestamp"))),
            c.getString(c.getColumnIndexOrThrow("sender_id"))
        };
    }

    private String[] rowToStringArrayFull(Cursor c) {
        return new String[]{
            c.getString(c.getColumnIndexOrThrow("message_id")),
            c.getString(c.getColumnIndexOrThrow("thread_id")),
            c.getString(c.getColumnIndexOrThrow("sender_username")),
            c.getString(c.getColumnIndexOrThrow("content")),
            c.getString(c.getColumnIndexOrThrow("message_type")),
            String.valueOf(c.getLong(c.getColumnIndexOrThrow("timestamp"))),
            String.valueOf(c.getInt(c.getColumnIndexOrThrow("is_deleted")))
        };
    }
}
