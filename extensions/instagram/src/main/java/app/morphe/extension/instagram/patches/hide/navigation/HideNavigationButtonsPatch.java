/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.hide.navigation;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;

@SuppressWarnings("unused")
public class HideNavigationButtonsPatch {

    private static final boolean HIDE_FEED;
    private static final boolean HIDE_REELS;
    private static final boolean HIDE_DIRECT;
    private static final boolean HIDE_SEARCH;
    private static final boolean HIDE_CREATE;

    static {
        HIDE_FEED = Pref.hideNavigationFeed() && SettingsStatus.hideNavigationButtons;
        HIDE_REELS = Pref.hideNavigationReels() && SettingsStatus.hideNavigationButtons;
        HIDE_DIRECT = Pref.hideNavigationDirect() && SettingsStatus.hideNavigationButtons;
        HIDE_SEARCH = Pref.hideNavigationSearch() && SettingsStatus.hideNavigationButtons;
        HIDE_CREATE = Pref.hideNavigationCreate() && SettingsStatus.hideNavigationButtons;
    }

    // Credits to brosssh.
    // https://github.com/brosssh/morphe-patches/blob/27cc95b04b162d0df3b5722542f9fd095f42fd9d/extensions/instagram/src/main/java/app/morphe/extension/instagram/hide/navigation/HideNavigationButtonsPatch.java
    /**
     * Injection point.
     * @param navigationButtonsList the list of navigation buttons, as an (obfuscated) Enum type
     * @param buttonNameToRemove the name of the button we want to remove
     * @param enumNameField the field in the nav button enum class which contains the name of the button
     * @return the patched list of navigation buttons
     */
    public static List<Object> filterNavigationButtons(
            List<Object> navigationButtonsList,
            String enumNameField
    ) throws IllegalAccessException, NoSuchFieldException {
        List<Object> mutableList = new ArrayList<>(navigationButtonsList);

        Iterator<Object> iterator = mutableList.iterator();

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
                    name.equals("fragment_share") && HIDE_CREATE
            ) {
                iterator.remove();
            }
        }
        return mutableList;
    }
}
