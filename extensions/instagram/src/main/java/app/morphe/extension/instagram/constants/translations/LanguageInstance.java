/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
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
        } else if (lang.equals("id")) {
			return new StringsIndonesian();
        } else if (lang.equals("pl")) {
            return new StringsPolish();
        } else if (lang.equals("tr")) {
            return new StringsTurkish();
        } else if (lang.equals("ru")) {
            return new StringsRussian();
        } else if (lang.equals("ja")) {
            return new StringsJapanese();
        } else if (lang.equals("pt")) {
            return new StringsPortugueseBR();
        } else if (lang.equals("es")) {
            return new StringsSpanish();
        } else if (lang.equals("bn")) {
            return new StringsBengali();
        }

        return new StringsEnglish(); // Default fallback
    }
}
