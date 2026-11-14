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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Sends a GIF committed from the keyboard as an animated image in a DM.
 *
 * Instagram's paste path decodes the content to a bitmap, saves it as a temp JPEG and sends that file as a photo.
 * The hooks below keep the original GIF bytes, write them to a file beside the JPEG, and give the pending-media
 * factory that file to upload as animated media. The JPEG is left as Instagram saved it, so when any step fails
 * it is sent as a photo.
 */
@SuppressWarnings("unused")
public final class KeyboardGif {
    private static final String MIME_GIF = "image/gif";
    private static final String GIF_EXTENSION = ".gif";

    /** Instagram rejects larger animated uploads, and the bytes are held in memory until the send starts. */
    private static final int MAX_GIF_BYTES = 15 * 1024 * 1024;

    /** GIF bytes keyed by the bitmap Instagram decoded from the same content, which is what its paste path hands on. */
    private static final Map<Bitmap, byte[]> PENDING = Collections.synchronizedMap(new WeakHashMap<>());

    /** A send can be dropped between the save and the pending media, which leaves a GIF file behind. */
    private static final int MAX_GIF_FILES = 8;

    /**
     * Paths of the GIF files written by {@link #afterSave}, oldest first, keyed by the name of the JPEG Instagram
     * saved for the same content. Consumed by {@link #keyboardGifPath}.
     * Keyed by name, not path: each has a random UUID, and Instagram may spell the directory differently later on.
     */
    private static final Map<String, String> GIF_PATHS = new LinkedHashMap<>();

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
     * Injection point, after Instagram saved the bitmap to directory/name.
     */
    public static void afterSave(Bitmap bitmap, String directory, String name) {
        byte[] gif = bitmap == null ? null : PENDING.remove(bitmap);
        if (gif == null) return;

        int extension = name.lastIndexOf('.');
        File file = new File(directory, (extension < 0 ? name : name.substring(0, extension)) + GIF_EXTENSION);
        File partial = new File(directory, file.getName() + ".part");
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

        synchronized (GIF_PATHS) {
            GIF_PATHS.put(name, file.getPath());
            Iterator<String> oldest = GIF_PATHS.values().iterator();
            while (GIF_PATHS.size() > MAX_GIF_FILES) {
                new File(oldest.next()).delete();
                oldest.remove();
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
        synchronized (GIF_PATHS) {
            return GIF_PATHS.remove(new File(path).getName());
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
            byte[] data = out.toByteArray();
            return isGif(data) ? data : null;
        }
    }

    /** Some keyboards label WebP or PNG stickers as GIFs. Only real GIF data goes down the animated path. */
    private static boolean isGif(byte[] data) {
        return data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8';
    }
}
