/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.actionbar;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.patches.userprofile.ProfileMoreOption;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.constants.Constants;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

import com.instagram.common.session.UserSession;

public class ActionBarPatch {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Set<ImageView> GHOST_MODE_ICONS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final SharedPreferences.OnSharedPreferenceChangeListener GHOST_MODE_LISTENER =
            (sharedPreferences, key) -> {
                if (!Settings.TURN_ON_ALL_GHOST_MODES.key.equals(key)) {
                    return;
                }
                try {
                    updateGhostModeIcons(Pref.getTurnOnAllGhostModes());
                } catch (RuntimeException exception) {
                    Logger.printException(
                            () -> "Failed reading ghost mode preference: ",
                            exception
                    );
                }
            };
    private static boolean ghostModeListenerRegistered;

    private static void runOnMainThread(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            MAIN_HANDLER.post(action);
        }
    }

    private static synchronized void registerGhostModeListener() {
        if (ghostModeListenerRegistered) {
            return;
        }
        try {
            ghostModeListenerRegistered =
                    SharedPref.registerOnSharedPreferenceChangeListener(GHOST_MODE_LISTENER);
        } catch (RuntimeException exception) {
            Logger.printException(
                    () -> "Failed registering ghost mode preference listener: ",
                    exception
            );
        }
    }

    private static void updateGhostModeIcons(boolean enabled) {
        runOnMainThread(() -> {
            List<ImageView> currentIcons;
            synchronized (GHOST_MODE_ICONS) {
                currentIcons = new ArrayList<>(GHOST_MODE_ICONS);
            }

            String icon = enabled ? UI.DRAWABLE_EYE_STROKE_ICON : UI.DRAWABLE_EYE_ICON;
            for (ImageView imageView : currentIcons) {
                try {
                    UI.setThemedIcon(imageView, icon);
                } catch (RuntimeException exception) {
                    Logger.printException(
                            () -> "Failed updating ghost mode icon: ",
                            exception
                    );
                }
            }
        });
    }

    private static void ghostModeToggle(ViewGroup viewGroup) throws Exception {
        if(SettingsStatus.ghostSection()){
            boolean ghostModeToggle = Pref.getTurnOnAllGhostModes();

            String iconStr = ghostModeToggle ? UI.DRAWABLE_EYE_STROKE_ICON:UI.DRAWABLE_EYE_ICON;
            ImageView imageView = UI.addImageViewToViewGroup(viewGroup, iconStr, null);
            if (imageView == null) {
                return;
            }
            synchronized (GHOST_MODE_ICONS) {
                GHOST_MODE_ICONS.add(imageView);
            }
            registerGhostModeListener();
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        boolean ghostModeToggle= !Pref.getTurnOnAllGhostModes();
                        if (!Pref.setTurnOnAllGhostModes(ghostModeToggle)) {
                            throw new IllegalStateException("Failed saving ghost mode preference");
                        }
                        if (!ghostModeListenerRegistered) {
                            updateGhostModeIcons(ghostModeToggle);
                        }

                        String toastStr = ghostModeToggle ? str("piko_ghost_modes_on") : str("piko_ghost_modes_default");
                        Utils.showToastShort(toastStr);
                    } catch (Exception ex) {
                        Logger.printException(() -> "ghost icon click failed: ", ex);
                    }
                }
            });
        }

    }

    public static void mainFeedActionBarButton(ViewGroup viewGroup) {
        try {
            if (viewGroup == null) {
                return;
            }

            Set<String> pref = Pref.mainFeedActionBarButtons();

            if(pref.contains(Constants.AB_GHOST_MODE_ICON)) {
                ghostModeToggle(viewGroup);
            }

            if(pref.contains(Constants.AB_SETTINGS_ICON)) {
                UI.pikoSettingsGear(viewGroup);
            }

            HomeActionBarLayout.observe(
                    viewGroup,
                    NavigationBarPatch::effectiveNotificationsVisibility
            );

        } catch (Exception e) {
            Logger.printException(() -> "mainFeedActionBarButton failure", e);
            PikoUtils.logger(e);
        }
    }

    public static String filterHomeAction(String action) {
        if ("share".equals(action) && Pref.getHideHomeCreateButton()) return null;
        if ("news".equals(action) && Pref.getHideHomeNotificationsButton()) return null;
        return action;
    }

    public static void hideProfileCreateButton(View view, int drawableId) {
        if (drawableId != 0 && !Pref.userProfileActionBarButtons().contains(Constants.AB_CREATE)
                && drawableId == ResourceUtils.getIdentifier(ResourceType.DRAWABLE, "instagram_add_outline_24")) {
            view.setVisibility(View.GONE);
        }
    }

    public static void userProfileActionBarButton(Activity activity, ViewGroup viewGroup, UserSession userSession, Object userObject){
        try {
            if (activity == null || viewGroup == null) {
                return;
            }

            Set<String> pref = Pref.userProfileActionBarButtons();

            UserData userData = new UserData(userObject);
            Boolean isSelfProfile = userData.getUserId().equals(userSession.getUserId());

            if(pref.contains(Constants.AB_SETTINGS_ICON) && isSelfProfile) {
                UI.pikoSettingsGear(viewGroup);
            }

            if(pref.contains(Constants.AB_GHOST_MODE_ICON) && isSelfProfile) {
                ghostModeToggle(viewGroup);
            }

            if(pref.contains(Constants.AB_PROFILE_INFO_ICON)) {
                UI.addImageViewToViewGroup(viewGroup, UI.DRAWABLE_INFO_ICON, () -> ProfileMoreOption.moreOptionsDailogueBox(activity, userData));
            }


        } catch (Exception e) {
            Logger.printException(() -> "userProfileActionBarButton: ", e);
            PikoUtils.logger(e);
        }
    }

    public static void chatActionBarButton(ViewGroup viewGroup) {
        try {
            if (viewGroup == null) {
                return;
            }

            Set<String> pref = Pref.chatActionBarButtons();

            if(pref.contains(Constants.AB_SETTINGS_ICON)) {
                UI.pikoSettingsGear(viewGroup);
            }

            if(pref.contains(Constants.AB_GHOST_MODE_ICON)) {
                ghostModeToggle(viewGroup);
            }

        } catch (Exception e) {
            Logger.printException(() -> "chatActionBarButton:", e);
        }
    }

    public static void inboxActionBarButton(ViewGroup viewGroup) {
        try {
            if (viewGroup == null) {
                return;
            }

            Set<String> pref = Pref.inboxActionBarButtons();

            if(pref.contains(Constants.AB_SETTINGS_ICON)) {
                UI.pikoSettingsGear(viewGroup);
            }

            if(pref.contains(Constants.AB_GHOST_MODE_ICON)) {
                ghostModeToggle(viewGroup);
            }

        } catch (Exception e) {
            Logger.printException(() -> "inboxActionBarButton:", e);
        }
    }

}
