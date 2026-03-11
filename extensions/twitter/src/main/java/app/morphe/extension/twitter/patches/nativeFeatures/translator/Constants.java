/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
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