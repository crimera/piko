/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.morphe.extension.instagram.patches.hide.navigation;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;

@SuppressWarnings("unused")
public class HideNavigationButtonsPatch {

    private static final boolean HIDE_FEED;
    private static final boolean HIDE_REELS;
    private static final boolean HIDE_DIRECT;
    private static final boolean HIDE_SEARCH;
    private static final boolean HIDE_CREATE;
    private static final boolean HIDE_PROFILE;

    static {
        HIDE_FEED = Pref.hideNavigationFeed() && SettingsStatus.hideNavigationButtons;
        HIDE_REELS = Pref.hideNavigationReels() && SettingsStatus.hideNavigationButtons;
        HIDE_DIRECT = Pref.hideNavigationDirect() && SettingsStatus.hideNavigationButtons;
        HIDE_SEARCH = Pref.hideNavigationSearch() && SettingsStatus.hideNavigationButtons;
        HIDE_CREATE = Pref.hideNavigationCreate() && SettingsStatus.hideNavigationButtons;
        HIDE_PROFILE = Pref.hideNavigationProfile() && SettingsStatus.hideNavigationButtons;
    }

    /**
     * Injection point.
     *
     * @param navigationButtonsList the list of navigation buttons, as an (obfuscated) Enum type
     * @param enumNameField         the field in the nav button enum class which contains the name of the button
     * @return the patched list of navigation buttons
     */
    public static List<Object> filterNavigationButtons(
            List<Object> navigationButtonsList,
            String enumNameField
    ) throws IllegalAccessException, NoSuchFieldException {
        Iterator<Object> iterator = navigationButtonsList.iterator();
        while (iterator.hasNext()) {
            Object button = iterator.next();
            Field f = button.getClass().getDeclaredField(enumNameField);
            f.setAccessible(true);
            String name = (String) f.get(button);
            if (name == null) continue;

            if (name.equals("fragment_feed") && HIDE_FEED ||
                    name.equals("fragment_clips") && HIDE_REELS ||
                    name.equals("fragment_direct_tab") && HIDE_DIRECT ||
                    name.equals("fragment_search") && HIDE_SEARCH ||
                    name.equals("fragment_share") && HIDE_CREATE ||
                    name.equals("fragment_profile") && HIDE_PROFILE
            ) {
                iterator.remove();
            }
        }
        return navigationButtonsList;
    }
}
