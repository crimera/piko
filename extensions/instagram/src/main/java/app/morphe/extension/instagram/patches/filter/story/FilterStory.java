/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.filter.story;


import java.util.Set;

import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.entity.ReelResponseItem;

import app.morphe.extension.crimera.PikoUtils;

@SuppressWarnings("unused")
public class FilterStory {

    private static Set<String> REEL_TYPES;
    static {
        REEL_TYPES = Pref.filterStoryByType();
    }

    public static Object filter(Object itemObject) {
        try {
            ReelResponseItem reelResponseItem = new ReelResponseItem(itemObject);
            String reelType = reelResponseItem.getReelType();

            if(REEL_TYPES.contains(reelType)) {
                return null;
            };

        } catch (Exception e) {
            PikoUtils.logger(e.toString());
        }
        return itemObject;
    }
}
