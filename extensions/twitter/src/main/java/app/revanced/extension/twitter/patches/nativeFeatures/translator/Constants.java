package app.revanced.extension.twitter.patches.nativeFeatures.translator;

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