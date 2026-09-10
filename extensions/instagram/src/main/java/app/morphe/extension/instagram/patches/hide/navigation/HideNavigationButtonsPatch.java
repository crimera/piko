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

    // A03="fragment_share" is shared by two enum constants (SHARE and CREATION,
    // the real camera/create button). Disambiguate via Enum.name() for this key only.
    private static final String AMBIGUOUS_SHARE_KEY = "fragment_share";
    private static final String CREATION_ENUM_NAME = "CREATION";
    private static final String NEWS_ENUM_NAME = "NEWS";

    private static final boolean HIDE_FEED;
    private static final boolean HIDE_REELS;
    private static final boolean HIDE_DIRECT;
    private static final boolean HIDE_SEARCH;
    private static final boolean HIDE_PROFILE;
    private static final boolean HIDE_CREATE;
    private static final boolean HIDE_NEWS;

    static {
        HIDE_FEED = Pref.hideNavigationFeed() && SettingsStatus.hideNavigationButtons;
        HIDE_REELS = Pref.hideNavigationReels() && SettingsStatus.hideNavigationButtons;
        HIDE_DIRECT = Pref.hideNavigationDirect() && SettingsStatus.hideNavigationButtons;
        HIDE_SEARCH = Pref.hideNavigationSearch() && SettingsStatus.hideNavigationButtons;
        HIDE_PROFILE = Pref.hideNavigationProfile() && SettingsStatus.hideNavigationButtons;
        HIDE_CREATE = Pref.hideNavigationCreate() && SettingsStatus.hideNavigationButtons;
        HIDE_NEWS = Pref.hideNavigationNews() && SettingsStatus.hideNavigationButtons;
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

            boolean matchesCreate = name.equals(AMBIGUOUS_SHARE_KEY)
                    && HIDE_CREATE
                    && isCreationEnum(button);
            boolean matchesNews = (name.equals("fragment_news") || isNewsEnum(button)) && HIDE_NEWS;

            if (name.equals("fragment_feed") && HIDE_FEED ||
                    name.equals("fragment_clips") && HIDE_REELS ||
                    name.equals("fragment_direct_tab") && HIDE_DIRECT ||
                    name.equals("fragment_search") && HIDE_SEARCH ||
                    matchesCreate ||
                    name.equals("fragment_profile") && HIDE_PROFILE ||
                    matchesNews
            ) {
                iterator.remove();
            }
        }

        return reorderNavigationButtons(mutableList, enumNameField);
    }

    /** Reorders the filtered list per the saved order; unknown buttons append at the end. */
    private static List<Object> reorderNavigationButtons(
            List<Object> buttons,
            String enumNameField
    ) throws NoSuchFieldException, IllegalAccessException {
        String savedOrder = Pref.navigationButtonsOrder();
        if (savedOrder == null || savedOrder.isEmpty()) return buttons;

        List<Object> remaining = new ArrayList<>(buttons);
        List<Object> sorted = new ArrayList<>(buttons.size());

        for (String key : savedOrder.split(",")) {
            Iterator<Object> it = remaining.iterator();
            while (it.hasNext()) {
                Object button = it.next();
                Field f = button.getClass().getDeclaredField(enumNameField);
                f.setAccessible(true);
                Object fieldValue = f.get(button);

                if (keyMatchesField(key, fieldValue, button)) {
                    sorted.add(button);
                    it.remove();
                    break;
                }
            }
        }
        sorted.addAll(remaining);
        return sorted;
    }

    private static boolean keyMatchesField(String savedKey, Object fieldValue, Object button) {
        if (AMBIGUOUS_SHARE_KEY.equals(savedKey)) {
            return AMBIGUOUS_SHARE_KEY.equals(fieldValue) && isCreationEnum(button);
        }
        if ("fragment_news".equals(savedKey)) {
            return "fragment_news".equals(fieldValue) || isNewsEnum(button);
        }
        return savedKey.equals(fieldValue);
    }

    private static boolean isCreationEnum(Object button) {
        return button instanceof Enum && CREATION_ENUM_NAME.equals(((Enum<?>) button).name());
    }

    private static boolean isNewsEnum(Object button) {
        return button instanceof Enum && NEWS_ENUM_NAME.equals(((Enum<?>) button).name());
    }

    /** News moved to the top action bar bypasses the IgTab list above entirely. */
    public static String filterNewsTopBarEntry(String original) {
        return "news".equals(original) && HIDE_NEWS ? null : original;
    }
}
