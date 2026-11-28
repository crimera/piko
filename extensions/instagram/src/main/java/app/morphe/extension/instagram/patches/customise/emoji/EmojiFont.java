/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.emoji;

import android.graphics.Typeface;
import android.icu.lang.UCharacter;
import android.icu.lang.UProperty;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;

import androidx.annotation.RequiresApi;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import app.morphe.extension.shared.Logger;

/**
 * Applies the imported font to emoji runs in text and to the paint used to draw emoji bitmaps.
 */
public final class EmojiFont {

    private EmojiFont() {
    }

    private static volatile boolean loaded;
    private static EmojiFontStorage.LoadedFont installed;

    /** Marks the spans this class adds, so text processed twice does not collect duplicates. */
    @RequiresApi(Build.VERSION_CODES.P)
    private static final class EmojiTypefaceSpan extends TypefaceSpan {
        private final String emoji;

        EmojiTypefaceSpan(Typeface typeface, String emoji) {
            super(typeface);
            this.emoji = emoji;
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            applyTypeface(paint);
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            applyTypeface(paint);
        }

        private void applyTypeface(TextPaint paint) {
            // Instagram can size the layout from the original text before this span is installed.
            float originalWidth = advance(paint);
            super.updateMeasureState(paint);
            float customWidth = advance(paint);
            if (originalWidth > 0 && customWidth > 0 && customWidth != originalWidth) {
                paint.setTextScaleX(paint.getTextScaleX() * (originalWidth / customWidth));
            }
        }

        private float advance(TextPaint paint) {
            return paint.getRunAdvance(emoji, 0, emoji.length(), 0, emoji.length(), false, emoji.length());
        }
    }

    /** The emoji font, loaded on first use. It only changes on a restart. */
    private static Typeface typeface() {
        if (!loaded) {
            synchronized (EmojiFont.class) {
                if (!loaded) {
                    try {
                        installed = EmojiFontStorage.load();
                    } catch (Throwable t) {
                        Logger.printException(() -> "Failed to load the emoji font", t);
                    }
                    loaded = true;
                }
            }
        }
        return installed == null ? null : installed.typeface;
    }

    /**
     * Injected at the entry of EmojiCompat's process method. EmojiCompat itself adds its spans to a
     * {@link Spannable} in place, so its callers expect that, and the same is done here.
     */
    public static CharSequence process(CharSequence text) {
        try {
            if (text == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return text;
            }
            Typeface font = typeface();
            if (font == null) {
                return text;
            }
            return applySpans(text, 0, text.length(), font, true);
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to apply the emoji font", t);
            return text;
        }
    }

    /**
     * Wraps the typeface the emoji image infra hands its bitmap renderers (null for the system
     * font). Reactions, the reaction picker and other emoji images are drawn into bitmaps, not
     * laid out as text.
     */
    public static Typeface emojiImageTypeface(Typeface original) {
        Typeface font = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? typeface() : null;
        return font != null ? font : original;
    }

    /**
     * Injected into the emoji bitmap disk-cache key builder. The font is made part of the key;
     * otherwise bitmaps drawn in the previous font keep being served after a change.
     */
    public static String emojiImageCacheKey(String key) {
        if (key == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P || typeface() == null) {
            return key;
        }
        return key + installed.suffix;
    }

    public static boolean isEmojiTypeface(Typeface candidate) {
        return loaded && installed != null && candidate == installed.typeface;
    }

    /** Injected into the app's text fields: spans the emoji of the text being edited, in place. */
    public static void onTextChanged(CharSequence text) {
        if (text instanceof Spannable) {
            process(text);
        }
    }

