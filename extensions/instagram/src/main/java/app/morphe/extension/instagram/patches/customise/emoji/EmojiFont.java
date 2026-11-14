/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.emoji;

import android.graphics.Typeface;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;

import androidx.annotation.RequiresApi;

import java.util.Locale;

import app.morphe.extension.shared.Logger;

/**
 * Draws emoji in a downloaded emoji font instead of the system one.
 *
 * Emoji are drawn by whatever typeface the paint holds, so the font is attached to the emoji runs of
 * the text itself, as spans, where Instagram's text passes on its way to the screen: EmojiCompat's
 * process step (AppCompat text views, text fields, Compose text), the app's own platform TextView
 * subclasses, and {@code StaticLayout.Builder.obtain}, which custom text views (captions, comments)
 * use to lay out text themselves. Emoji drawn as bitmaps (reactions) get the font through the emoji
 * image infra. With no font downloaded every hook hands its value back untouched.
 */
public final class EmojiFont {

    private EmojiFont() {
    }

    private static volatile boolean loaded;
    private static Typeface typeface;

    /** Marks the spans this class adds, so text processed twice does not collect duplicates. */
    @RequiresApi(Build.VERSION_CODES.P)
    private static final class EmojiTypefaceSpan extends TypefaceSpan {
        EmojiTypefaceSpan(Typeface typeface) {
            super(typeface);
        }
    }

    /** The emoji font, loaded on first use. It only changes on a restart. */
    private static Typeface typeface() {
        if (!loaded) {
            synchronized (EmojiFont.class) {
                if (!loaded) {
                    try {
                        typeface = EmojiFontStorage.loadTypeface();
                    } catch (Throwable t) {
                        Logger.printException(() -> "Failed to load the emoji font", t);
                    }
                    loaded = true;
                }
            }
        }
        return typeface;
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
            int first = nextEmoji(text, 0);
            if (first < 0) {
                return text;
            }
            Spannable out;
            if (text instanceof Spannable) {
                out = (Spannable) text;
                for (EmojiTypefaceSpan span : out.getSpans(0, out.length(), EmojiTypefaceSpan.class)) {
                    out.removeSpan(span);
                }
            } else {
                out = new SpannableString(text);
            }
            addSpans(out, first, font);
            return out;
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
        return key + imageKeySuffix();
    }

    private static volatile String imageKeySuffix;

    /** Which font emoji images were drawn in; read from storage once, it only changes on a restart. */
    private static String imageKeySuffix() {
        String suffix = imageKeySuffix;
        if (suffix == null) {
            EmojiFontStorage.Source source = EmojiFontStorage.current();
            suffix = "-piko" + (source == null ? "custom" : source.name().toLowerCase(Locale.ROOT));
            imageKeySuffix = suffix;
        }
        return suffix;
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
                int first = font == null ? -1 : nextEmoji(source, start);
                if (first >= 0 && first < end && !hasOurSpans(source)) {
                    SpannableString copy = new SpannableString(source);
                    addSpans(copy, first, font);
                    text = copy;
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to apply the emoji font to a layout", t);
            text = source;
        }
        return StaticLayout.Builder.obtain(text, start, end, paint, width);
    }

    private static boolean hasOurSpans(CharSequence text) {
        return text instanceof Spanned
                && ((Spanned) text).nextSpanTransition(0, text.length(), EmojiTypefaceSpan.class) < text.length();
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private static void addSpans(Spannable text, int from, Typeface font) {
        int length = text.length();
        int i = from;
        while (i >= 0 && i < length) {
            int end = emojiEnd(text, i);
            text.setSpan(new EmojiTypefaceSpan(font), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i = nextEmoji(text, end);
        }
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

    /** End of the emoji (and any emoji directly after it) that starts at {@code start}. */
    static int emojiEnd(CharSequence text, int start) {
        int length = text.length();
        int i = start + Character.charCount(Character.codePointAt(text, start));
        while (i < length) {
            int cp = Character.codePointAt(text, i);
            if (isModifier(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            if (cp == 0x200D) { // Zero width joiner: the next code point belongs to this emoji.
                i += 1;
                if (i < length) {
                    i += Character.charCount(Character.codePointAt(text, i));
                }
                continue;
            }
            if (startsEmoji(text, i, cp)) {
                i += Character.charCount(cp);
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
        if (cp >= 0x1F000 && cp <= 0x1FAFF) {
            return true;
        }
        if (cp == '#' || cp == '*' || (cp >= '0' && cp <= '9')) {
            if (next == 0x20E3) {
                return true;
            }
            int afterIndex = nextIndex + 1;
            return next == 0xFE0F && afterIndex < text.length() && text.charAt(afterIndex) == 0x20E3;
        }
        if (next == 0xFE0F && (cp == 0x00A9 || cp == 0x00AE || (cp >= 0x203C && cp <= 0x3299))) {
            return true;
        }
        return isDefaultEmojiPresentation(cp);
    }

    /** BMP code points that are drawn as emoji even without a variation selector. */
    private static boolean isDefaultEmojiPresentation(int cp) {
        if (cp < 0x231A || cp > 0x2B55) {
            return false;
        }
        switch (cp) {
            case 0x231A: case 0x231B: case 0x23E9: case 0x23EA: case 0x23EB: case 0x23EC:
            case 0x23F0: case 0x23F3: case 0x25FD: case 0x25FE: case 0x2614: case 0x2615:
            case 0x267F: case 0x2693: case 0x26A1: case 0x26AA: case 0x26AB: case 0x26BD:
            case 0x26BE: case 0x26C4: case 0x26C5: case 0x26CE: case 0x26D4: case 0x26EA:
            case 0x26F2: case 0x26F3: case 0x26F5: case 0x26FA: case 0x26FD: case 0x2705:
            case 0x270A: case 0x270B: case 0x2728: case 0x274C: case 0x274E: case 0x2753:
            case 0x2754: case 0x2755: case 0x2757: case 0x2795: case 0x2796: case 0x2797:
            case 0x27B0: case 0x27BF: case 0x2B1B: case 0x2B1C: case 0x2B50: case 0x2B55:
                return true;
            default:
                return cp >= 0x2648 && cp <= 0x2653; // Zodiac signs.
        }
    }
}
