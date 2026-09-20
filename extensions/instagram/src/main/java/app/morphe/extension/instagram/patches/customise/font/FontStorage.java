/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.font;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import static app.morphe.extension.instagram.utils.IgStr.str;

/**
 * The fonts the user added, kept in the app's private storage, and which of them is chosen.
 *
 * Nothing here runs while text is being drawn - {@link CustomFont} reads the choice once, when the
 * process starts, and never touches storage again.
 */
public final class FontStorage {

    /** What came of adding a font, so the caller can say which way it went wrong. */
    public enum ImportResult {
        ADDED,
        NOT_A_FONT,
        TOO_LARGE,
        FAILED,
    }

    /**
     * Stands for the device's own system font in every place a font file's name otherwise would -
     * {@link #selected()}, {@link #isSelected}, {@link #isUsable} and so on all take it as they
     * find it, with no file behind it. Chosen as the empty string on purpose: it is already what
     * an unset {@link Settings#CUSTOM_FONT_SELECTED} reads back as, so turning the feature on with
     * nothing yet chosen already lands on a real, always-usable font rather than a broken one.
     */
    public static final String SYSTEM_FONT = "";

    /**
     * The name the whole platform's own default text already renders through - resolving a
     * typeface by this name, rather than by the {@link Typeface#DEFAULT}/{@code DEFAULT_BOLD}
     * static fields, is what picks up a device's own font override: those fields stay pinned to
     * the platform's original face regardless, but a name lookup goes through whatever the
     * override put there instead.
     */
    static final String SYSTEM_FONT_FAMILY = "sans-serif";

    private static final String DIRECTORY_NAME = "piko_fonts";

    /** Extensions {@link Typeface#createFromFile} can load. */
    private static final String[] EXTENSIONS = {".ttf", ".otf", ".ttc"};

    /** Room for any real font, and far too little for a file picked by mistake. */
    private static final long MAX_SIZE_BYTES = 8L * 1024 * 1024;

    /** Room for a collision suffix and the extension well inside the filesystem's own limit. */
    private static final int MAX_BASE_NAME_LENGTH = 80;

    /** Typefaces of the added fonts, so every row of the list can be shown in its own face. */
    private static final Map<String, Typeface> previewTypefaces = new ConcurrentHashMap<>();

    /**
     * Fonts {@link #previewTypeface} has already tried and failed to parse, so a leftover file
     * that passed the header check on import but is otherwise broken is never retried - and never
     * treated as usable - on every subsequent bind or restart check.
     */
    private static final Set<String> unloadableFonts = ConcurrentHashMap.newKeySet();

    private FontStorage() {
    }

    // The chosen font.

    /**
     * Whether the custom font is switched on. Read straight from the preference rather than
     * through {@link app.morphe.extension.instagram.utils.Pref}, so that {@link CustomFont#load}
     * can call it at start up without having to run after the settings flags are loaded. A stored
     * choice can do nothing on a build without the patch: there are no hooks to read it.
     */
    public static boolean isEnabled() {
        return SharedPref.getBooleanPref(Settings.CUSTOM_FONT);
    }

    /**
     * Switches the custom font on or off. Nothing has to be chosen for this to leave the switch in
     * a working state: an unset choice already reads back as {@link #SYSTEM_FONT}, which is always
     * usable. The font is only read at start up, so {@link SettingsRestart#markChanged} queues the
     * actual restart.
     */
    public static void setEnabled(boolean enabled) {
        boolean previous = isEnabled();
        SharedPref.setBooleanPref(Settings.CUSTOM_FONT.key, enabled);
        SettingsRestart.markChanged(previous, enabled);
    }

    /** File name of the chosen font, or {@link #SYSTEM_FONT} when nothing has been chosen. */
    public static String selected() {
        String stored = SharedPref.getStringPref(Settings.CUSTOM_FONT_SELECTED);
        return stored == null ? SYSTEM_FONT : stored;
    }

    /**
     * Chooses a font. Also reached from {@link #delete}, {@link #deleteAll} and
     * {@link #importFrom} - every path that can change which face the app is drawn in, so none of
     * them has to remember to queue the restart on its own.
     */
    public static void select(String fontFileName) {
        String previous = selected();
        SharedPref.setStringPref(Settings.CUSTOM_FONT_SELECTED.key, fontFileName);
        SettingsRestart.markChanged(previous, fontFileName);
    }

    /**
     * Whether a font carries the mark in the list. The mark follows the choice even while the
     * switch is off - the section is greyed out as a whole, which is what says it is not in use.
     */
    public static boolean isSelected(String fontFileName) {
        return selected().equals(fontFileName);
    }

