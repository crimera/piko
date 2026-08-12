/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.xlite.misc;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.icu.lang.UCharacter;
import android.icu.lang.UProperty;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ReplacementSpan;

import java.io.File;
import java.io.IOException;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.xlite.settings.SettingsActionHandler;
import app.morphe.extension.xlite.settings.SettingsRegistry;

public class UpdateFont {
    public static final String FONT_FILE_NAME = "custom_font.ttf";
    public static final String EMOJI_FONT_FILE_NAME = "custom_emoji_font.ttf";

    private static Typeface textTypeface;
    private static Typeface emojiTypeface;
    private static final Context context;
    private static final boolean isCustomFontEnabled;
    private static final boolean isCustomEmojiFontEnabled;

    static {
        context = app.morphe.extension.shared.Utils.getContext();
        isCustomFontEnabled = SettingsRegistry.getBooleanOrDefault("xlite.content.custom_font.enabled", false);
        isCustomEmojiFontEnabled = SettingsRegistry.getBooleanOrDefault("xlite.content.custom_emoji_font.enabled", false);

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
                PikoUtils.toast(str("piko_xlite_font_deleted"));
            } else {
                PikoUtils.toast(str("piko_xlite_font_delete_failed"));
            }
        } else {
            PikoUtils.toast(str("piko_xlite_font_not_found"));
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
     * (X-Lite settings), which do not pass through the Compose paragraph typeface hook.
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
        Fragment fragment = new FontPickerFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean("isEmojiFont", isEmojiFont);
        fragment.setArguments(arguments);

        int containerId =
                ResourceUtils.getIdentifierOrThrow(
                        activity,
                        ResourceType.ID,
                        "fragment_container"
                );
        activity.getFragmentManager()
                .beginTransaction()
                .replace(containerId, fragment)
                .addToBackStack(null)
                .commit();
    }
}