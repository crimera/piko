/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.actionbar;

import android.view.ViewGroup;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.UI;

public class MainFeedActionBar {
    private static boolean PIKO_SETTINGS_ON_ACTION_BAR;
    static {
        PIKO_SETTINGS_ON_ACTION_BAR = Pref.pikoSettingsOnActionBar();
    }

    public static void addActionBarButton(ViewGroup viewGroup) {
        try {
            if(PIKO_SETTINGS_ON_ACTION_BAR) {
                UI.pikoSettingsGear(viewGroup);
            }

        } catch (Exception e) {
            Logger.printException(() -> "addActionBarButton failure", e);
        }
    }

}