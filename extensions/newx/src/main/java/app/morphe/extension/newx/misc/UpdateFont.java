/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.newx.misc;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.icu.lang.UCharacter;
import android.icu.lang.UProperty;
import android.net.Uri;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ReplacementSpan;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.newx.settings.SettingsActionHandler;
import app.morphe.extension.newx.settings.SettingsRegistry;

public class UpdateFont {
    public static final String FONT_FILE_NAME = "custom_font.ttf";
    public static final String EMOJI_FONT_FILE_NAME = "custom_emoji_font.ttf";

    private static final int PICK_FONT_REQUEST_CODE = 0x5061;
    private static final int PICK_EMOJI_FONT_REQUEST_CODE = 0x5062;

    private static Typeface textTypeface;
    private static Typeface emojiTypeface;
    private static final Context context;
    private static final boolean isCustomFontEnabled;
    private static final boolean isCustomEmojiFontEnabled;

    static {
        context = app.morphe.extension.shared.Utils.getContext();
        isCustomFontEnabled = SettingsRegistry.getBooleanOrDefault("newx.content.custom_font.enabled", false);
        isCustomEmojiFontEnabled = SettingsRegistry.getBooleanOrDefault("newx.content.custom_emoji_font.enabled", false);

        if (isCustomFontEnabled) {
            loadFont(FONT_FILE_NAME, false);
        }
        if (isCustomEmojiFontEnabled) {
            loadFont(EMOJI_FONT_FILE_NAME, true);
        }
    }

    public static void loadFont(String fontName, boolean isEmojiFont) {
        File fontFile = new File(context.getFilesDir(), fontName);
        if (!fontFile.exists()) {
            PikoUtils.logger("Font not found: " + fontFile.getAbsolutePath());
        } else {
            Typeface typeface = createTypeface(fontFile, isEmojiFont);
            if (isEmojiFont) {
                emojiTypeface = typeface;
            } else {
                textTypeface = typeface;
            }
            PikoUtils.logger("Font loaded: " + fontFile.getAbsolutePath());
        }
    }

    public static void deleteFont(boolean isEmojiFont) {
        String filename = isEmojiFont ? EMOJI_FONT_FILE_NAME : FONT_FILE_NAME;
        File fontFile = new File(context.getFilesDir(), filename);
        if (fontFile.exists()) {
            if (fontFile.delete()) {
                PikoUtils.toast(str("piko_newx_font_deleted"));
            } else {
                PikoUtils.toast(str("piko_newx_font_delete_failed"));
            }
        } else {
            PikoUtils.toast(str("piko_newx_font_not_found"));
        }
    }

    public static void applyTypeface(Paint paint, Typeface typeface) {
        paint.setTypeface(processTypeface(typeface));
    }

    public static void applyEmojiTypeface(Paint paint, Typeface typeface) {
        paint.setTypeface(
                isCustomEmojiFontEnabled && emojiTypeface != null ? emojiTypeface : typeface
        );
    }

