/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.instagram.constants.translations;

import java.util.Locale;

public class LanguageInstance {

    public static DefaultStrings getLanguageInstance() {
        String lang = Locale.getDefault().getLanguage();

        if (lang.equals("hi")) {
            return new StringsHindi();
        } else if (lang.equals("ko")) {
            return new StringsKorean();
        } else if (lang.equals("in")) {
			      return new StringsIndonesian();
        } else if (lang.equals("pl")) {
            return new StringsPolish();
        }

        return new StringsEnglish(); // Default fallback
    }
}
