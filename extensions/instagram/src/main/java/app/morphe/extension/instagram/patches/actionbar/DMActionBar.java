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

import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.UI;

public class DMActionBar {
    private static final String HIDE_ICON_NAME = "design_ic_visibility_off";
    private static final String SHOW_ICON_NAME = "design_ic_visibility";

    public static void addActionBarButton(ViewGroup viewGroup) {
        try {
            boolean togglePreference = Pref.enableGhostModeQuickToggle();
            boolean hasGhostSection = SettingsStatus.ghostSection();
            if(togglePreference && hasGhostSection){
                boolean ghostModeToggle = Pref.getTurnOnAllGhostModes();

                String iconStr = ghostModeToggle ? HIDE_ICON_NAME:SHOW_ICON_NAME;
                ImageView imageView = UI.addImageViewToViewGroup(viewGroup, iconStr, null);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            boolean ghostModeToggle= !Pref.getTurnOnAllGhostModes();
                            String iconStr = ghostModeToggle ? HIDE_ICON_NAME:SHOW_ICON_NAME;
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

        } catch (Exception e) {
            Logger.printException(() -> "addActionBarButton failure", e);
        }
    }

}