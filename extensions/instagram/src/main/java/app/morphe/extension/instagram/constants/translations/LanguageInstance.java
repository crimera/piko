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
        switch (lang) {
            case "hi":
                return new StringsHindi();
            case "ko":
                return new StringsKorean();
            case "id":
                return new StringsIndonesian();
            case "pl":
                return new StringsPolish();
            case "tr":
                return new StringsTurkish();
            case "ru":
                return new StringsRussian();
            case "ja":
                return new StringsJapanese();
            case "pt":
                return new StringsPortugueseBR();
            case "es":
                return new StringsSpanish();
            case "bn":
                return new StringsBengali();
            default:
                return new StringsEnglish(); // Default fallback
        }
    }
}
