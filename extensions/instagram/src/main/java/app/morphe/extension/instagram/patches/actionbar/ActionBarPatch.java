/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.actionbar;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.Set;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.patches.userprofile.ProfileMoreOption;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.constants.Constants;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;

import com.instagram.common.session.UserSession;

public class ActionBarPatch {


    private static void ghostModeToggle(ViewGroup viewGroup) throws Exception {
        if(SettingsStatus.ghostSection()){
            boolean ghostModeToggle = Pref.getTurnOnAllGhostModes();

            String iconStr = ghostModeToggle ? UI.DRAWABLE_EYE_STROKE_ICON:UI.DRAWABLE_EYE_ICON;
            ImageView imageView = UI.addImageViewToViewGroup(viewGroup, iconStr, null);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        boolean ghostModeToggle= !Pref.getTurnOnAllGhostModes();
                        String iconStr = ghostModeToggle ? UI.DRAWABLE_EYE_STROKE_ICON:UI.DRAWABLE_EYE_ICON;
                        Pref.setTurnOnAllGhostModes(ghostModeToggle);
                        UI.setThemedIcon(imageView,iconStr);

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

        } catch (Exception e) {
            Logger.printException(() -> "mainFeedActionBarButton failure", e);
            PikoUtils.logger(e);
        }
    }

    public static void userProfileActionBarButton(ViewGroup viewGroup, UserSession userSession, Object userObject){
        try {
            if (viewGroup == null) {
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
                Context context = viewGroup.getContext();
                UI.addImageViewToViewGroup(viewGroup, UI.DRAWABLE_INFO_ICON, () -> ProfileMoreOption.moreOptionsDailogueBox(context, userData));
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