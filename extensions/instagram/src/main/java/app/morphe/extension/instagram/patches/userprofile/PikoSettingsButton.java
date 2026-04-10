/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.patches.userprofile;

import app.morphe.extension.instagram.constants.UI;
import android.view.ViewGroup;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.entity.ProfileInfo;

public class PikoSettingsButton {

    public static void addPikoSettingsButton(ViewGroup viewGroup, Object object) {
        try {
            ProfileInfo profileInfo = new ProfileInfo(object);
            Boolean isSelfProfile = profileInfo.isSelfProfile();

            if (isSelfProfile) {
                UI.pikoSettingsButton(viewGroup);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to add piko button: ", e);
        }

    }
}

