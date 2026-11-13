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
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Sends a GIF committed from the keyboard as an animated image in a DM.
 *
 * Instagram's paste path decodes the content to a bitmap, saves it as a temp JPEG and sends that file as a photo.
 * The hooks below keep the original GIF bytes, write them over the temp file, and tell the pending-media factory
 * which files to upload as animated media.
 */
@SuppressWarnings("unused")
public final class KeyboardGif {
    private static final String MIME_GIF = "image/gif";
    private static final String GIF_EXTENSION = ".gif";

    /** Instagram rejects larger animated uploads, and the bytes are held in memory until the send starts. */
    private static final int MAX_GIF_BYTES = 15 * 1024 * 1024;

    /** GIF bytes keyed by the bitmap Instagram decoded from the same content, which is what its paste path hands on. */
    private static final Map<Bitmap, byte[]> PENDING = Collections.synchronizedMap(new WeakHashMap<>());

    /** A send can be dropped between the save and the pending media, which leaves a name behind. */
    private static final int MAX_GIF_FILE_NAMES = 8;

    /**
     * Names of the temp files that now hold a GIF, oldest first. Consumed by {@link #isKeyboardGif}.
     * Names, not paths: each has a random UUID, and Instagram may spell the directory differently later on.
     */
    private static final Set<String> GIF_FILE_NAMES = new LinkedHashSet<>();

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
        try {
            if (bitmap == null || !info.getDescription().hasMimeType(MIME_GIF)) return;

            byte[] gif = read(info.getContentUri());
            if (gif != null) PENDING.put(bitmap, gif);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not read the keyboard GIF, sending a still image instead", ex);
        }
    }

    /**
     * Injection point. Names the temp file Instagram is about to save the bitmap to.
     */
    public static String fileExtension(Bitmap bitmap, String original) {
        return bitmap != null && PENDING.containsKey(bitmap) ? GIF_EXTENSION : original;
    }

    /**
     * Injection point, after Instagram saved the bitmap to directory/name.
     */
    public static void afterSave(Bitmap bitmap, String directory, String name) {
        byte[] gif = bitmap == null ? null : PENDING.remove(bitmap);
        if (gif == null) return;

        // Written beside the file and renamed over it, so a failed write leaves the JPEG Instagram saved,
        // which is then sent as a photo.
        File file = new File(directory, name);
        File partial = new File(directory, name + ".part");
        try {
            try (FileOutputStream out = new FileOutputStream(partial)) {
                out.write(gif);
            }
            if (!partial.renameTo(file)) throw new IOException("Could not rename " + partial);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not write the keyboard GIF, sending a still image instead", ex);
            partial.delete();
            return;
        }

        synchronized (GIF_FILE_NAMES) {
            GIF_FILE_NAMES.add(name);
            Iterator<String> oldest = GIF_FILE_NAMES.iterator();
            while (GIF_FILE_NAMES.size() > MAX_GIF_FILE_NAMES) {
                oldest.next();
                oldest.remove();
            }
        }
    }

    /**
     * Injection point. True once for a file that {@link #afterSave} filled with a GIF.
     */
    public static boolean isKeyboardGif(String path) {
        if (path == null) return false;
        synchronized (GIF_FILE_NAMES) {
            return GIF_FILE_NAMES.remove(new File(path).getName());
        }
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
            return isGif(out) ? out.toByteArray() : null;
        }
    }

    /** Some keyboards label WebP or PNG stickers as GIFs. Only real GIF data goes down the animated path. */
    private static boolean isGif(ByteArrayOutputStream data) {
        if (data.size() < 6) return false;
        byte[] header = data.toByteArray();
        return header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8';
    }
}
