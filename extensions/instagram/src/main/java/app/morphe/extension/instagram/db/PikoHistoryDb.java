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

/** Feed posts, Reels and Stories the user has viewed, newest kept up to {@link #MAX_ROWS}. */
public class PikoHistoryDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "piko_view_history.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "view_history";

    private static final int MAX_ROWS = 2000;

    private static volatile PikoHistoryDb instance;

    public static PikoHistoryDb getInstance(Context context) {
        if (instance == null) {
            synchronized (PikoHistoryDb.class) {
                if (instance == null) {
                    instance = new PikoHistoryDb(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private PikoHistoryDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABLE + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "media_pk TEXT NOT NULL UNIQUE," +
            "post_type TEXT NOT NULL," +
            "owner_username TEXT," +
            "thumb_url TEXT," +
            "caption TEXT," +
            "permalink TEXT NOT NULL," +
            "viewed_at INTEGER NOT NULL" +
            ")"
        );
        db.execSQL("CREATE INDEX idx_viewed_at ON " + TABLE + "(viewed_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /** Adds a viewed item, or moves an already logged one to the top. */
    public void logView(String mediaPk, String postType, String ownerUsername, String thumbUrl,
                        String caption, String permalink) {
        ContentValues cv = new ContentValues();
        cv.put("media_pk", mediaPk);
        cv.put("post_type", postType);
        cv.put("owner_username", ownerUsername);
        cv.put("thumb_url", thumbUrl);
        cv.put("caption", caption);
        cv.put("permalink", permalink);
        cv.put("viewed_at", System.currentTimeMillis());

        SQLiteDatabase db = getWritableDatabase();
        db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        db.execSQL(
            "DELETE FROM " + TABLE + " WHERE id IN (" +
            "SELECT id FROM " + TABLE + " ORDER BY viewed_at DESC LIMIT -1 OFFSET " + MAX_ROWS +
            ")"
        );
    }

    /** All entries, newest first. */
    public List<Entry> getHistory() {
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null, "viewed_at DESC")) {
            while (c.moveToNext()) {
                result.add(new Entry(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    c.getString(c.getColumnIndexOrThrow("post_type")),
                    c.getString(c.getColumnIndexOrThrow("owner_username")),
                    c.getString(c.getColumnIndexOrThrow("thumb_url")),
                    c.getString(c.getColumnIndexOrThrow("caption")),
                    c.getString(c.getColumnIndexOrThrow("permalink")),
                    c.getLong(c.getColumnIndexOrThrow("viewed_at"))
                ));
            }
        }
        return result;
    }

    public void deleteEntry(long id) {
        getWritableDatabase().delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    public static final class Entry {
        public final long id;
        public final String postType;
        public final String ownerUsername;
        public final String thumbUrl;
        public final String caption;
        public final String permalink;
        public final long viewedAt;

        Entry(long id, String postType, String ownerUsername, String thumbUrl, String caption,
              String permalink, long viewedAt) {
            this.id = id;
            this.postType = postType;
            this.ownerUsername = ownerUsername;
            this.thumbUrl = thumbUrl;
            this.caption = caption;
            this.permalink = permalink;
            this.viewedAt = viewedAt;
        }
    }
}
