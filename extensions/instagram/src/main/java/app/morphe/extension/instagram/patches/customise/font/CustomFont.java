/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.customise.font;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.widget.TextView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;

/**
 * Replaces the typefaces the app hands out for its own interface with either a font file the
 * user added from their device storage, or the device's own system font.
 *
 * Every piece of text the app draws passes through {@link #apply}, so the work here is arranged
 * around one fact: a font file only reaches the screen after a restart, which makes a file-based
 * choice constant for the life of the process. That request is therefore settled once, cached by
 * {@link #descriptorSubstitutions} and {@link #typefaceSubstitutions}, and costs a single map
 * lookup afterwards.
 *
 * The system font is the exception: what "sans-serif" resolves to can depend on a device font
 * override that attaches to the process some time after it starts, rather than on anything
 * {@link #load} settles up front. Caching its answer the first time it is asked would risk
 * freezing every interface font on whatever that override had - or had not - applied yet, for the
 * rest of the process. So a system-font request is re-derived every time instead of cached; see
 * {@link #replacementFor} and {@link #apply(Object, Typeface)}.
 */
public class CustomFont {

    /**
     * Names of the fonts the app uses for its own interface, as the font descriptors report
     * themselves. Every other font - the creative fonts of the story and reel editor, notes and
     * profile bios, and fonts the user imported into the app - is content and is left untouched.
     */
    private static final String[] INTERFACE_FONT_NAMES = {
            "INSTAGRAM_SANS",
            "PRISM_SANS",
            "SANS_SERIF",
            "SERIF_MONOSPACE",
            "ROBOTO",
            "OPTIMISTIC",
            "FACEBOOK",
            "FB_",
            "IGVR",
    };

    /**
     * How long a stuck content-font flag - from a resolver that threw before its exit hook ran -
     * keeps suppressing substitution on that thread. Resolution itself is fast in-memory work, but
     * the margin has to clear real scheduling and GC pauses, not just the CPU cost of the work.
     */
    private static final long REQUEST_TIMEOUT_MS = 100;

    /** Enough for every typeface the app hands out, and a ceiling if one ever hands out more. */
    private static final int MAX_CACHED_SUBSTITUTIONS = 256;

    /** Stands in the descriptor cache for a font that must keep the face it asked for. */
    private static final Object KEEP_ORIGINAL = new Object();

    /**
     * What to hand back for a font descriptor: the replacement typeface, or {@link #KEEP_ORIGINAL}.
     * Keyed by the descriptor's class because each font has a descriptor class of its own, and the
     * repository holds one typeface per descriptor - so both the answer and the replacement are
     * settled the first time a font is seen. Not consulted at all for the system font; see the
     * class-level note on why its answer is re-derived every time instead.
     */
    private static final Map<Class<?>, Object> descriptorSubstitutions = new ConcurrentHashMap<>();

    /**
     * Replacements for typefaces that arrive without a descriptor, keyed by the typeface they
     * replace. {@link Typeface} does not define equality, so this is keyed by identity. Not
     * consulted at all for the system font; see the class-level note on why its answer is
     * re-derived every time instead.
     */
    private static final Map<Typeface, Typeface> typefaceSubstitutions = new ConcurrentHashMap<>();

    /** How deep into the resolvers of a font the user picked in the app this thread is. */
    private static final ThreadLocal<ContentRequest> contentRequest =
            new ThreadLocal<ContentRequest>() {
                @Override
                protected ContentRequest initialValue() {
                    return new ContentRequest();
                }
            };

    /**
     * Whether a custom font is in use. The only thing a font request reads before it can be sure
     * there is nothing to do, and written once, from {@link #load}, before any text is drawn.
     */
    private static volatile boolean active;

    /** The font the app is drawn in. Written once, in {@link #load}. */
    private static Typeface customTypeface;

    /**
     * Whether {@link #customTypeface} stands for {@link FontStorage#SYSTEM_FONT} rather than a
     * font file. Written once, in {@link #load}, alongside {@link #customTypeface} itself.
     */
    private static boolean systemFontSelected;

    /** Guards the cache-size check and this flag in {@link #replacementFor}, both racy otherwise. */
    private static final Object substitutionCacheLock = new Object();

    private static boolean substitutionCacheFullReported;

    private static final class ContentRequest {
        int depth;
        long updatedAt;
    }

