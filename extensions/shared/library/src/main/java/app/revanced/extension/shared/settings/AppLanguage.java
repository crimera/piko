package app.revanced.extension.shared.settings;

import java.util.Locale;

public enum AppLanguage {
    /**
     * The current app language.
     */
    DEFAULT,

    // Languages codes not included with YouTube, but are translated on Crowdin
    GA,

    // Language codes found in locale_config.xml
    // All region specific variants have been removed.
    AF,
    AM,
    AR,
    AS,
    AZ,
    BE,
    BG,
    BN,
    BS,
    CA,
    CS,
    DA,
    DE,
    EL,
    EN,
    ES,
    ET,
    EU,
    FA,
    FI,
    FR,
    GL,
    GU,
    HI,
    HE, // App uses obsolete 'IW' and not the modern 'HE' ISO code.
    HR,
    HU,
    HY,
    ID,
    IS,
    IT,
    JA,
    KA,
    KK,
    KM,
    KN,
    KO,
    KY,
    LO,
    LT,
    LV,
    MK,
    ML,
    MN,
    MR,
    MS,
    MY,
    NE,
    NL,
    NB,
    OR,
    PA,
    PL,
    PT,
    RO,
    RU,
    SI,
    SK,
    SL,
    SQ,
    SR,
    SV,
    SW,
    TA,
    TE,
    TH,
    TL,
    TR,
    UK,
    UR,
    UZ,
    VI,
    ZH,
    ZU;

    private final String language;
    private final Locale locale;

    AppLanguage() {
        language = name().toLowerCase(Locale.US);
        locale = Locale.forLanguageTag(language);
    }

    /**
     * @return The 2 letter ISO 639_1 language code.
     */
    public String getLanguage() {
        // Changing the app language does not force the app to completely restart,
        // so the default needs to be the current language and not a static field.
        if (this == DEFAULT) {
            return Locale.getDefault().getLanguage();
        }

        return language;
    }

    public Locale getLocale() {
        if (this == DEFAULT) {
            return Locale.getDefault();
        }

        return locale;
    }
}
