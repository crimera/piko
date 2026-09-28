/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.feed;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;

public class ChangeLikeAnimationPatch {
    public static final String RANDOM = "RANDOM";
    private static final String DEFAULT = "ARES_LIKE_ACTIVATION";
    private static final String LAST_RANDOM = "last_random_like_animation";
    private static final String CHANGE_LIKE_ANIMATION = Pref.changeLikeAnimation();
    private static String sessionAnimation;

    public static void onActivityCreated() {
        // Removing a task can leave the process, and its random choice, alive.
        if (RANDOM.equals(Pref.changeLikeAnimation())) {
            SettingsRestart.requestRestartOnTaskRemoved();
        }
    }

    private static Class<?> animationClass() throws ClassNotFoundException {
        return Class.forName("className");
    }

    public static Object changeLikeAnimation(Object defaultAnimation){
        try {
            return selectedAnimation();
        } catch (Exception e) {
            Logger.printException(() -> "changeLikeAnimation failure", e);
        }
        return defaultAnimation;
    }

    public static Object changeRenderAnimation(Object defaultAnimation) {
        try {
            Object animation = selectedAnimation();
            return animation == null ? null : mapAnimation(animation);
        } catch (Exception e) {
            Logger.printException(() -> "changeRenderAnimation failure", e);
            return defaultAnimation;
        }
    }

    private static Object selectedAnimation() throws Exception {
        if (CHANGE_LIKE_ANIMATION == null || DEFAULT.equals(CHANGE_LIKE_ANIMATION)) return null;
        String name = RANDOM.equals(CHANGE_LIKE_ANIMATION)
                ? sessionAnimation() : CHANGE_LIKE_ANIMATION;
        if (DEFAULT.equals(name)) return null;
        return new Entity().getMethod(animationClass(), "valueOf", name);
    }

    private static Object mapAnimation(Object animation) {
        // Replaced with Instagram's mapping from metadata to rendering animations.
        return null;
    }

    private static synchronized String sessionAnimation() throws ClassNotFoundException {
        if (sessionAnimation == null) {
            sessionAnimation = randomAnimation(SharedPref.getStringPref(LAST_RANDOM, ""));
            SharedPref.setStringPref(LAST_RANDOM, sessionAnimation);
        }
        return sessionAnimation;
    }

    private static String randomAnimation(String previous) throws ClassNotFoundException {
        List<String> candidates = new ArrayList<>();
        Set<String> available = new HashSet<>();
        for (Object value : animationClass().getEnumConstants()) {
            available.add(((Enum<?>) value).name());
        }
        for (String name : ResourceUtils.getStringArray("piko_array_change_like_animation_val")) {
            if (DEFAULT.equals(name) || RANDOM.equals(name)) continue;
            if (available.contains(name)) candidates.add(name);
        }
        if (candidates.size() > 1) candidates.remove(previous);
        return candidates.isEmpty() ? DEFAULT
                : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    public static String previewAnimation(String selection) throws ClassNotFoundException {
        // Trying Random in the dialog must not change the animation used by the feed.
        return RANDOM.equals(selection) ? randomAnimation("") : selection;
    }

    public static Drawable previewDrawable(Context context, String animation) {
        // Replaced by the patch with Instagram's own animation drawable factory.
        return null;
    }

    public static Drawable defaultPreviewDrawable(Context context) {
        // Replaced with the native default heart factory and its color initialization.
        return null;
    }

    public static void startPreview(Drawable drawable) {
        // Replaced by the patch with the native playback method.
    }

    public static void stopPreview(Drawable drawable) {
        // Replaced by the patch with the native stop method.
    }
}
