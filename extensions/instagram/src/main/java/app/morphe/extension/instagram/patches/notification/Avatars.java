/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Photos of the people in chats, on disk per user. Stock Instagram only attaches the photo of the
 * sender of each message it delivers; the rebuilt notification also shows earlier messages, so their
 * senders need a photo of their own. Filled, in this order of cost, from photos notifications carry,
 * from the members Instagram parses for a chat (prefetched, so a group member has a photo before
 * their first message), and on demand through Instagram's UserCache. Cleared on logout with the
 * chat avatars (same directory).
 */
final class Avatars {
    private static final int SIZE = 192;
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60_000L;
    // Memory copy of decoded photos (about 150 KB each), least recently used dropped first.
    private static final int MAX_ICONS = 64;
    private static final Map<String, Icon> icons = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Icon>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Icon> eldest) {
                    return size() > MAX_ICONS;
                }
            });
    // Users whose photo is being fetched or could not be, so each is tried once per process.
    private static final Set<String> tried = ConcurrentHashMap.newKeySet();
    private static Handler worker;

    private Avatars() {
    }

    /** Instagram's user ids are numeric; anything else never becomes a file name. */
    private static boolean valid(String userId) {
        return userId != null && userId.matches("\\d{1,30}");
    }

    static File file(Context context, String userId) {
        return new File(new File(context.getCacheDir(), ChatShortcuts.AVATAR_DIRECTORY), "u_" + userId + ".png");
    }

    /** The saved photo of a user, or null. Never touches the network. */
    static Icon icon(String userId) {
        if (!valid(userId)) return null;
        Icon known = icons.get(userId);
        if (known != null) return known;
        File file = file(Utils.getContext(), userId);
        if (!file.isFile()) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());
        if (bitmap == null) return null;
        Icon icon = Icon.createWithBitmap(bitmap);
        icons.put(userId, icon);
        return icon;
    }

    /** A photo a notification carried: saved when the user has none or an old one. */
    static void save(String userId, Icon icon) {
        if (!valid(userId) || icon == null) return;
        worker().post(() -> {
            try {
                Context context = Utils.getContext();
                File file = file(context, userId);
                if (fresh(file)) return;
                Drawable drawable = icon.loadDrawable(context);
                if (drawable == null) return;
                Bitmap bitmap;
                if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
                    bitmap = ((BitmapDrawable) drawable).getBitmap();
                } else {
                    bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
                    drawable.setBounds(0, 0, SIZE, SIZE);
                    drawable.draw(new Canvas(bitmap));
                }
                write(file, bitmap);
                icons.put(userId, Icon.createWithBitmap(square(bitmap)));
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to save a photo from a notification", ex);
            }
        });
    }

    /**
     * Downloads a user's photo once, when none is saved (or it is a week old). {@code ready} runs
     * after a new photo was saved.
     */
    static void fetch(String userId, String url, Runnable ready) {
        // Downloads follow "Show images in notifications", the switch for fetching media.
        if (!valid(userId) || TextUtils.isEmpty(url) || !url.startsWith("http") || !NotificationImages.enabled()) return;
        Context context = Utils.getContext();
        if (fresh(file(context, userId)) || !tried.add(userId)) return;
        worker().post(() -> {
            try {
                Bitmap bitmap = NotificationImages.downloadBitmap(url, SIZE);
                if (bitmap == null) return;
                File file = file(context, userId);
                write(file, bitmap);
                icons.put(userId, Icon.createWithBitmap(square(bitmap)));
                NotificationLog.event("AVATAR fetched for user " + userId);
                if (ready != null) ready.run();
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to fetch a profile photo", ex);
                NotificationLog.event("FAILED avatar for user " + userId + ": " + ex);
            }
        });
    }

    /** Called on logout, after the directory was emptied. */
    static void forget() {
        icons.clear();
        tried.clear();
    }

    private static boolean fresh(File file) {
        return file.isFile() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS;
    }

    private static Bitmap square(Bitmap source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        Bitmap crop = Bitmap.createBitmap(source, (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
        return side == SIZE ? crop : Bitmap.createScaledBitmap(crop, SIZE, SIZE, true);
    }

    private static void write(File file, Bitmap bitmap) throws Exception {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            square(bitmap).compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        if (!tmp.renameTo(file)) tmp.delete();
    }

    private static synchronized Handler worker() {
        if (worker == null) {
            HandlerThread thread = new HandlerThread("piko-avatars");
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        return worker;
    }
}
