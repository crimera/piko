/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.feed;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.constants.Strings;

public class MainFeedActionBarButton {
    private static boolean PIKO_SETTINGS_ON_ACTION_BAR;
    static {
        PIKO_SETTINGS_ON_ACTION_BAR = Pref.pikoSettingsOnActionBar();
    }

    public static void addActionBarButton(ViewGroup viewGroup) {
        try {
            if(SettingsStatus.ghostSection()){
                boolean ghostModeToggle = Pref.getTurnOnAllGhostModes();

                String iconStr = ghostModeToggle ? "$avd_show_password__0":"$avd_hide_password__0";
                ImageView imageView = UI.addImageViewToViewGroup(viewGroup, iconStr, null);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            boolean ghostModeToggle= !Pref.getTurnOnAllGhostModes();
                            String iconStr = ghostModeToggle ? "$avd_show_password__0":"$avd_hide_password__0";
                            Pref.setTurnOnAllGhostModes(ghostModeToggle);
                            UI.setThemedIcon(imageView,iconStr);

                            String toastStr = ghostModeToggle ? Strings.GHOST_MODES_ON : Strings.GHOST_MODES_DEFAULT;
                            Utils.showToastShort(toastStr);
                        } catch (Exception ex) {
                            Logger.printException(() -> "ghost icon click failed: ", ex);
                        }
                    }
                });
            }

            if(PIKO_SETTINGS_ON_ACTION_BAR) {
                UI.pikoSettingsGear(viewGroup);
            }

        } catch (Exception e) {
            Logger.printException(() -> "addActionBarButton failure", e);
        }
    }

}