    /**
     * Replaces every {@code StaticLayout.Builder.obtain} call in the app. The text is copied before
     * spans are added: a layout's source often belongs to a view that keeps editing it.
     */
    public static StaticLayout.Builder obtain(CharSequence source, int start, int end,
                                              TextPaint paint, int width) {
        CharSequence text = source;
        try {
            if (source != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface font = typeface();
                if (font != null) {
                    text = applySpans(source, start, end, font, false);
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to apply the emoji font to a layout", t);
            text = source;
        }
        return StaticLayout.Builder.obtain(text, start, end, paint, width);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    static CharSequence applySpans(CharSequence text, int start, int end, Typeface font, boolean inPlace) {
        Spanned source = text instanceof Spanned ? (Spanned) text : null;
        Set<EmojiTypefaceSpan> stale = null;
        if (source != null) {
            EmojiTypefaceSpan[] existing = source.getSpans(start, end, EmojiTypefaceSpan.class);
            if (existing.length != 0) stale = new HashSet<>(Arrays.asList(existing));
        }

        Spannable out = inPlace && text instanceof Spannable ? (Spannable) text : null;
        for (int i = nextEmoji(text, start); i >= 0 && i < end;) {
            int runEnd = Math.min(end, emojiEnd(text, i));
            boolean matched = false;
            if (stale != null && !stale.isEmpty()) {
                for (EmojiTypefaceSpan span : source.getSpans(i, runEnd, EmojiTypefaceSpan.class)) {
                    if (Math.max(start, source.getSpanStart(span)) == i
                            && Math.min(end, source.getSpanEnd(span)) == runEnd
                            && span.emoji.length() == source.getSpanEnd(span) - source.getSpanStart(span)
                            && TextUtils.regionMatches(text, source.getSpanStart(span), span.emoji, 0, span.emoji.length())
                            && stale.remove(span)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) {
                if (out == null) out = new SpannableString(text);
                out.setSpan(new EmojiTypefaceSpan(font, TextUtils.substring(text, i, runEnd)),
                        i, runEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (runEnd == end) break;
            i = nextEmoji(text, runEnd);
        }
        // Edits can split a run or turn it back into ordinary text. Remove only our obsolete spans.
        if (stale != null && !stale.isEmpty()) {
            if (out == null) out = new SpannableString(text);
            for (EmojiTypefaceSpan span : stale) out.removeSpan(span);
        }
        return out != null ? out : text;
    }

    /** Index of the next emoji at or after {@code from}, or -1. */
    static int nextEmoji(CharSequence text, int from) {
        int length = text.length();
        int i = Math.max(0, from);
        while (i < length) {
            char c = text.charAt(i);
            // Fast path: plain Latin text other than keycap bases and (c)/(r) never starts one.
            if (c < 0x00A9 && c != '#' && c != '*' && (c < '0' || c > '9')) {
                i++;
                continue;
            }
            int cp = Character.codePointAt(text, i);
            if (startsEmoji(text, i, cp)) {
                return i;
            }
            i += Character.charCount(cp);
        }
        return -1;
    }

    /** End of one emoji, including the code points that form a single glyph. */
    static int emojiEnd(CharSequence text, int start) {
        int length = text.length();
        int first = Character.codePointAt(text, start);
        int i = start + Character.charCount(first);
        if (UCharacter.hasBinaryProperty(first, UProperty.REGIONAL_INDICATOR) && i < length) {
            int second = Character.codePointAt(text, i);
            if (UCharacter.hasBinaryProperty(second, UProperty.REGIONAL_INDICATOR) && startsEmoji(text, i, second)) {
                i += Character.charCount(second);
            }
        }
        while (i < length) {
            int cp = Character.codePointAt(text, i);
            if (isModifier(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            if (cp == 0x200D && i + 1 < length) {
                int joined = Character.codePointAt(text, i + 1);
                int after = i + 1 + Character.charCount(joined);
                // A joined emoji may normally have text presentation (e.g. the female sign).
                if (!UCharacter.hasBinaryProperty(joined, UProperty.EMOJI)
                        || (after < length && text.charAt(after) == 0xFE0E)) break;
                i = after;
                continue;
            }
            break;
        }
        return i;
    }

    private static boolean isModifier(int cp) {
        return cp == 0xFE0F || cp == 0x20E3
                || (cp >= 0x1F3FB && cp <= 0x1F3FF) // Skin tones.
                || (cp >= 0xE0020 && cp <= 0xE007F); // Tag sequences (subdivision flags).
    }

    static boolean startsEmoji(CharSequence text, int index, int cp) {
        int nextIndex = index + Character.charCount(cp);
        int next = nextIndex < text.length() ? Character.codePointAt(text, nextIndex) : -1;
        if (next == 0xFE0E) { // Text presentation asked for explicitly.
            return false;
        }
        if (cp == '#' || cp == '*' || (cp >= '0' && cp <= '9')) {
            if (next == 0x20E3) {
                return true;
            }
            int afterIndex = nextIndex + 1;
            return next == 0xFE0F && afterIndex < text.length() && text.charAt(afterIndex) == 0x20E3;
        }
        return UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_PRESENTATION)
                || (next == 0xFE0F && UCharacter.hasBinaryProperty(cp, UProperty.EMOJI));
    }
}
