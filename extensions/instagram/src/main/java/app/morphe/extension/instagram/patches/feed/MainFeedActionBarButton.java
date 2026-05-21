/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.feed;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.UI;

public class MainFeedActionBarButton {

    public static void addActionBarButton(ViewGroup viewGroup) {
        try {
            UI.pikoSettingsGear(viewGroup);

        } catch (Exception e) {
            Logger.printException(() -> "addActionBarButton failure", e);
        }
    }

}