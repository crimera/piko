package app.revanced.integrations.twitter.patches.translator;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


import app.revanced.integrations.shared.Utils;

public class Constants {
    public static String[] languageTags,displayNames;
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

    protected static String getTweetInfoField(){
        return "";
    }

    protected static String getLangField(){
        return "";
    }

    protected static String getShortTextMethodName(){
        return "";
    }

    protected static String getLongTextMethodName(){
        return "";
    }

    protected static String getLongTextFieldName(){
        return "";
    }
}