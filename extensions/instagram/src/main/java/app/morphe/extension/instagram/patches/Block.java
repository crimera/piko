/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.patches;

import java.util.List;
import java.util.Arrays;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.utils.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.SettingsStatus;

public class Block {
    private static boolean DISABLE_SUGGESTED_CONTENT;
    private static List<String> SUGGESTED_CONTENT_KEY = Arrays.asList("clips_netego","stories_netego","in_feed_survey","bloks_netego","suggested_igd_channels","suggested_top_accounts","suggested_users");
    static {
        DISABLE_SUGGESTED_CONTENT = Pref.hideSuggestedContent() && SettingsStatus.hideSuggestedContent;
    }

    // Returns an invalid string, such that json parsing fails for the key.
    public static String replaceJsonParserKey(String key){
        boolean condition = false;

        if(DISABLE_SUGGESTED_CONTENT){
            if(SUGGESTED_CONTENT_KEY.contains(key)) condition = true;
        }

        return condition?Strings.PIKO:key;
    }
}