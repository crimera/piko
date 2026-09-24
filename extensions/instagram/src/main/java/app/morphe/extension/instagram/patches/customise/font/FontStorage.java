/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.font;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * The one font the user added, kept in the app's private storage, and whether the switch that
 * falls back to the system font is on.
 *
 * Nothing here runs while text is being drawn - {@link CustomFont} reads the choice once, when
 * the process starts, and never touches storage again.
 */
public final class FontStorage {

    /** What came of adding a font, so the caller can say which way it went wrong. */
    public enum ImportResult {
        ADDED,
        NOT_A_FONT,
        TOO_LARGE,
        FAILED,
    }

    /** What came of deleting the font. */
    public enum DeleteResult {
        DELETED,
        NOT_FOUND,
        FAILED,
    }

    /**
     * The name the whole platform's own default text already renders through - resolving a
     * typeface by this name, rather than by the {@link Typeface#DEFAULT}/{@code DEFAULT_BOLD}
     * static fields, is what picks up a device's own font override: those fields stay pinned to
     * the platform's original face regardless, but a name lookup goes through whatever the
     * override put there instead.
     */
    static final String SYSTEM_FONT_FAMILY = "sans-serif";

    /** Only one font can ever be stored, so there is nothing to name it but this. */
    private static final String FILE_NAME = "custom_font.ttf";

    /** Room for any real font, and far too little for a file picked by mistake. */
    private static final long MAX_SIZE_BYTES = 8L * 1024 * 1024;

    private FontStorage() {
    }

    // The switch.

    /**
     * Whether the switch that falls back to the system font is on. Read straight from the
     * preference rather than through {@link app.morphe.extension.instagram.utils.Pref}, so that
     * {@link CustomFont#load} can call it at start up without having to run after the settings
     * flags are loaded. Meaningless while a font is stored - see the class-level note on
     * {@link CustomFont#load} for why a stored file always wins over this switch regardless.
     */
    public static boolean useSystemFont() {
        return SharedPref.getBooleanPref(Settings.USE_SYSTEM_FONT);
    }

    /**
     * Switches the system-font fallback on or off. The font is only read at start up, so
     * {@link SettingsRestart#markChanged} queues the actual restart.
     */
    public static void setUseSystemFont(boolean enabled) {
        boolean previous = useSystemFont();
        SharedPref.setBooleanPref(Settings.USE_SYSTEM_FONT.key, enabled);
        SettingsRestart.markChanged(previous, enabled);
    }

    // The file.

    private static File file() {
        return new File(Utils.getContext().getFilesDir(), FILE_NAME);
    }

    /** Whether a font has been added. */
    public static boolean hasFile() {
        File file = file();
        return file.isFile() && file.length() > 0;
    }

    /** The stored font, parsed fresh. Null when there is none, or it can no longer be loaded. */
    public static Typeface loadTypeface() {
        if (!hasFile()) {
            return null;
        }
        try {
            return Typeface.createFromFile(file());
        } catch (Exception e) {
            Logger.printException(() -> "Failed to load the custom font", e);
            return null;
        }
    }

    /**
     * Deletes the stored font. The font the app is currently drawn in stays loaded until the app
     * restarts.
     */
    public static DeleteResult delete() {
        File file = file();
        if (!file.exists()) {
            return DeleteResult.NOT_FOUND;
        }
        try {
            if (!file.delete()) {
                return DeleteResult.FAILED;
            }
            SettingsRestart.markChanged(true, false);
            return DeleteResult.DELETED;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to delete the custom font", e);
            return DeleteResult.FAILED;
        }
    }

    /**
     * Copies a picked file over the stored font. The file is read once: its first bytes decide
     * whether it is a font at all, and the same stream then carries the rest.
     *
     * Written to a temporary file first and only moved into place once it is fully and validly
     * written, so a file that turns out not to be a font, or an import interrupted partway
     * through, can never leave a previously working font half overwritten.
     */
    public static ImportResult importFrom(Context context, Uri uri) {
        File destination = file();
        File temp = new File(destination.getParentFile(), FILE_NAME + ".tmp");
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return ImportResult.FAILED;
                }

                byte[] header = new byte[4];
                if (!readFully(in, header) || !isFontHeader(header)) {
                    return ImportResult.NOT_A_FONT;
                }

                try (OutputStream out = new FileOutputStream(temp)) {
                    out.write(header);

                    long written = header.length;
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        written += read;
                        if (written > MAX_SIZE_BYTES) {
                            return ImportResult.TOO_LARGE;
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }

            if (!isValidFont(temp)) {
                return ImportResult.NOT_A_FONT;
            }

            if (!temp.renameTo(destination)) {
                throw new IOException("Could not move the imported font into place");
            }
            // Unconditional, not the real previous/next: a replaced font's content can differ even
            // when a font was already stored both before and after, so this always queues a restart.
            SettingsRestart.markChanged(false, true);
            return ImportResult.ADDED;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to add a custom font", e);
            return ImportResult.FAILED;
        } finally {
            if (temp.exists() && !temp.delete()) {
                Logger.printException(() -> "Failed to remove a partly written font");
            }
        }
    }

    /** A single read may hand back less than was asked for, even from a file that has it all. */
    private static boolean readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read == -1) {
                return false;
            }
            total += read;
        }
        return true;
    }

    /** The magic bytes of the font formats Android can load from a file. */
    private static boolean isFontHeader(byte[] header) {
        // OpenType (CFF), Apple TrueType, TrueType collection.
        String magic = new String(header, 0, 4, StandardCharsets.ISO_8859_1);
        if (magic.equals("OTTO") || magic.equals("true") || magic.equals("ttcf")) {
            return true;
        }

        // TrueType.
        return header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00;
    }

    /**
     * Whether the file holds a real sfnt table directory, with every table's offset and length
     * inside the file - checked instead of trusting {@link Typeface#createFromFile}, which can
     * silently return the default typeface for a file that fails to parse.
     */
    private static boolean isValidFont(File file) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long length = in.length();
            if (length < 12) {
                return false;
            }

            byte[] header = new byte[4];
            in.readFully(header);
            long tableDirectory = 0;

            if (new String(header, 0, 4, StandardCharsets.ISO_8859_1).equals("ttcf")) {
                // A TrueType collection points at its first font's own table directory.
                if (length < 16) {
                    return false;
                }
                in.seek(8);
                if (readUnsignedInt(in) < 1) {
                    return false;
                }
                tableDirectory = readUnsignedInt(in);
            }

            if (tableDirectory + 12 > length) {
                return false;
            }
            in.seek(tableDirectory + 4);
            int numTables = in.readUnsignedShort();
            if (numTables < 1 || numTables > 64) {
                return false;
            }

            long directoryEnd = tableDirectory + 12 + (long) numTables * 16;
            if (directoryEnd > length) {
                return false;
            }

            in.seek(tableDirectory + 12);
            for (int i = 0; i < numTables; i++) {
                in.skipBytes(8); // Tag and checksum.
                long tableOffset = readUnsignedInt(in);
                long tableLength = readUnsignedInt(in);
                if (tableOffset + tableLength > length) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static long readUnsignedInt(RandomAccessFile in) throws IOException {
        return ((long) in.readUnsignedByte() << 24)
                | (in.readUnsignedByte() << 16)
                | (in.readUnsignedByte() << 8)
                | in.readUnsignedByte();
    }
}
