/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import android.view.ViewGroup;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.userprofile.ProfileMoreOption;

public class UserProfileButton {

    public static void addButtons(ViewGroup viewGroup, Object object) {
        try {
            ProfileInfo profileInfo = new ProfileInfo(object);
            Boolean isSelfProfile = profileInfo.isSelfProfile();

            Set<String> userProfileABPref = Pref.userProfileActionBarButtons();

            if (!userProfileABPref.contains(Constants.AB_SETTINGS_ICON) && isSelfProfile){
                UI.pikoSettingsButton(viewGroup);
            }
            if(!userProfileABPref.contains(Constants.AB_PROFILE_INFO_ICON) && Pref.isMoreOptionsOnProfilePatched()){
                ProfileMoreOption.addProfileMoreOptionsButton(viewGroup, profileInfo);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to add piko button: ", e);
        }

    }
}

