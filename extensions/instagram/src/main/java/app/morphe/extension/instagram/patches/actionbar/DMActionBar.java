/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.actionbar;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Strings;

public class DMActionBar {
    private static final String HIDE_ICON_NAME = "$avd_hide_password__0";
    private static final String SHOW_ICON_NAME = "$avd_show_password__0";

    public static void addActionBarButton(ViewGroup viewGroup) {
        try {
            boolean togglePreference = Pref.enableGhostModeQuickToggle();
            boolean hasGhostSection = SettingsStatus.ghostSection();
            if(togglePreference && hasGhostSection){
                boolean ghostModeToggle = Pref.getTurnOnAllGhostModes();

                String iconStr = ghostModeToggle ? SHOW_ICON_NAME:HIDE_ICON_NAME;
                ImageView imageView = UI.addImageViewToViewGroup(viewGroup, iconStr, null);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            boolean ghostModeToggle= !Pref.getTurnOnAllGhostModes();
                            String iconStr = ghostModeToggle ? SHOW_ICON_NAME:HIDE_ICON_NAME;
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

        } catch (Exception e) {
            Logger.printException(() -> "addActionBarButton failure", e);
        }
    }

}