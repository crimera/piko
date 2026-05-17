/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.translator;

import java.util.Locale;

public class Constants {
    public static final String[] languageTags, displayNames;
    static {
        Locale[] locales = Locale.getAvailableLocales();
        languageTags = new String[locales.length];
        displayNames = new String[locales.length];

        for (int i = 0; i < locales.length; i++) {
            languageTags[i] = locales[i].toLanguageTag();
            displayNames[i] = locales[i].getDisplayName();
        }
    }

    protected static String getLanguageNameFromLocale(String languageCode) {
        try {
            Locale locale = Locale.forLanguageTag(languageCode);
            return locale.getDisplayName(Locale.getDefault());
        } catch (Exception e) {
            return "xxx";
        }
    }
}