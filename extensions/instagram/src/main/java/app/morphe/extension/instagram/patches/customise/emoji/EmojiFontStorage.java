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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import app.morphe.extension.instagram.settings.RestartPolicy;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * The emoji font: one file in the app's private storage, downloaded from a {@link Source} or imported
 * from a file the user picks, plus a small file naming where it came from. Both go to a temporary
 * file that only replaces the current font once it is complete and loads as a font, so a failed or
 * cancelled download or import leaves the old one.
 */
public final class EmojiFontStorage {

    private EmojiFontStorage() {
    }

    /** Fonts that can be downloaded. All are CBDT colour bitmap fonts, which Android can draw. */
    public enum Source {
        NOTO("Google Noto", 11,
                "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/2D/fonts/NotoColorEmoji.ttf"),
        TWEMOJI("Twemoji", 7,
                "https://github.com/MawCeron/twemoji-cbdt/releases/latest/download/TwemojiCBDT.ttf"),
        FLUENT("Microsoft Fluent", 25,
                "https://raw.githubusercontent.com/Magisk-Modules-Alt-Repo/FluentEmojiMagisk/main/system/fonts/fluent.ttf");

        public final String label;
        public final int approximateMegabytes;
        public final String url;

        Source(String label, int approximateMegabytes, String url) {
            this.label = label;
            this.approximateMegabytes = approximateMegabytes;
            this.url = url;
        }
    }

    public interface Progress {
        /** Bytes so far, and the total or -1 when the server did not say. */
        void onProgress(long done, long total);

        boolean isCancelled();
    }

    public enum Result {DONE, CANCELLED, NOT_A_FONT, TOO_LARGE, FAILED}

    /** What the source file says for a font imported from storage. */
    private static final String IMPORTED = "IMPORTED";

    private static final String FILE_NAME = "piko_emoji_font.ttf";
    private static final String SOURCE_FILE_NAME = "piko_emoji_font.source";
    private static final String RESTART_KEY = "piko_emoji_font";
    private static final long MAX_SIZE_BYTES = 300L * 1024 * 1024;

    private static File dir() {
        return Utils.getContext().getFilesDir();
    }

    private static File file() {
        return new File(dir(), FILE_NAME);
    }

    private static File sourceFile() {
        return new File(dir(), SOURCE_FILE_NAME);
    }

    public static boolean hasFont() {
        File file = file();
        return file.isFile() && file.length() > 0;
    }

    /** The source of the stored font, or null when there is none or it was imported from a file. */
    public static Source current() {
        try {
            return Source.valueOf(currentName());
        } catch (Exception e) {
            return null;
        }
    }

    /** Whether the stored font was imported from a file rather than downloaded. */
    public static boolean isImported() {
        return IMPORTED.equals(currentName());
    }

    private static String currentName() {
        if (!hasFont()) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(sourceFile().toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The font as a custom fallback family. Minikin always picks a custom fallback family that has
     * the glyph; a plain typeface loses emoji to the system emoji font, which is tagged as an emoji
     * font (und-Zsye) and wins font selection for emoji even inside a span. Custom fallback families
     * need Android 10; Android 9 gets the plain typeface, which works where no system emoji font wins.
     */
    static Typeface loadTypeface() {
        if (!hasFont()) {
            return null;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return parse(file());
        }
        try {
            android.graphics.fonts.Font font = new android.graphics.fonts.Font.Builder(file()).build();
            android.graphics.fonts.FontFamily family = new android.graphics.fonts.FontFamily.Builder(font).build();
            return new Typeface.CustomFallbackBuilder(family).setSystemFallback("sans-serif").build();
        } catch (Exception e) {
            Logger.printException(() -> "Failed to load the emoji font as a fallback family", e);
            return parse(file());
        }
    }

    /** Removes the stored font; the system emoji are back after a restart. */
    public static boolean delete() {
        String previous = currentName();
        boolean deleted = !file().exists() || file().delete();
        sourceFile().delete();
        if (deleted) {
            SettingsRestart.markChanged(RESTART_KEY, RestartPolicy.REQUIRED, previous, null);
        }
        return deleted;
    }

    /** Downloads a font, blocking; call off the main thread. */
    public static Result download(Source source, Progress progress) {
        HttpURLConnection connection = null;
        try {
            connection = open(source.url);
            long total = connection.getContentLengthLong();
            if (total > MAX_SIZE_BYTES) {
                return Result.TOO_LARGE;
            }
            try (InputStream in = connection.getInputStream()) {
                return install(in, total, progress, source.name());
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to download the emoji font " + source.name(), e);
            return Result.FAILED;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Imports a font file the user picked, blocking; call off the main thread. */
    public static Result importFrom(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return Result.FAILED;
            }
            return install(in, -1, null, IMPORTED);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to import the emoji font", e);
            return Result.FAILED;
        }
    }

    /**
     * Copies a font into place: to a temporary file first, which replaces the stored font only
     * once it is complete and loads as a font.
     */
    private static Result install(InputStream in, long total, Progress progress, String sourceName)
            throws IOException {
        File temp = new File(dir(), FILE_NAME + ".part");
        try {
            try (OutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0;
                long lastReport = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (progress != null && progress.isCancelled()) {
                        return Result.CANCELLED;
                    }
                    out.write(buffer, 0, read);
                    done += read;
                    if (done > MAX_SIZE_BYTES) {
                        return Result.TOO_LARGE;
                    }
                    if (progress != null && done - lastReport > 256 * 1024) {
                        lastReport = done;
                        progress.onProgress(done, total);
                    }
                }
                if (progress != null) {
                    progress.onProgress(done, total);
                }
                if (total > 0 && done != total) {
                    throw new IOException("Download ended early: " + done + " of " + total);
                }
            }

            if (!isFontHeader(temp) || parse(temp) == null) {
                return Result.NOT_A_FONT;
            }

            String previous = currentName();
            if (!temp.renameTo(file())) {
                throw new IOException("Could not move the emoji font into place");
            }
            try (OutputStream out = new FileOutputStream(sourceFile())) {
                out.write(sourceName.getBytes(StandardCharsets.UTF_8));
            }
            // An imported file can replace another one, so it never counts as unchanged.
            SettingsRestart.markChanged(RESTART_KEY, RestartPolicy.REQUIRED, previous,
                    IMPORTED.equals(sourceName) ? IMPORTED + System.currentTimeMillis() : sourceName);
            return Result.DONE;
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    /** Opens the URL, following redirects by hand: GitHub release downloads redirect to another host. */
    private static HttpURLConnection open(String url) throws IOException {
        for (int hops = 0; hops < 6; hops++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "piko");
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) {
                    throw new IOException("Redirect without a location");
                }
                url = new URL(new URL(url), location).toString();
                continue;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new IOException("HTTP " + code + " for " + url);
            }
            return connection;
        }
        throw new IOException("Too many redirects");
    }

    private static boolean isFontHeader(File file) {
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            if (in.read(header) != 4) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        String magic = new String(header, 0, 4, StandardCharsets.ISO_8859_1);
        return magic.equals("OTTO") || magic.equals("true") || magic.equals("ttcf")
                || (header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00);
    }

    private static Typeface parse(File file) {
        try {
            return new Typeface.Builder(file).build();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
