/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.customise.font;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;

/**
 * Replaces the typefaces the app hands out for its own interface with either a font file the
 * user added from their device storage, or the device's own system font.
 *
 * Every typeface the app sets on a view or paint passes through {@link #assign}, so the work here
 * is arranged around one fact: a font file only reaches the screen after a restart, which makes a
 * file-based choice constant for the life of the process. That request is therefore settled once,
 * cached by {@link #typefaceSubstitutions}, and costs a single map lookup afterwards.
 *
 * The system font is the exception: what "sans-serif" resolves to can depend on a device font
 * override that attaches to the process some time after it starts. Its answers are cached too,
 * but dropped whenever "sans-serif" resolves to a different family; see {@link #refreshSystemFamily}.
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

    /** How often the system font's family is checked for a device font override. */
    private static final long SYSTEM_FAMILY_RECHECK_MS = 1000;

    /**
     * Whether a font descriptor names an interface font, keyed by the descriptor's class since
     * each font has a descriptor class of its own.
     */
    private static final Map<Class<?>, Boolean> interfaceDescriptors = new ConcurrentHashMap<>();

    /** Replacements keyed by the typeface they replace. */
    private static final Map<Typeface, Typeface> typefaceSubstitutions = new ConcurrentHashMap<>();

    // Lock-free rather than weak: assign() reads these while text is drawn, and they only ever
    // hold fonts the app itself keeps for the life of the process.

    /** Typefaces resolved for content, which {@link #assign} leaves alone wherever they are set. */
    private static final Set<Typeface> contentTypefaces = ConcurrentHashMap.newKeySet();

    /** The copy handed to content for each typeface, so the shared original is never marked. */
    private static final Map<Typeface, Typeface> contentCopies = new ConcurrentHashMap<>();

    /** Content font resolvers running on any thread, so the thread-local is only read when some are. */
    private static final AtomicInteger resolversRunning = new AtomicInteger();

    /** What "sans-serif" resolved to when last checked, and when that was. */
    private static volatile Typeface systemFamily;
    private static volatile long systemFamilyCheckedAt;

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

    /** The stored font file's typeface. Null, and unused, when {@link #systemFontSelected}. */
    private static Typeface customTypeface;

    /**
     * Whether the app is drawn in the system font rather than {@link #customTypeface}. Written
     * once, in {@link #load}, alongside {@link #customTypeface} itself.
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
     *
     * A stored font always wins over the system-font switch: the switch only ever matters when
     * nothing has been added.
     */
    public static void load() {
        if (FontStorage.hasFile()) {
            customTypeface = FontStorage.loadTypeface();
            systemFontSelected = false;
            active = customTypeface != null;
        } else if (FontStorage.useSystemFont()) {
            systemFontSelected = true;
            active = true;
        } else {
            active = false;
        }
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
            Boolean isInterface = interfaceDescriptors.get(descriptor.getClass());
            if (isInterface == null) {
                isInterface = isInterfaceFontName(String.valueOf(descriptor));
                interfaceDescriptors.put(descriptor.getClass(), isInterface);
            }

            // A handful of interface fonts double as story and note text styles, and those are
            // told apart by who asked rather than by name.
            if (!isInterface || isResolvingContent()) {
                return keepAsContent(original);
            }
            return replacementFor(original);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to apply the custom font", e);
            return original;
        }
    }

    /**
     * Injected where Compose and React Native hand out a typeface, which they resolve on their own
     * without saying which font it is.
     *
     * @param original the typeface the app resolved.
     * @return the custom font in the same weight and slant, or {@code original} when the request
     * must keep the face it asked for.
     */
    public static Typeface apply(Typeface original) {
        if (!active || original == null) {
            return original;
        }
        if (isResolvingContent()) {
            return keepAsContent(original);
        }
        // Compose is also handed ready-made content typefaces, such as the story text style previews.
        if (contentTypefaces.contains(original)) {
            return original;
        }
        return replacementFor(original);
    }

    /**
     * Injected wherever the app sets a typeface on its text views, paints and platform spans.
     * {@code null} draws in the platform default, so it is substituted as that.
     */
    public static Typeface assign(Typeface typeface) {
        if (!active) {
            return typeface;
        }
        Typeface current = typeface != null ? typeface : Typeface.DEFAULT;
        if (isResolvingContent()) {
            return typeface;
        }
        // Content copies are never substituted, so a cached answer is safe to hand out first.
        Typeface cached = cachedReplacement(current);
        if (cached != null) {
            return cached;
        }
        if (contentTypefaces.contains(current)) {
            return typeface;
        }
        return replacementFor(current);
    }

    /**
     * Marks a typeface as content and returns the one content should use. The repository and
     * ResourcesCompat share one object per font with the interface, so content gets its own copy.
     */
    private static Typeface keepAsContent(Typeface typeface) {
        Typeface content = typeface;
        if (!contentTypefaces.contains(typeface)) {
            content = contentCopies.get(typeface);
            if (content == null) {
                content = Typeface.create(typeface, typeface.getWeight(), typeface.isItalic());
                contentCopies.put(typeface, content);
            }
        }
        contentTypefaces.add(content);
        return content;
    }

    static boolean isActive() {
        return active;
    }

    /**
     * The font for a view piko built itself. Those are never app content, so unlike
     * {@link #assign(Typeface)} this asks no questions about the caller.
     */
    static Typeface forPikoView(Typeface typeface) {
        if (!active) {
            return typeface;
        }
        return replacementFor(typeface == null ? Typeface.DEFAULT : typeface);
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
        if (hasExpired(current)) {
            resolversRunning.addAndGet(1 - current.depth);
            current.depth = 1;
        } else {
            current.depth++;
            resolversRunning.incrementAndGet();
        }
        current.updatedAt = SystemClock.uptimeMillis();
    }

    /** Injected at every return of a content font resolver, which keeps the typeface it returns. */
    public static Typeface endContentFontRequest(Typeface returned) {
        if (!active) {
            return returned;
        }

        ContentRequest current = contentRequest.get();
        if (current.depth > 0) {
            current.depth--;
            resolversRunning.decrementAndGet();
        }
        return returned != null ? keepAsContent(returned) : null;
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

        resolversRunning.addAndGet(-current.depth);
        current.depth = 0;
        return false;
    }

    private static boolean isResolvingContent() {
        return resolversRunning.get() > 0 && inContentResolver();
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
        Typeface family = customTypeface;
        if (systemFontSelected) {
            family = systemFamily != null
                    ? systemFamily
                    : Typeface.create(FontStorage.SYSTEM_FONT_FAMILY, Typeface.NORMAL);
        }
        return Typeface.create(family, original.getWeight(), original.isItalic());
    }

    /**
     * What to draw in place of a typeface: the custom font in the same weight and slant, or the
     * typeface itself when it is not one the app writes text in. Settled once per typeface.
     */
    private static Typeface replacementFor(Typeface original) {
        try {
            Typeface cached = cachedReplacement(original);
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

    private static Typeface cachedReplacement(Typeface original) {
        if (systemFontSelected) {
            refreshSystemFamily();
        }
        return typefaceSubstitutions.get(original);
    }

    /**
     * Drops the system font's cached answers once "sans-serif" resolves to a different family - a
     * device font override attaches by replacing it, possibly after the process has started.
     * Checked at most once a second, since resolving the family takes a platform lock.
     */
    private static void refreshSystemFamily() {
        long now = SystemClock.uptimeMillis();
        if (now - systemFamilyCheckedAt < SYSTEM_FAMILY_RECHECK_MS) {
            return;
        }
        systemFamilyCheckedAt = now;
        Typeface family = Typeface.create(FontStorage.SYSTEM_FONT_FAMILY, Typeface.NORMAL);
        if (family != systemFamily) {
            systemFamily = family;
            typefaceSubstitutions.clear();
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
        // Cached since assign() runs while text is drawn, and the answer never changes.
        Boolean cached = drawsTextCache.get(typeface);
        if (cached != null) {
            return cached;
        }
        Paint paint = new Paint();
        paint.setTypeface(typeface);
        boolean result = (paint.hasGlyph("A") && paint.hasGlyph("a"))
                || (paint.hasGlyph("0") && paint.hasGlyph("9"));
        drawsTextCache.put(typeface, result);
        return result;
    }

    private static final Map<Typeface, Boolean> drawsTextCache = new ConcurrentHashMap<>();
}
