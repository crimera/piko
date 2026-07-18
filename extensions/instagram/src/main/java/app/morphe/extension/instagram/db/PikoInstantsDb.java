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

import app.morphe.extension.shared.Logger;

/**
 * Local persistent store for captured Instants (quicksnap). View-once instants change on tap and
 * vanish after viewing, so InstantsDownloadHook records each one's CDN links as it's viewed; the
 * "Saved Instants" screen (InstantsVaultActivity) reads them back for viewing and download.
 *
 * <p>Modelled on the DM vault (PikoMessageDb) but a separate table/file — instants are not messages.
 */
public class PikoInstantsDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "piko_instants_vault.db";
    private static final int DB_VERSION = 2;
    private static final String TABLE = "saved_instants";

    private static final String COL_ID = "media_id";
    private static final String COL_USERNAME = "username";
    private static final String COL_IMAGE_URL = "image_url";
    private static final String COL_VIDEO_URL = "video_url";
    private static final String COL_IS_VIDEO = "is_video";
    private static final String COL_TS = "ts";
    private static final String COL_USERID = "userid";

    private static volatile PikoInstantsDb sInstance;

    public static synchronized PikoInstantsDb getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PikoInstantsDb(context.getApplicationContext());
        }
        return sInstance;
    }

    private PikoInstantsDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABLE + " (" +
                COL_ID + " TEXT PRIMARY KEY, " +
                COL_USERNAME + " TEXT, " +
                COL_IMAGE_URL + " TEXT, " +
                COL_VIDEO_URL + " TEXT, " +
                COL_IS_VIDEO + " INTEGER DEFAULT 0, " +
                COL_TS + " INTEGER, " +
                COL_USERID + " TEXT" +
            ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 -> v2: add a stable userid so grouping survives username changes. Best-effort.
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_USERID + " TEXT");
            } catch (Throwable t) {
                Logger.printException(() -> "PikoInstantsDb.onUpgrade v2 failed", t);
            }
        }
    }

    /** Records an instant, ignoring duplicates (same media id). Best-effort — never throws. */
    public void insertOrIgnore(String mediaId, String username, String userId, String imageUrl,
                               String videoUrl, boolean isVideo) {
        if (mediaId == null || mediaId.isEmpty()) return;
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_ID, mediaId);
            cv.put(COL_USERNAME, username);
            cv.put(COL_USERID, userId);
            cv.put(COL_IMAGE_URL, imageUrl);
            cv.put(COL_VIDEO_URL, videoUrl);
            cv.put(COL_IS_VIDEO, isVideo ? 1 : 0);
            cv.put(COL_TS, System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict(
                TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Throwable t) {
            Logger.printException(() -> "PikoInstantsDb.insertOrIgnore failed", t);
        }
    }

    public boolean has(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return false;
        try (Cursor c = getReadableDatabase().query(
                TABLE, new String[]{COL_ID}, COL_ID + "=?", new String[]{mediaId},
                null, null, null, "1")) {
            return c.moveToFirst();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Newest first. Each row: {media_id, username, image_url, video_url, is_video, ts, userid}. */
    public List<String[]> getAll() {
        List<String[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                TABLE,
                new String[]{COL_ID, COL_USERNAME, COL_IMAGE_URL, COL_VIDEO_URL, COL_IS_VIDEO, COL_TS, COL_USERID},
                null, null, null, null, COL_TS + " DESC")) {
            while (c.moveToNext()) {
                out.add(new String[]{
                    c.getString(0), c.getString(1), c.getString(2),
                    c.getString(3), String.valueOf(c.getInt(4)), String.valueOf(c.getLong(5)),
                    c.getString(6),
                });
            }
        } catch (Throwable t) {
            Logger.printException(() -> "PikoInstantsDb.getAll failed", t);
        }
        return out;
    }

    public void delete(String mediaId) {
        try {
            getWritableDatabase().delete(TABLE, COL_ID + "=?", new String[]{mediaId});
        } catch (Throwable t) {
            Logger.printException(() -> "PikoInstantsDb.delete failed", t);
        }
    }

    public void clearAll() {
        try {
            getWritableDatabase().delete(TABLE, null, null);
        } catch (Throwable t) {
            Logger.printException(() -> "PikoInstantsDb.clearAll failed", t);
        }
    }
}
