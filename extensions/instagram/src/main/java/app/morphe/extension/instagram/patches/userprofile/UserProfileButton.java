/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import android.view.ViewGroup;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.userprofile.ProfileMoreOption;

public class UserProfileButton {
    private static boolean PIKO_SETTINGS_ON_ACTION_BAR;
    static {
        PIKO_SETTINGS_ON_ACTION_BAR = Pref.pikoSettingsOnActionBar();
    }

    public static void addButtons(ViewGroup viewGroup, Object object) {
        try {
            ProfileInfo profileInfo = new ProfileInfo(object);
            Boolean isSelfProfile = profileInfo.isSelfProfile();

            if(!Pref.enableMoreOptionsOnProfileQuickToggle() && Pref.isMoreOptionsOnProfilePatched()){
                ProfileMoreOption.addProfileMoreOptionsButton(viewGroup, profileInfo);
            }

            if (isSelfProfile && !PIKO_SETTINGS_ON_ACTION_BAR) {
                UI.pikoSettingsButton(viewGroup);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to add piko button: ", e);
        }

    }
}