    /**
     * Settles the font for the life of the process. Injected into the app's start up, so the first
     * text the app draws already has a typeface to be drawn in and no request has to go looking.
     */
    public static void load() {
        String font = effectiveFont();
        if (font == null) {
            return;
        }

        // Already parsed and cached by effectiveFont()'s usability check, just above - read back
        // rather than parsed again, so the file is not decoded twice on every cold start.
        customTypeface = FontStorage.previewTypeface(font);
        systemFontSelected = FontStorage.isSystemFont(font);
        active = customTypeface != null;
    }

    /**
     * The font the app would be drawn in if it started now, null when it would use Instagram's
     * own. A font that is switched off or no longer there is no different from none at all.
     *
     * {@link FontStorage#SYSTEM_FONT} - the empty string - is itself a real answer here, so
     * "nothing to draw in" has to be its own value rather than being folded into an empty one.
     */
    private static String effectiveFont() {
        if (!FontStorage.isEnabled()) {
            return null;
        }
        String selected = FontStorage.selected();
        return FontStorage.isUsable(selected) ? selected : null;
    }

    /**
     * Injected at every return of the typeface repository, which resolves a font from the
     * descriptor it was handed. The descriptor names the font, which is what tells an interface
     * font apart from one the user picked inside the app.
     *
     * @param descriptor the font the app asked for.
     * @param original   the typeface the app resolved.
     * @return the custom font in the same weight and slant, or {@code original} when this font
     * must keep the face it asked for.
     */
    public static Typeface apply(Object descriptor, Typeface original) {
        if (!active || original == null || descriptor == null) {
            return original;
        }

        try {
            Object replacement;
            if (systemFontSelected) {
                // Never cached - see the class-level note on why a system-font answer cannot be
                // trusted to stay right for the life of the process.
                replacement = isInterfaceFontName(String.valueOf(descriptor))
                        ? replacementFor(original)
                        : KEEP_ORIGINAL;
            } else {
                Class<?> descriptorClass = descriptor.getClass();
                replacement = descriptorSubstitutions.get(descriptorClass);
                if (replacement == null) {
                    replacement = isInterfaceFontName(String.valueOf(descriptor))
                            ? replacementFor(original)
                            : KEEP_ORIGINAL;
                    descriptorSubstitutions.put(descriptorClass, replacement);
                }
            }

            if (replacement == KEEP_ORIGINAL) {
                return original;
            }

            // A handful of interface fonts double as story and note text styles, and those are
            // told apart by who asked rather than by name.
            return inContentResolver() ? original : (Typeface) replacement;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to apply the custom font", e);
            return original;
        }
    }

    /**
     * Injected wherever the app hands out a typeface for its own interface without saying which
     * font it is: the IGDS font helper, and Compose, which resolves fonts on its own.
     *
     * @param original the typeface the app resolved.
     * @return the custom font in the same weight and slant, or {@code original} when the request
     * must keep the face it asked for.
     */
    public static Typeface apply(Typeface original) {
        if (!active || original == null || inContentResolver()) {
            return original;
        }
        return replacementFor(original);
    }

    /**
     * Applies the custom font to a view piko built itself. Piko's own views are plain
     * {@link TextView}s, so they never reach the app's typeface repository, and they are never
     * app content - so unlike {@link #apply(Typeface)} this asks no questions about the caller.
     */
    public static void applyTo(TextView view) {
        if (!active || view == null) {
            return;
        }

        Typeface current = view.getTypeface();
        if (current == null) {
            return;
        }

        Typeface replacement = replacementFor(current);
        if (replacement != current) {
            view.setTypeface(replacement);
        }
    }

    /**
     * Injected at the entry of the resolvers that turn a font the user picked inside the app into a
     * typeface. Those resolvers ask the repository for fonts the interface uses as well, so the
     * typefaces they resolve are recognised by their call path rather than by their name.
     */
    public static void beginContentFontRequest() {
        if (!active) {
            return;
        }

        ContentRequest current = contentRequest.get();
        current.depth = hasExpired(current) ? 1 : current.depth + 1;
        current.updatedAt = SystemClock.uptimeMillis();
    }

    /** Injected at every return of a content font resolver. */
    public static void endContentFontRequest() {
        if (!active) {
            return;
        }

        ContentRequest current = contentRequest.get();
        if (current.depth > 0) {
            current.depth--;
        }
    }

