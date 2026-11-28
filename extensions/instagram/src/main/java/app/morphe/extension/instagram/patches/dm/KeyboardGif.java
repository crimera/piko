/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.inputmethod.InputContentInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Iterator;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Sends a GIF committed from the keyboard as an animated image in a DM.
 *
 * Instagram's paste path decodes the content to a bitmap, saves it as a temp JPEG and sends that file as a photo.
 * The hooks below keep the original GIF bytes, write them to a cache file, and give the pending-media
 * factory that file to upload as animated media. The JPEG is left as Instagram saved it, so when any step fails
 * it is sent as a photo.
 */
@SuppressWarnings("unused")
public final class KeyboardGif {
    private static final String MIME_GIF = "image/gif";
    private static final String GIF_EXTENSION = ".gif";

    /** Instagram rejects larger animated uploads. */
    private static final int MAX_GIF_BYTES = 15 * 1024 * 1024;

    // A send can be dropped before or after Instagram assigns the JPEG name.
    private static final int MAX_GIF_FILES = 8;
    private static final ArrayDeque<PendingGif> PENDING = new ArrayDeque<>();

    private static final class PendingGif {
        final File file;
        final WeakReference<Bitmap> bitmap;
        String jpegName;

        PendingGif(File file, Bitmap bitmap) {
            this.file = file;
            this.bitmap = new WeakReference<>(bitmap);
        }
    }

    private KeyboardGif() {
    }

    /**
     * Injection point.
     *
     * @param isGif Whether the content committed by the keyboard is a GIF.
     * @return Whether to refuse it with Instagram's "Tap the sticker icon to find and send GIFs." toast.
     */
    public static boolean isUnsupportedGif(boolean isGif) {
        return isGif && !Pref.sendKeyboardGifs();
    }

    /**
     * Injection point, on Instagram's background thread, while the keyboard's URI permission is still held.
     *
     * @param bitmap The bitmap Instagram decoded from the content, or null if it could not decode it.
     */
    public static void capture(InputContentInfo info, Bitmap bitmap) {
        File file = null;
        try {
            if (bitmap == null || !info.getDescription().hasMimeType(MIME_GIF)) return;

            byte[] gif = read(info.getContentUri());
            if (gif == null) return;
            file = File.createTempFile("direct_temp_photo_", GIF_EXTENSION, Utils.getContext().getCacheDir());
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(gif);
            }

            File evicted = null;
            synchronized (PENDING) {
                PENDING.addLast(new PendingGif(file, bitmap));
                if (PENDING.size() > MAX_GIF_FILES) evicted = PENDING.removeFirst().file;
            }
            file = null; // The queue now owns the completed file.
            if (evicted != null) evicted.delete();
        } catch (Exception ex) {
            Logger.printException(() -> "Could not prepare the keyboard GIF, sending a still image instead", ex);
        } finally {
            if (file != null) file.delete();
        }
    }

    /**
     * Injection point, after Instagram saved the bitmap as a JPEG.
     */
    public static void afterSave(Bitmap bitmap, String name) {
        synchronized (PENDING) {
            for (PendingGif gif : PENDING) {
                if (gif.bitmap.get() == bitmap && bitmap != null) {
                    gif.jpegName = name;
                    gif.bitmap.clear();
                    return;
                }
            }
        }
    }

    /**
     * Injection point.
     *
     * @param path The photo file a pending media is being built for.
     * @return The GIF to upload in its place, once, or null if the photo did not come from a keyboard GIF.
     */
    public static String keyboardGifPath(String path) {
        if (path == null) return null;
        String name = new File(path).getName();
        synchronized (PENDING) {
            Iterator<PendingGif> entries = PENDING.iterator();
            while (entries.hasNext()) {
                PendingGif gif = entries.next();
                if (name.equals(gif.jpegName)) {
                    entries.remove();
                    return gif.file.getPath();
                }
            }
        }
        return null;
    }

    private static byte[] read(Uri uri) throws Exception {
        ContentResolver resolver = Utils.getContext().getContentResolver();
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_GIF_BYTES) {
                    Logger.printInfo(() -> "Keyboard GIF is larger than " + MAX_GIF_BYTES + " bytes, sending a still image instead");
                    return null;
                }
            }
            byte[] data = out.toByteArray();
            return isGif(data) ? data : null;
        }
    }

    /** Some keyboards label WebP or PNG stickers as GIFs. Only real GIF data goes down the animated path. */
    private static boolean isGif(byte[] data) {
        return data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8';
    }
}
