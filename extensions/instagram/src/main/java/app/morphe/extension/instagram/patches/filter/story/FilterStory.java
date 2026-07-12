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
import app.morphe.extension.instagram.entity.UserData;

import app.morphe.extension.crimera.PikoUtils;

@SuppressWarnings("unused")
public class FilterStory {

    private static Set<String> REEL_TYPES;
    private static Set<String> USER_TYPES;
    private static Integer MIN_STORY_ITEM;
    private static Integer MAX_STORY_ITEM;

    static {
        REEL_TYPES = Pref.filterStoryByType();
        USER_TYPES = Pref.filterStoryByUserType();
        MIN_STORY_ITEM = Pref.filterStoryByMinStoryItems();
        MAX_STORY_ITEM = Pref.filterStoryByMaxStoryItems();
    }

    public static Object filter(Object itemObject) {
        try {
            ReelResponseItem reelResponseItem = new ReelResponseItem(itemObject);

            String reelType = reelResponseItem.getReelType();
            if(REEL_TYPES.contains(reelType)) return null;

            UserData userData = reelResponseItem.getUserData();
            if(USER_TYPES.contains("verified") && userData.isVerified()) return null;
            if(USER_TYPES.contains("unverified") && !userData.isVerified()) return null;

            Integer mediaCount = reelResponseItem.getMediaCount();
            if(mediaCount < MIN_STORY_ITEM) return null;
            if(mediaCount > MAX_STORY_ITEM) return null;

        } catch (Exception e) {
            PikoUtils.logger(e.toString());
        }
        return itemObject;
    }
}