    /**
     * Whether the thread is inside a resolver for a font the user picked in the app.
     *
     * The record is given a life of its own because a resolver that throws never reaches its exit
     * hook, and a depth left standing would keep the custom font off that thread for good. The
     * clock is only read once a resolver is actually on the stack, which is the rare case.
     */
    private static boolean inContentResolver() {
        ContentRequest current = contentRequest.get();
        if (current.depth <= 0) {
            return false;
        }
        if (!hasExpired(current)) {
            return true;
        }

        current.depth = 0;
        return false;
    }

    private static boolean hasExpired(ContentRequest current) {
        return SystemClock.uptimeMillis() - current.updatedAt > REQUEST_TIMEOUT_MS;
    }

    /**
     * The custom font in the weight and slant of the typeface it stands in for.
     *
     * The system font is asked for by name - the "sans-serif" the whole platform already renders
     * its own default text in - rather than by re-deriving {@link Typeface#DEFAULT}. A device's
     * own font override can replace what that name resolves to without ever touching the
     * DEFAULT/DEFAULT_BOLD static fields themselves, which stay pinned to the platform's original
     * face: named lookup is what every other app's plain, non-bold text is observed going
     * through, and DEFAULT/DEFAULT_BOLD is what stays unchanged regardless. A file the user added
     * has no such name to begin with, so it is asked for the ordinary way, by instance.
     */
    private static Typeface derive(Typeface original) {
        if (systemFontSelected) {
            return Typeface.create(FontStorage.SYSTEM_FONT_FAMILY, styleOf(original));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(customTypeface, original.getWeight(), original.isItalic());
        }
        return Typeface.create(customTypeface, original.getStyle());
    }

    /** `original`'s weight and slant, folded down to the four styles {@link Typeface#create} takes. */
    private static int styleOf(Typeface original) {
        boolean bold;
        boolean italic;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bold = original.getWeight() >= 600;
            italic = original.isItalic();
        } else {
            bold = (original.getStyle() & Typeface.BOLD) != 0;
            italic = (original.getStyle() & Typeface.ITALIC) != 0;
        }
        if (bold && italic) {
            return Typeface.BOLD_ITALIC;
        }
        return bold ? Typeface.BOLD : italic ? Typeface.ITALIC : Typeface.NORMAL;
    }

    /**
     * What to draw in place of a typeface: the custom font in the same weight and slant, or the
     * typeface itself when it is not one the app writes text in. Settled once per typeface and
     * cached - except for the system font, which is re-derived on every call; see the class-level
     * note on why its answer is not trusted to stay right for the life of the process.
     */
    private static Typeface replacementFor(Typeface original) {
        try {
            if (systemFontSelected) {
                return drawsText(original) ? derive(original) : original;
            }

            Typeface cached = typefaceSubstitutions.get(original);
            if (cached != null) {
                return cached;
            }

            Typeface replacement = drawsText(original) ? derive(original) : original;
            synchronized (substitutionCacheLock) {
                if (typefaceSubstitutions.size() < MAX_CACHED_SUBSTITUTIONS) {
                    typefaceSubstitutions.put(original, replacement);
                    // Views that are bound again arrive holding what was handed out last time, so
                    // a replacement maps to itself and no view is ever given a new typeface twice.
                    typefaceSubstitutions.putIfAbsent(replacement, replacement);
                } else if (!substitutionCacheFullReported) {
                    substitutionCacheFullReported = true;
                    Logger.printException(() -> "Custom font substitutions no longer being cached");
                }
            }
            return replacement;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to derive the custom font", e);
            return original;
        }
    }

    /**
     * Whether a font descriptor names one of the fonts the interface uses. Whether that font holds
     * text rather than pictures is a separate question, asked of the font itself in
     * {@link #drawsText}.
     */
    private static boolean isInterfaceFontName(String fontName) {
        for (String name : INTERFACE_FONT_NAMES) {
            if (fontName.startsWith(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a typeface is one the app writes words or numbers in, rather than one of the icon or
     * logo fonts the interface loads under the same interface-font names. Checked on the font
     * itself instead of a name list: a text font always carries a full alphabet or digit set, and a
     * picture font never does.
     */
    private static boolean drawsText(Typeface typeface) {
        Paint paint = new Paint();
        paint.setTypeface(typeface);
        return (paint.hasGlyph("A") && paint.hasGlyph("a"))
                || (paint.hasGlyph("0") && paint.hasGlyph("9"));
    }
}