    /** Whether a name stands for {@link #SYSTEM_FONT} rather than a font file. */
    public static boolean isSystemFont(String fontFileName) {
        return SYSTEM_FONT.equals(fontFileName);
    }

    // The files.

    /**
     * Where the added fonts are kept. Deliberately not created here: reading the list must not
     * touch the file system any more than it has to, and only adding a font needs the directory.
     */
    static File directory() {
        return new File(Utils.getContext().getFilesDir(), DIRECTORY_NAME);
    }

    static File fileOf(String fontFileName) {
        return new File(directory(), fontFileName);
    }

    /** Whether a font is present and can actually be loaded as a font. */
    public static boolean isUsable(String fontFileName) {
        if (isSystemFont(fontFileName)) {
            return true;
        }
        File file = fileOf(fontFileName);
        if (!file.isFile() || file.length() <= 0) {
            return false;
        }
        return previewTypeface(fontFileName) != null;
    }

    /** File names of the fonts the user added, in alphabetical order. */
    public static List<String> list() {
        List<String> names = new ArrayList<>();
        try {
            File[] files = directory().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.length() > 0 && hasFontExtension(file.getName())) {
                        names.add(file.getName());
                    }
                }
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to list the custom fonts", e);
        }
        return names;
    }

    /** The name a font is shown by, which is its file name without the extension. */
    public static String displayName(String fontFileName) {
        if (isSystemFont(fontFileName)) {
            return str("piko_pref_system_font");
        }
        String extension = extensionOf(fontFileName);
        return fontFileName.substring(0, fontFileName.length() - extension.length());
    }

    /**
     * The typeface of an added font, used to show it in the list in its own face. Null when the
     * file cannot be loaded. {@link #SYSTEM_FONT} is always loadable and needs no file read.
     */
    public static Typeface previewTypeface(String fontFileName) {
        if (isSystemFont(fontFileName)) {
            return Typeface.create(SYSTEM_FONT_FAMILY, Typeface.NORMAL);
        }
        Typeface cached = previewTypefaces.get(fontFileName);
        if (cached != null) {
            return cached;
        }
        if (unloadableFonts.contains(fontFileName)) {
            return null;
        }
        try {
            Typeface typeface = Typeface.createFromFile(fileOf(fontFileName));
            previewTypefaces.put(fontFileName, typeface);
            return typeface;
        } catch (Exception e) {
            unloadableFonts.add(fontFileName);
            Logger.printException(() -> "Failed to load the font " + fontFileName, e);
            return null;
        }
    }

    /**
     * Loads every added font's preview typeface on a background thread, so scrolling through a
     * long list does not stall on a disk read the first time each row comes into view.
     */
    public static void warmPreviewCache(List<String> fontFileNames) {
        new Thread(() -> {
            for (String fontFileName : fontFileNames) {
                previewTypeface(fontFileName);
            }
        }).start();
    }

    /**
     * Deletes an added font. When it was the chosen one, {@link #SYSTEM_FONT} is chosen in its
     * place - never another font from the list, which the user never asked for and did not
     * necessarily even see. The font the app is currently drawn in stays loaded until the app
     * restarts.
     */
    public static boolean delete(String fontFileName) {
        if (isSystemFont(fontFileName)) {
            // Not a file, and never offered for delete - a guard against ever writing to
            // directory() itself through fileOf("").
            return false;
        }
        try {
            File file = fileOf(fontFileName);
            if (file.exists() && !file.delete()) {
                // The file is still there and still the one on record; leaving it selected is
                // more honest than switching away from a font that was never actually removed.
                return false;
            }

            previewTypefaces.remove(fontFileName);
            unloadableFonts.remove(fontFileName);
            if (isSelected(fontFileName)) {
                select(SYSTEM_FONT);
            }
            return true;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to delete a custom font", e);
            return false;
        }
    }

    /**
     * Removes every added font and falls back to {@link #SYSTEM_FONT}. Used when piko's settings
     * are reset from scratch, so a font the reset was meant to clear cannot silently come back the
     * next time the switch is turned back on.
     */
    public static boolean deleteAll() {
        try {
            boolean allDeleted = true;
            for (String fontFileName : list()) {
                File file = fileOf(fontFileName);
                if (file.exists() && !file.delete()) {
                    allDeleted = false;
                }
                previewTypefaces.remove(fontFileName);
                unloadableFonts.remove(fontFileName);
            }
            select(SYSTEM_FONT);
            return allDeleted;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to delete the custom fonts", e);
            return false;
        }
    }

    /**
     * Copies a picked file into the fonts directory and chooses it. The file is read once: its
     * first bytes decide whether it is a font at all, and the same stream then carries the rest.
     *
     * A file that turns out not to be a font, or to be far larger than any font, leaves nothing
     * behind - a half written font would otherwise sit in the list as one that cannot be loaded.
     */
    public static ImportResult importFrom(Context context, Uri uri) {
        File destination = null;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return ImportResult.FAILED;
                }

                byte[] header = new byte[4];
                if (!readFully(in, header) || !isFontHeader(header)) {
                    return ImportResult.NOT_A_FONT;
                }

                destination = resolveNewFile(pickedName(context, uri));
                try (OutputStream out = new FileOutputStream(destination)) {
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

            // Shown by whatever name the font calls itself, when it names itself at all - the
            // picked file's name is often just a generic download name that says nothing about
            // which font it actually is.
            destination = preferRealFontName(destination);

            // A font is added to be used, so it becomes the chosen one.
            select(destination.getName());
            destination = null;
            return ImportResult.ADDED;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to add a custom font", e);
            return ImportResult.FAILED;
        } finally {
            if (destination != null && destination.exists() && !destination.delete()) {
                final File left = destination;
                Logger.printException(() -> "Failed to remove a partly written font " + left);
            }
        }
    }

    /**
     * Turns the name of a picked file into the file the font is stored as, keeping the name the
     * user knows it by and never overwriting a font they already added.
     */
    private static File resolveNewFile(String pickedFileName) throws IOException {
        File directory = directory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }

        String name = sanitize(pickedFileName);
        String extension = extensionOf(name);
        String base = capLength(name.substring(0, name.length() - extension.length()));
        return uniqueFile(directory, base, extension, null);
    }

    /**
     * Renames a freshly imported font to the name it calls itself in its own {@code name} table -
     * the way a file manager showing a font's real name would read it - keeping the extension it
     * already has. Left as it is when that name cannot be read or sanitises down to nothing, so a
     * font with no readable name of its own is still kept under the name it was picked by.
     */
    private static File preferRealFontName(File file) {
        String realName = readFontName(file);
        if (realName == null) {
            return file;
        }

        String base = capLength(sanitizeCharacters(realName));
        if (base.isEmpty() || base.equals("font")) {
            return file;
        }

        // Excludes `file` itself from the collision check - otherwise a font already named after
        // itself reads back as a collision and gets a suffix it never needed.
        File renamed = uniqueFile(file.getParentFile(), base, extensionOf(file.getName()), file);
        return renamed.equals(file) || file.renameTo(renamed) ? renamed : file;
    }

    private static String capLength(String base) {
        return base.length() > MAX_BASE_NAME_LENGTH ? base.substring(0, MAX_BASE_NAME_LENGTH) : base;
    }

    /**
     * The first name in `directory` for `base+extension` that nothing but `ignore` already uses -
     * `ignore` being the file this name is being chosen for, when it may already occupy it.
     */
    private static File uniqueFile(File directory, String base, String extension, File ignore) {
        File file = new File(directory, base + extension);
        for (int suffix = 1; file.exists() && !file.equals(ignore); suffix++) {
            file = new File(directory, base + " (" + suffix + ")" + extension);
        }
        return file;
    }

    /** The name the picked file is shown by in the document picker. */
    private static String pickedName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    return name;
                }
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to read the picked file name", e);
        }
        return uri.getLastPathSegment();
    }

    /**
     * Strips everything that could take the written file outside the fonts directory, and gives a
     * file that does not name itself a font a name that does. Letters and digits of any script are
     * kept, so a font named in Cyrillic or Japanese keeps the name the user knows it by.
     */
    private static String sanitize(String pickedFileName) {
        String name = sanitizeCharacters(pickedFileName);
        return hasFontExtension(name) ? name : name + EXTENSIONS[0];
    }

    /**
     * The character-safety half of {@link #sanitize}, on its own for a name that is not a picked
     * file's name and so must not have a font extension forced onto it - the font's own name, read
     * from its {@code name} table, which never ends in one to begin with.
     */
    private static String sanitizeCharacters(String rawName) {
        String name = rawName == null ? "" : rawName.trim();

        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        name = name.replaceAll("[^\\p{L}\\p{N} .()_-]", "_").trim();

        return name.isEmpty() || name.equals(".") || name.equals("..") ? "font" : name;
    }

    private static boolean hasFontExtension(String fileName) {
        return !extensionOf(fileName).isEmpty();
    }

    private static String extensionOf(String fileName) {
        String lowerCase = fileName.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (lowerCase.endsWith(extension)) {
                return fileName.substring(fileName.length() - extension.length());
            }
        }
        return "";
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

    // The font's own name, read out of its sfnt `name` table - the same metadata a file manager
    // or font viewer reads to show a font by what it actually calls itself.

    private static final int NAME_ID_FAMILY = 1;
    private static final int NAME_ID_FULL = 4;

    /**
     * The font's own preferred name, or null when the file is too unusual to make sense of - a
     * missing or malformed `name` table, or a font collection this cannot parse. Every step here
     * only ever reads what {@link #isFontHeader} already confirmed is a real font file, so a
     * failure here is a reason to fall back to the picked file's name, never to fail the import.
     */
    private static String readFontName(File file) {
        try {
            byte[] data = readAll(file);
            int offset = sfntOffset(data);
            if (!fitsWithin(data, offset, 12)) {
                return null;
            }

            int numTables = readUInt16(data, offset + 4);
            int nameTableOffset = -1;
            for (int i = 0; i < numTables; i++) {
                int record = offset + 12 + i * 16;
                if (record + 16 > data.length) {
                    break;
                }
                if (data[record] == 'n' && data[record + 1] == 'a'
                        && data[record + 2] == 'm' && data[record + 3] == 'e') {
                    nameTableOffset = readInt32(data, record + 8);
                    break;
                }
            }
            if (!fitsWithin(data, nameTableOffset, 6)) {
                return null;
            }

            return readNameFromTable(data, nameTableOffset);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to read the font's own name from " + file.getName(), e);
            return null;
        }
    }

    /**
     * Whether a {@code length}-byte span at {@code offset} lies inside {@code data}. Widened to
     * {@code long} for the addition: {@code offset} is a 32-bit value read straight out of the
     * font file, and an {@code int} check of {@code offset + length > data.length} can overflow
     * and wrap negative for a huge offset, letting an out-of-range span slip past the check.
     */
    private static boolean fitsWithin(byte[] data, int offset, int length) {
        return offset >= 0 && (long) offset + length <= data.length;
    }

    /** Where the sfnt table directory starts: the file itself, or a collection's first font. */
    private static int sfntOffset(byte[] data) {
        if (data.length < 16) {
            return -1;
        }
        String magic = new String(data, 0, 4, StandardCharsets.ISO_8859_1);
        return magic.equals("ttcf") ? readInt32(data, 12) : 0;
    }

    private static String readNameFromTable(byte[] data, int nameTableOffset) {
        int format = readUInt16(data, nameTableOffset);
        if (format != 0 && format != 1) {
            return null;
        }
        int count = readUInt16(data, nameTableOffset + 2);
        int storageOffset = nameTableOffset + readUInt16(data, nameTableOffset + 4);

        String bestFull = null, anyFull = null, bestFamily = null, anyFamily = null;
        for (int i = 0; i < count; i++) {
            int record = nameTableOffset + 6 + i * 12;
            if (record + 12 > data.length) {
                break;
            }

            int platformId = readUInt16(data, record);
            int languageId = readUInt16(data, record + 4);
            int nameId = readUInt16(data, record + 6);
            if (nameId != NAME_ID_FULL && nameId != NAME_ID_FAMILY) {
                continue;
            }

            int length = readUInt16(data, record + 8);
            int stringOffset = storageOffset + readUInt16(data, record + 10);
            if (stringOffset < 0 || stringOffset + length > data.length) {
                continue;
            }

            String value = decodeNameRecord(data, stringOffset, length, platformId);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            value = value.trim();

            // Windows platform + US English, or Macintosh platform + English - the two "give me
            // the name in a language I can read" cases every font maker fills in.
            boolean english = (platformId == 3 && languageId == 0x0409)
                    || (platformId == 1 && languageId == 0);
            if (nameId == NAME_ID_FULL) {
                anyFull = value;
                if (english) bestFull = value;
            } else {
                anyFamily = value;
                if (english) bestFamily = value;
            }
        }

        // Prefer the full name (e.g. "Roboto Condensed Bold") over the family name, so weight/style
        // the font itself picked is not lost.
        if (bestFull != null) return bestFull;
        if (anyFull != null) return anyFull;
        return bestFamily != null ? bestFamily : anyFamily;
    }

    /** Macintosh platform records are (near enough) Latin-1; everything else is UTF-16BE. */
    private static String decodeNameRecord(byte[] data, int offset, int length, int platformId) {
        try {
            return new String(data, offset, length,
                    platformId == 1 ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_16BE);
        } catch (Exception e) {
            return null;
        }
    }

    private static int readUInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int readInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static byte[] readAll(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (InputStream in = new FileInputStream(file)) {
            if (!readFully(in, data)) {
                throw new IOException("Could not read " + file);
            }
        }
        return data;
    }
}
