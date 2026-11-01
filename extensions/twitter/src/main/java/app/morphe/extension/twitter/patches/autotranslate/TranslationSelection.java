/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.autotranslate;

import java.util.Locale;

/** Immutable request settings; changing provider or target invalidates existing view bindings. */
final class TranslationSelection {
    final int provider;
    final String target;

    TranslationSelection(int provider, String target) {
        this.provider = provider;
        this.target = target;
    }

    boolean matches(int provider, String target) {
        return this.provider == provider && this.target.equalsIgnoreCase(target);
    }

    String cacheKey(long tweetId, String source, String text) {
        return tweetId + "\u0000" + source + "\u0000" + target.toLowerCase(Locale.ROOT)
                + "\u0000" + provider + "\u0000" + text;
    }

    boolean isSourceLanguage(String source) {
        if (source.equalsIgnoreCase(target)) return true;
        Locale from = Locale.forLanguageTag(source), to = Locale.forLanguageTag(target);
        if (from.getLanguage().isEmpty() || !from.getLanguage().equals(to.getLanguage())) return false;
        // Chinese regional tags can imply different scripts even without an explicit script subtag.
        if ("zh".equals(from.getLanguage())) return false;
        return from.getScript().isEmpty() || to.getScript().isEmpty() || from.getScript().equals(to.getScript());
    }
}