    public static CharSequence processComposeEmoji(CharSequence value) {
        if (value == null || !isCustomEmojiFontEnabled || emojiTypeface == null || value.length() == 0) {
            return value;
        }

        SpannableStringBuilder result = new SpannableStringBuilder(value);
        int index = 0;
        while (index < result.length()) {
            int end = findEmojiEnd(result, index);
            if (end == index) {
                int codePoint = Character.codePointAt(result, index);
                index += Character.charCount(codePoint);
                continue;
            }

            ReplacementSpan[] existingSpans =
                    result.getSpans(index, end, ReplacementSpan.class);
            for (ReplacementSpan existingSpan : existingSpans) {
                if (existingSpan.getClass().getName().startsWith("androidx.emoji2.text.")) {
                    result.removeSpan(existingSpan);
                }
            }
            result.setSpan(
                    new ComposeEmojiSpan(),
                    index,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            index = end;
        }
        return result;
    }

    private static int findEmojiEnd(CharSequence text, int start) {
        int codePoint = Character.codePointAt(text, start);
        int end = start + Character.charCount(codePoint);
        if (!isEmojiStart(text, codePoint, end)) return start;

        if (isRegionalIndicator(codePoint) && end < text.length()) {
            int next = Character.codePointAt(text, end);
            if (isRegionalIndicator(next)) return end + Character.charCount(next);
        }

        end = consumeEmojiSuffix(text, end);
        while (end < text.length() && Character.codePointAt(text, end) == 0x200D) {
            int joinedStart = end + 1;
            if (joinedStart >= text.length()) break;
            int joinedCodePoint = Character.codePointAt(text, joinedStart);
            if (!UCharacter.hasBinaryProperty(joinedCodePoint, UProperty.EMOJI)) break;
            end = joinedStart + Character.charCount(joinedCodePoint);
            end = consumeEmojiSuffix(text, end);
        }
        return end;
    }

    private static boolean isEmojiStart(CharSequence text, int codePoint, int nextIndex) {
        if (UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_PRESENTATION)) return true;
        if (!UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)) return false;
        if (nextIndex >= text.length()) return false;
        int next = Character.codePointAt(text, nextIndex);
        return next == 0xFE0F || next == 0x20E3;
    }

    private static int consumeEmojiSuffix(CharSequence text, int start) {
        int end = start;
        if (end < text.length()) {
            int codePoint = Character.codePointAt(text, end);
            if (codePoint == 0xFE0E || codePoint == 0xFE0F) {
                end += Character.charCount(codePoint);
            }
        }
        if (end < text.length()) {
            int codePoint = Character.codePointAt(text, end);
            if (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF) {
                end += Character.charCount(codePoint);
            }
        }
        if (end < text.length() && Character.codePointAt(text, end) == 0x20E3) {
            end += Character.charCount(0x20E3);
        }
        while (end < text.length()) {
            int codePoint = Character.codePointAt(text, end);
            if (codePoint < 0xE0020 || codePoint > 0xE007F) break;
            end += Character.charCount(codePoint);
        }
        return end;
    }

    private static boolean isRegionalIndicator(int codePoint) {
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }

    private static Typeface createTypeface(File fontFile, boolean isEmojiFont) {
        if (isEmojiFont || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Typeface.createFromFile(fontFile);
        }
        try {
            Font font = new Font.Builder(fontFile).build();
            FontFamily family = new FontFamily.Builder(font).build();
            return new Typeface.CustomFallbackBuilder(family)
                    .setSystemFallback("sans-serif")
                    .build();
        } catch (IOException | RuntimeException exception) {
            PikoUtils.logger(exception);
            return Typeface.createFromFile(fontFile);
        }
    }

    /**
     * Returns the loaded custom text font when both the setting and the font file are in
     * place, otherwise the given fallback. Used by View-based surfaces we own directly
     * (NewX settings), which do not pass through the Compose paragraph typeface hook.
     */
    public static Typeface customTypefaceOr(Typeface fallback) {
        return isCustomFontEnabled && textTypeface != null
                ? styledTypeface(textTypeface, fallback)
                : fallback;
    }

    private static Typeface processTypeface(Typeface original) {
        return isCustomFontEnabled && textTypeface != null
                ? styledTypeface(textTypeface, original)
                : original;
    }

    private static Typeface styledTypeface(Typeface custom, Typeface original) {
        int style = original == null ? Typeface.NORMAL : original.getStyle();
        return Typeface.create(custom, style);
    }

    private static final class ComposeEmojiSpan extends ReplacementSpan {
        @Override
        public int getSize(
                Paint paint,
                CharSequence text,
                int start,
                int end,
                Paint.FontMetricsInt fontMetrics
        ) {
            Typeface original = paint.getTypeface();
            applyEmojiTypeface(paint, original);
            if (fontMetrics != null) {
                Paint.FontMetricsInt customMetrics = paint.getFontMetricsInt();
                fontMetrics.top = customMetrics.top;
                fontMetrics.ascent = customMetrics.ascent;
                fontMetrics.descent = customMetrics.descent;
                fontMetrics.bottom = customMetrics.bottom;
                fontMetrics.leading = customMetrics.leading;
            }
            int width = Math.round(paint.measureText(text, start, end));
            paint.setTypeface(original);
            return width;
        }

        @Override
        public void draw(
                Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int y,
                int bottom,
                Paint paint
        ) {
            Typeface original = paint.getTypeface();
            applyEmojiTypeface(paint, original);
            canvas.drawText(text, start, end, x, y, paint);
            paint.setTypeface(original);
        }
    }

    public static final class AddFontAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            openPicker(activity, false);
        }
    }

    public static final class AddEmojiFontAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            openPicker(activity, true);
        }
    }

    public static final class DeleteFontAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            deleteFont(false);
        }
    }

    public static final class DeleteEmojiFontAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            deleteFont(true);
        }
    }

    private static void openPicker(Activity activity, boolean isEmojiFont) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        activity.startActivityForResult(
                intent,
                isEmojiFont ? PICK_EMOJI_FONT_REQUEST_CODE : PICK_FONT_REQUEST_CODE
        );
    }

    public static boolean handleActivityResult(
            Activity activity,
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        if (requestCode != PICK_FONT_REQUEST_CODE && requestCode != PICK_EMOJI_FONT_REQUEST_CODE) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        boolean isEmojiFont = (requestCode == PICK_EMOJI_FONT_REQUEST_CODE);
        Uri uri = data.getData();
        if (hasValidFontHeader(activity, uri)) {
            if (copyFont(activity, uri, isEmojiFont)) {
                PikoUtils.toast(str("piko_newx_font_added"));
                promptForRestart(activity);
            } else {
                PikoUtils.toast(str("piko_newx_font_add_failed"));
            }
        } else {
            PikoUtils.toast(str("piko_newx_font_invalid"));
        }
        return true;
    }

    private static boolean hasValidFontHeader(Context ctx, Uri uri) {
        try (InputStream inputStream = ctx.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return false;
            byte[] header = new byte[4];
            int read = inputStream.read(header);
            if (read < 4) return false;
            String magic = new String(header, StandardCharsets.ISO_8859_1);
            if (magic.equals("OTTO") || magic.equals("true") || magic.equals("ttcf")) {
                return true;
            }
            if (header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00) {
                return true;
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return false;
    }

    private static boolean copyFont(Context ctx, Uri uri, boolean isEmojiFont) {
        String filename = isEmojiFont ? EMOJI_FONT_FILE_NAME : FONT_FILE_NAME;
        File outFile = new File(ctx.getFilesDir(), filename);
        try (InputStream inputStream = ctx.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(outFile)) {
            if (inputStream == null) return false;
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            return true;
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return false;
    }

    private static void promptForRestart(Activity activity) {
        Dialog dialog =
                CustomDialog.create(
                        activity,
                        str("piko_newx_restart_title"),
                        str("piko_newx_restart_summary"),
                        null,
                        str("piko_newx_restart_now"),
                        () -> Utils.restartApp(activity),
                        () -> { },
                        null,
                        null,
                        true
                ).first;
        dialog.show();
    }
}