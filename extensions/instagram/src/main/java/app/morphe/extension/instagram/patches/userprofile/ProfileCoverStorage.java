/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/** Each account's profile cover, kept as picked in the app's private storage. */
public final class ProfileCoverStorage {

    public enum ImportResult {
        ADDED,
        NOT_AN_IMAGE,
        TOO_LARGE,
        FAILED,
    }

    private static final String FILE_PREFIX = "profile_cover_";
    private static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;
    private static final int MAX_WIDTH_PX = 1440;

    private ProfileCoverStorage() {
    }

    private static File fileOf(String accountId) {
        return new File(Utils.getContext().getFilesDir(), FILE_PREFIX + accountId.replaceAll("[^0-9A-Za-z_]", "_"));
    }

    /** Changes whenever the account's cover does; 0 when it has none. */
    public static long stamp(String accountId) {
        File file = fileOf(accountId);
        return file.isFile() ? file.lastModified() : 0;
    }

    /** A new drawable for the account's cover, animated when it is a GIF. Null when it has none or can't be read. */
    public static Drawable decode(String accountId) {
        File file = fileOf(accountId);
        if (!file.isFile()) return null;
        try {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file), (decoder, info, source) -> {
                int width = info.getSize().getWidth();
                if (width > MAX_WIDTH_PX) decoder.setTargetSampleSize(width / MAX_WIDTH_PX);
            });
        } catch (Exception e) {
            Logger.printException(() -> "Failed to decode the profile cover", e);
            return null;
        }
    }

    public static ImportResult importFrom(Context context, Uri uri, String accountId) {
        File temp = new File(context.getFilesDir(), FILE_PREFIX + "import.tmp");
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(temp)) {
                if (in == null) return ImportResult.FAILED;
                byte[] buffer = new byte[16 * 1024];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_SIZE_BYTES) {
                        temp.delete();
                        return ImportResult.TOO_LARGE;
                    }
                    out.write(buffer, 0, read);
                }
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temp.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                temp.delete();
                return ImportResult.NOT_AN_IMAGE;
            }
            File target = fileOf(accountId);
            target.delete();
            if (!temp.renameTo(target)) {
                temp.delete();
                return ImportResult.FAILED;
            }
            ProfileCoverPref.reset(accountId);
            return ImportResult.ADDED;
        } catch (Exception e) {
            temp.delete();
            Logger.printException(() -> "Failed to import the profile cover", e);
            return ImportResult.FAILED;
        }
    }

    public static boolean delete(String accountId) {
        File file = fileOf(accountId);
        ProfileCoverPref.reset(accountId);
        return file.isFile() && file.delete();
    }
}
