/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.actionbar;

import android.view.ViewGroup;
import android.content.Context;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.userprofile.ProfileMoreOption;
import app.morphe.extension.instagram.entity.UserData;

public class UserProfileActionBar {

    public static void addActionBarButton(ViewGroup viewGroup, Object userObject){
        try {
            if(Pref.enableMoreOptionsOnProfileQuickToggle()) {
                if (viewGroup == null) {
                    return;
                }

                Context context = viewGroup.getContext();
                UserData userData = new UserData(userObject);
                UI.addImageViewToViewGroup(viewGroup, UI.DRAWABLE_INFO_ICON, () -> ProfileMoreOption.moreOptionsDailogueBox(context, userData));
            }

        } catch (Exception e) {
            Logger.printException(() -> "Failed to add profile more options: ", e);
        }
    }
}

