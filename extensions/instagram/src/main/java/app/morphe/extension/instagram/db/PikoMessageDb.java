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
    static final int DB_VERSION = 4;
    private static final String TABLE = "saved_messages";
    // sender_id → username directory. MQTT-delivered items carry only a numeric sender_id;
    // the REST path occasionally carries a full UserInfo. Every time we resolve a username we
    // persist it here so later MQTT-only items can show the real handle.
    private static final String DIR_TABLE = "user_directory";

    private static volatile PikoMessageDb instance;

    static String normalizeIdentity(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static String canonicalMessageId(String serverId, String clientContext) {
        String server = normalizeIdentity(serverId);
        return server != null ? server : normalizeIdentity(clientContext);
    }

    static String[] identityIndexStatements() {
        return new String[]{
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_server_id ON " + TABLE
                        + "(server_id) WHERE server_id IS NOT NULL AND server_id != ''",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_client_context ON " + TABLE
                        + "(client_context) WHERE client_context IS NOT NULL AND client_context != ''"
        };
    }

    static String[] upgradeStatements(int oldVersion) {
        List<String> statements = new ArrayList<>();
        if (oldVersion < 3) {
            statements.add("ALTER TABLE " + TABLE + " ADD COLUMN server_id TEXT");
            statements.add("ALTER TABLE " + TABLE + " ADD COLUMN client_context TEXT");
            statements.add("UPDATE " + TABLE + " SET server_id = message_id "
                    + "WHERE message_id GLOB '[0-9]*' AND message_id NOT GLOB '*[^0-9]*'");
            java.util.Collections.addAll(statements, identityIndexStatements());
        }
        return statements.toArray(new String[0]);
    }

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
            "server_id TEXT," +
            "client_context TEXT," +
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
        createIdentityIndexes(db);
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

    private void createIdentityIndexes(SQLiteDatabase db) {
        for (String statement : identityIndexStatements()) {
            db.execSQL(statement);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createDirTable(db);
        for (String statement : upgradeStatements(oldVersion)) {
            db.execSQL(statement);
        }
    }

    /** Insert or enrich a captured message using exact Instagram identifiers. */
    public String upsertMessage(String serverId, String clientContext, String threadId,
                                String senderId, String senderUsername, String content,
                                String type, long timestamp) {
        String normalizedServer = normalizeIdentity(serverId);
        String normalizedClient = normalizeIdentity(clientContext);
        String canonicalId = canonicalMessageId(normalizedServer, normalizedClient);
        if (canonicalId == null) return null;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String messageId = resolveAndMergeMessageId(db, normalizedServer, normalizedClient);
            if (messageId == null) {
                ContentValues values = new ContentValues();
                values.put("message_id", canonicalId);
                values.put("server_id", normalizedServer);
                values.put("client_context", normalizedClient);
                values.put("thread_id", threadId != null ? threadId : "");
                values.put("sender_id", senderId);
                values.put("sender_username", senderUsername != null ? senderUsername : "");
                values.put("content", content != null ? content : "");
                values.put("message_type", type != null ? type : "unknown");
                values.put("timestamp", timestamp);
                long inserted = db.insertWithOnConflict(
                        TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                messageId = inserted == -1
                        ? resolveAndMergeMessageId(db, normalizedServer, normalizedClient)
                        : canonicalId;
            }

            if (messageId == null) return null;
            fillIfEmpty(db, messageId, "server_id", normalizedServer);
            fillIfEmpty(db, messageId, "client_context", normalizedClient);
            fillIfEmpty(db, messageId, "thread_id", threadId);
            fillIfEmpty(db, messageId, "sender_id", senderId);
            fillIfEmpty(db, messageId, "sender_username", senderUsername);
            fillIfEmpty(db, messageId, "message_type", type);
            if (content != null && content.startsWith("http")) {
                upgradeContentToUrl(db, messageId, content);
            } else {
                fillIfEmpty(db, messageId, "content", content);
            }
            db.setTransactionSuccessful();
            return messageId;
        } finally {
            db.endTransaction();
        }
    }

    /** Resolve exact identifiers and merge separate server/client captures when both exist. */
    public String resolveAndMergeMessageId(String serverId, String clientContext) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String messageId = resolveAndMergeMessageId(db, serverId, clientContext);
            db.setTransactionSuccessful();
            return messageId;
        } finally {
            db.endTransaction();
        }
    }

    private String resolveAndMergeMessageId(SQLiteDatabase db, String serverId,
                                            String clientContext) {
        String normalizedServer = normalizeIdentity(serverId);
        String normalizedClient = normalizeIdentity(clientContext);
        String messageId = null;

        if (normalizedServer != null) {
            messageId = findMessageIdByColumn(db, "server_id", normalizedServer);
        }
        if (normalizedClient != null) {
            String clientMessageId = findMessageIdByColumn(
                    db, "client_context", normalizedClient);
            if (messageId == null) {
                messageId = clientMessageId;
            } else if (clientMessageId != null && !messageId.equals(clientMessageId)) {
                String olderMessageId = findOlderMessageId(db, messageId, clientMessageId);
                String newerMessageId = olderMessageId.equals(messageId)
                        ? clientMessageId : messageId;
                mergeRows(db, olderMessageId, newerMessageId);
                messageId = olderMessageId;
            }
        }

        if (messageId != null) {
            fillIfEmpty(db, messageId, "server_id", normalizedServer);
            fillIfEmpty(db, messageId, "client_context", normalizedClient);
        }
        return messageId;
    }

    private String findMessageIdByColumn(SQLiteDatabase db, String identityColumn,
                                         String identity) {
        Cursor cursor = db.query(
                TABLE,
                new String[]{"message_id"},
                identityColumn + " = ? OR message_id = ?",
                new String[]{identity, identity},
                null,
                null,
                "id ASC",
                "1"
        );
        String result = cursor.moveToFirst() ? cursor.getString(0) : null;
        cursor.close();
        return result;
    }

    private String findOlderMessageId(SQLiteDatabase db, String firstId, String secondId) {
        Cursor cursor = db.query(
                TABLE,
                new String[]{"message_id"},
                "message_id IN (?, ?)",
                new String[]{firstId, secondId},
                null,
                null,
                "id ASC",
                "1"
        );
        String result = cursor.moveToFirst() ? cursor.getString(0) : firstId;
        cursor.close();
        return result;
    }

    /** Merge server-only and client-only captures once a callback supplies both ids. */
    private void mergeRows(SQLiteDatabase db, String primaryId, String secondaryId) {
        Cursor cursor = db.query(
                TABLE,
                new String[]{
                        "server_id", "client_context", "thread_id", "sender_id",
                        "sender_username", "content", "message_type", "is_deleted"
                },
                "message_id = ?",
                new String[]{secondaryId},
                null,
                null,
                null,
                "1"
        );
        if (!cursor.moveToFirst()) {
            cursor.close();
            return;
        }

        String secondaryServer = cursor.getString(0);
        String secondaryClient = cursor.getString(1);
        String threadId = cursor.getString(2);
        String senderId = cursor.getString(3);
        String senderUsername = cursor.getString(4);
        String content = cursor.getString(5);
        String messageType = cursor.getString(6);
        boolean deleted = cursor.getInt(7) != 0;
        cursor.close();

        ContentValues released = new ContentValues();
        released.putNull("server_id");
        released.putNull("client_context");
        db.update(TABLE, released, "message_id = ?", new String[]{secondaryId});

        fillIfEmpty(db, primaryId, "server_id", secondaryServer);
        fillIfEmpty(db, primaryId, "client_context", secondaryClient);
        fillIfEmpty(db, primaryId, "thread_id", threadId);
        fillIfEmpty(db, primaryId, "sender_id", senderId);
        fillIfEmpty(db, primaryId, "sender_username", senderUsername);
        fillIfEmpty(db, primaryId, "message_type", messageType);
        if (content != null && content.startsWith("http")) {
            upgradeContentToUrl(db, primaryId, content);
        } else {
            fillIfEmpty(db, primaryId, "content", content);
        }
        if (deleted) {
            ContentValues values = new ContentValues();
            values.put("is_deleted", 1);
            db.update(TABLE, values, "message_id = ?", new String[]{primaryId});
        }
        db.delete(TABLE, "message_id = ?", new String[]{secondaryId});
    }

    /** Overwrite stored content with a media url when the stored value isn't already an http link.
     *  A media DM is often captured first (MQTT real-time) before its CDN url is resolved, so the
     *  row lands with a caption or empty placeholder; a later pass (REST thread-history) that finds
     *  the url must be able to replace that so the item stays tappable / downloadable. */
    private void upgradeContentToUrl(SQLiteDatabase db, String messageId, String url) {
        ContentValues cv = new ContentValues();
        cv.put("content", url);
        db.update(TABLE, cv,
            "message_id = ? AND (content IS NULL OR content = '' OR content NOT LIKE 'http%')",
            new String[]{messageId});
    }

    /** Update a single column on the existing row only when the new value is non-empty AND
     *  the stored value is currently null/empty. Preserves is_deleted, timestamp, etc. */
    private void fillIfEmpty(SQLiteDatabase db, String messageId, String column, String value) {
        if (value == null || value.isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put(column, value);
        String emptyCondition = column + " IS NULL OR " + column + " = ''";
        if ("message_type".equals(column)) {
            emptyCondition += " OR " + column + " = 'unknown'";
        }
        db.update(TABLE, cv,
            "message_id = ? AND (" + emptyCondition + ")",
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

    /** Permanently remove saved messages from the vault as one atomic operation. */
    public void deleteSaved(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String messageId : messageIds) {
                if (messageId != null) {
                    db.delete(TABLE, "message_id = ?", new String[]{messageId});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Marks a live row deleted and returns true only for the first successful transition. */
    public boolean markDeleted(String messageId) {
        if (messageId == null) return false;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_deleted", 1);
        return db.update(TABLE, cv, "message_id = ? AND is_deleted = 0",
                new String[]{messageId}) == 1;
    }

    /** Returns the best available sender name for a notification. */
    public String getSenderDisplay(String messageId) {
        if (messageId == null) return null;
        Cursor cursor = getReadableDatabase().query(
                TABLE, new String[]{"sender_username", "sender_id"},
                "message_id = ?", new String[]{messageId}, null, null, null);
        String username = null;
        String senderId = null;
        if (cursor.moveToFirst()) {
            username = cursor.getString(0);
            senderId = cursor.getString(1);
        }
        cursor.close();
        if (username != null && !username.isEmpty()) return username;
        if (senderId == null || senderId.isEmpty()) return null;
        String directoryUsername = getUsername(senderId);
        return directoryUsername != null ? directoryUsername : senderId;
    }

    /** Returns the stored message type, or null when it is unknown. */
    public String getMessageType(String messageId) {
        if (messageId == null) return null;
        Cursor cursor = getReadableDatabase().query(
                TABLE, new String[]{"message_type"},
                "message_id = ?", new String[]{messageId}, null, null, null);
        String type = cursor.moveToFirst() ? cursor.getString(0) : null;
        cursor.close();
        return type != null && !type.isEmpty() && !"unknown".equals(type) ? type : null;
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

    /** A previously stored username for this sender in this thread, or null. */
    public String getThreadUsername(String threadId, String senderId) {
        if (threadId == null || threadId.isEmpty()
                || senderId == null || senderId.isEmpty()) return null;
        Cursor c = getReadableDatabase().query(TABLE, new String[]{"sender_username"},
                "thread_id = ? AND sender_id = ?"
                        + " AND sender_username IS NOT NULL AND sender_username != ''",
                new String[]{threadId, senderId}, null, null, null, "1");
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

    private static final String HAS_CONTENT =
        " AND (COALESCE(content, '') <> '' OR COALESCE(sender_id, '') <> ''"
            + " OR COALESCE(sender_username, '') <> '')";

    // Returns [messageId, threadId, senderUsername, content, messageType, timestamp, senderId]
    public List<String[]> getDeletedMessages() {
        List<String[]> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, "is_deleted = 1" + HAS_CONTENT, null, null, null, "timestamp DESC");
        while (c.moveToNext()) {
            result.add(rowToStringArray(c));
        }
        c.close();
        return result;
    }

    private String[] rowToStringArray(Cursor c) {
        String senderId = c.getString(c.getColumnIndexOrThrow("sender_id"));
        return new String[]{
            c.getString(c.getColumnIndexOrThrow("message_id")),
            c.getString(c.getColumnIndexOrThrow("thread_id")),
            resolveUsername(c.getString(c.getColumnIndexOrThrow("sender_username")), senderId),
            c.getString(c.getColumnIndexOrThrow("content")),
            c.getString(c.getColumnIndexOrThrow("message_type")),
            String.valueOf(c.getLong(c.getColumnIndexOrThrow("timestamp"))),
            senderId
        };
    }

    /**
     * The display username for a row: the stored sender_username if present, otherwise the
     * sender_id → username directory (populated from thread loads / 1:1 chats). Returns the
     * stored value (possibly empty) when the directory has no entry, so the caller's existing
     * numeric-id fallback still applies for a sender we have never seen named.
     */
    private String resolveUsername(String storedUsername, String senderId) {
        if (storedUsername != null && !storedUsername.isEmpty()) return storedUsername;
        if (senderId != null && !senderId.isEmpty()) {
            String dir = getUsername(senderId);
            if (dir != null && !dir.isEmpty()) return dir;
        }
        return storedUsername;
    }

}
