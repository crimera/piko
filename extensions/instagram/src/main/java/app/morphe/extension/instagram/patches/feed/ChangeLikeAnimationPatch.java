/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.feed;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

public class ChangeLikeAnimationPatch {
    private static String CHANGE_LIKE_ANIMATION;
    static{
        CHANGE_LIKE_ANIMATION = Pref.changeLikeAnimation();
    }

    // This is required in order to change the like animation class enum type on runtime.
    private static boolean checkPrefAnimation(){
        return CHANGE_LIKE_ANIMATION == null || "ARES_LIKE_ACTIVATION".equals(CHANGE_LIKE_ANIMATION);
    }

    public static Object changeLikeAnimation(Object defaultAnimation){
        try {
            if (ChangeLikeAnimationPatch.checkPrefAnimation()) {
                return null;
            }

            Entity entity = new Entity();
            Class<?> animationEnumClass = Class.forName("className");
            Object likeAnimation = entity.getMethod(
                    animationEnumClass,
                    "valueOf",
                    CHANGE_LIKE_ANIMATION
            );
            return likeAnimation;

        } catch (Exception e) {
            Logger.printException(() -> "changeLikeAnimation failure", e);
        }
        return defaultAnimation;
    }

}