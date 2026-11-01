/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.chatColors;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.utils.HexColor;
import app.morphe.extension.shared.Logger;

/** The picture chosen as the background of the chats, cut to the shape of the screen and shrunk when it is added. */
public final class ChatBackgroundImage {

    public enum ImportResult {
        ADDED,
        NOT_AN_IMAGE,
        FAILED,
    }

    public enum RemoveResult {
        REMOVED,
        NOT_FOUND,
        FAILED,
    }

    private static final String PREFIX = "piko_chat_image_";
    private static final Pattern NAME = Pattern.compile(PREFIX + "[0-9a-f]+\\.jpg");
    private static final int MAX_WIDTH = 1080;
    private static final int JPEG_QUALITY = 90;
    private static final long MAX_DECODED_PIXELS = 20_000_000L;
    private static final int TONE_SAMPLE = 16;

    private ChatBackgroundImage() { }

    // The setting can come from an imported backup, so its name is not trusted.
    static boolean isName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    static File fileOf(Context context, String name) {
        if (!isName(name)) return null;
        File file = new File(context.getFilesDir(), name);
        return file.isFile() ? file : null;
    }

    static String readableOn(File image) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = TONE_SAMPLE;
        Bitmap bitmap = BitmapFactory.decodeFile(image.getPath(), options);
        if (bitmap == null) return "FFFFFF";
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        bitmap.recycle();
        double total = 0;
        for (int pixel : pixels) total += HexColor.brightness(pixel);
        return HexColor.readableText(total / pixels.length);
    }

    /** Returns false when a file could not be deleted. */
    static boolean deleteWithPrefix(File directory, String prefix, File... keep) {
        File[] files = directory.listFiles((parent, name) -> name.startsWith(prefix));
        if (files == null) return true;
        List<File> kept = Arrays.asList(keep);
        boolean deleted = true;
        for (File file : files) {
            if (!kept.contains(file)) deleted &= file.delete();
        }
        return deleted;
    }

    static void deleteFiles(Context context) {
        deleteWithPrefix(context.getFilesDir(), PREFIX);
    }

    /** Meant to be called off the main thread. */
    public static synchronized ImportResult importFrom(Context context, Uri uri) {
        File directory = context.getFilesDir();
        File temporary = new File(directory, PREFIX + "new.tmp");
        try {
            int orientation = orientationOf(context, uri);
            int[] screen = screenSize(context);
            Bitmap middle = decodeMiddle(context, uri, orientation, screen);
            if (middle == null) return ImportResult.NOT_AN_IMAGE;

            Bitmap background = shapedLikeScreen(middle, orientation, screen[0], screen[1]);
            boolean written;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                written = background.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output);
            }
            background.recycle();
            // A new name each time, so the colors that use it are told apart from the ones for the old picture.
            File picture = new File(directory, PREFIX + Long.toHexString(System.currentTimeMillis()) + ".jpg");
            if (!written || !temporary.renameTo(picture)) return ImportResult.FAILED;
            if (!SharedPref.setStringPref(Settings.CHAT_COLORS_BACKGROUND_IMAGE.key, picture.getName())) {
                picture.delete();
                return ImportResult.FAILED;
            }
            // The old picture goes last, so a failure above leaves the one that was there.
            deleteWithPrefix(directory, PREFIX, picture);
            return ImportResult.ADDED;
        } catch (Throwable throwable) {
            Logger.printException(() -> "Failed adding the chat background image", throwable);
            return ImportResult.FAILED;
        } finally {
            temporary.delete();
        }
    }

    public static synchronized RemoveResult remove(Context context) {
        try {
            File directory = context.getFilesDir();
            String[] pictures = directory.list((parent, name) -> name.startsWith(PREFIX));
            boolean found = pictures != null && pictures.length > 0;
            if (!deleteWithPrefix(directory, PREFIX)
                    || !SharedPref.setStringPref(Settings.CHAT_COLORS_BACKGROUND_IMAGE.key, "")) {
                return RemoveResult.FAILED;
            }
            return found ? RemoveResult.REMOVED : RemoveResult.NOT_FOUND;
        } catch (Throwable throwable) {
            Logger.printException(() -> "Failed removing the chat background image", throwable);
            return RemoveResult.FAILED;
        }
    }

    private static int orientationOf(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return ExifInterface.ORIENTATION_NORMAL;
            return new ExifInterface(input)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException exception) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static int[] turnedSize(int orientation, int width, int height) {
        boolean quarterTurn = orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_ROTATE_270
                || orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE;
        return quarterTurn ? new int[]{height, width} : new int[]{width, height};
    }

    private static Bitmap rotated(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.postScale(-1, 1);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static int[] screenSize(Context context) {
        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int shortSide = Math.min(screen.widthPixels, screen.heightPixels);
        int longSide = Math.max(screen.widthPixels, screen.heightPixels);
        int width = Math.min(MAX_WIDTH, shortSide);
        return new int[]{width, Math.round(width * (float) longSide / shortSide)};
    }

    private static int[] cropSize(int width, int height, int targetWidth, int targetHeight) {
        float wanted = (float) targetWidth / targetHeight;
        return new int[]{
                Math.max(1, Math.min(width, Math.round(height * wanted))),
                Math.max(1, Math.min(height, Math.round(width / wanted))),
        };
    }

    /** Only the middle part is decoded where the format allows it, as a photo is large. Null when not a picture. */
    private static Bitmap decodeMiddle(Context context, Uri uri, int orientation, int[] screen) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open " + uri);
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int[] turned = turnedSize(orientation, bounds.outWidth, bounds.outHeight);
        int[] kept = cropSize(turned[0], turned[1], screen[0], screen[1]);
        int[] stored = turnedSize(orientation, kept[0], kept[1]);
        // Samples down as far as the screen size allows.
        int sample = 1;
        while (kept[0] / (sample * 2) >= screen[0] && kept[1] / (sample * 2) >= screen[1]) sample *= 2;
        while ((long) (stored[0] / sample) * (stored[1] / sample) > MAX_DECODED_PIXELS) sample *= 2;

        int left = (bounds.outWidth - stored[0]) / 2;
        int top = (bounds.outHeight - stored[1]) / 2;
        Bitmap region = decodeRegion(context, uri, new Rect(left, top, left + stored[0], top + stored[1]), sample);
        if (region != null) return region;

        // Formats that cannot be read in parts are decoded whole, which takes more memory.
        while ((long) (bounds.outWidth / sample) * (bounds.outHeight / sample) > MAX_DECODED_PIXELS) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap decoded;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open " + uri);
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        return decoded == null ? null : cutMiddle(decoded, orientation, screen);
    }

    @SuppressWarnings("deprecation")
    private static Bitmap decodeRegion(Context context, Uri uri, Rect part, int sample) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(input, false);
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                return decoder.decodeRegion(part, options);
            } finally {
                decoder.recycle();
            }
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static Bitmap cutMiddle(Bitmap decoded, int orientation, int[] screen) {
        int[] turned = turnedSize(orientation, decoded.getWidth(), decoded.getHeight());
        int[] kept = cropSize(turned[0], turned[1], screen[0], screen[1]);
        // Cut before turning: the middle stays the middle.
        int[] stored = turnedSize(orientation, kept[0], kept[1]);
        Bitmap middle = Bitmap.createBitmap(decoded, (decoded.getWidth() - stored[0]) / 2,
                (decoded.getHeight() - stored[1]) / 2, stored[0], stored[1]);
        if (middle != decoded) decoded.recycle();
        return middle;
    }

    private static Bitmap shapedLikeScreen(Bitmap middle, int orientation, int width, int height) {
        Bitmap oriented = rotated(middle, orientation);
        if (oriented != middle) middle.recycle();
        Bitmap scaled = Bitmap.createScaledBitmap(oriented, width, height, true);
        if (scaled != oriented) oriented.recycle();
        return scaled;
    }
}
