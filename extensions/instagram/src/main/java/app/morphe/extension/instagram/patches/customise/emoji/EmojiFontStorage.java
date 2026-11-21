/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.emoji;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import app.morphe.extension.instagram.patches.customise.font.FontStorage.DeleteResult;
import app.morphe.extension.instagram.patches.customise.font.FontStorage.ImportResult;
import app.morphe.extension.instagram.settings.RestartPolicy;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class EmojiFontStorage {
    private static final String FILE_NAME = "piko_emoji_font.ttf";
    private static final long MAX_SIZE_BYTES = 300L * 1024 * 1024;
    private static final String HASH_ATTRIBUTE = "user.piko.emoji.sha256";

    private EmojiFontStorage() {}

    private static File file() {
        return new File(Utils.getContext().getFilesDir(), FILE_NAME);
    }

    static final class LoadedFont {
        final Typeface typeface;
        final String suffix;

        LoadedFont(Typeface typeface, String hash) {
            this.typeface = typeface;
            this.suffix = "-piko-" + hash;
        }
    }

    static synchronized LoadedFont load() throws Exception {
        File file = file();
        if (!file.isFile()) return null;

        try (FileInputStream input = new FileInputStream(file)) {
            String hash = storedFingerprint(file);
            if (hash != null) {
                // Imports replace the path by rename. Only use its attribute for the open inode.
                StructStat opened = Os.fstat(input.getFD());
                try {
                    StructStat current = Os.stat(file.getPath());
                    if (opened.st_dev != current.st_dev || opened.st_ino != current.st_ino) hash = null;
                } catch (ErrnoException removedDuringLoad) {
                    hash = null;
                }
            }
            // Older files or filesystems without attributes can still cache images within this run.
            if (hash == null) hash = UUID.randomUUID().toString();
            Typeface typeface = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    FileChannel channel = input.getChannel();
                    ByteBuffer bytes = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
                    android.graphics.fonts.Font font = new android.graphics.fonts.Font.Builder(bytes).build();
                    android.graphics.fonts.FontFamily family = new android.graphics.fonts.FontFamily.Builder(font).build();
                    // A plain Typeface loses to Android's emoji fallback inside a span.
                    typeface = new Typeface.CustomFallbackBuilder(family).setSystemFallback("sans-serif").build();
                } catch (Exception e) {
                    Logger.printException(() -> "Could not build the emoji fallback family", e);
                }
            }
            if (typeface == null) typeface = new Typeface.Builder(input.getFD()).build();
            return typeface == null ? null : new LoadedFont(typeface, hash);
        }
    }

    public static synchronized DeleteResult delete() {
        File file = file();
        if (!file.exists()) return DeleteResult.NOT_FOUND;
        try {
            String previous = fingerprint(file);
            if (!file.delete()) return DeleteResult.FAILED;
            SettingsRestart.markChanged(FILE_NAME, RestartPolicy.REQUIRED, previous, null);
            return DeleteResult.DELETED;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to delete the emoji font", e);
            return DeleteResult.FAILED;
        }
    }

    public static ImportResult importFrom(Context context, Uri uri) {
        File destination = file();
        File temp = null;
        try {
            temp = File.createTempFile(FILE_NAME, ".part", destination.getParentFile());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (in == null) return ImportResult.FAILED;
                byte[] buffer = new byte[64 * 1024];
                long written = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    written += count;
                    if (written > MAX_SIZE_BYTES) return ImportResult.TOO_LARGE;
                    digest.update(buffer, 0, count);
                    out.write(buffer, 0, count);
                }
            }
            if (new Typeface.Builder(temp).build() == null) return ImportResult.NOT_A_FONT;
            byte[] hash = digest.digest();
            try {
                // Store the digest on the file so a rename commits the font and its cache key together.
                Os.setxattr(temp.getPath(), HASH_ATTRIBUTE, hash, 0);
            } catch (ErrnoException ignored) {
                // Without attributes, loading uses a process-local image cache key.
            }
            synchronized (EmojiFontStorage.class) {
                String previous = destination.isFile() ? fingerprint(destination) : null;
                if (!temp.renameTo(destination)) throw new IOException("Could not replace the emoji font");
                SettingsRestart.markChanged(FILE_NAME, RestartPolicy.REQUIRED, previous, encode(hash));
            }
            return ImportResult.ADDED;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to import the emoji font", e);
            return ImportResult.FAILED;
        } finally {
            if (temp != null && temp.exists()) temp.delete();
        }
    }

    private static String storedFingerprint(File file) {
        try {
            byte[] hash = Os.getxattr(file.getPath(), HASH_ATTRIBUTE);
            if (hash.length == 32) return encode(hash);
        } catch (ErrnoException ignored) {
            // Also covers fonts imported before a digest was stored.
        }
        return null;
    }

    private static String fingerprint(File file) throws IOException, NoSuchAlgorithmException {
        String stored = storedFingerprint(file);
        if (stored != null) return stored;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return encode(digest.digest());
    }

    private static String encode(byte[] hash) {
        return Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
