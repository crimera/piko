/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.actionbar;

import android.view.ViewGroup;

import app.morphe.extension.shared.Logger;

public class DMActionBar {
    public static void addActionBarButton(ViewGroup viewGroup) {
        try {
            GhostModeQuickToggle.addToDirectThreadActionBar(viewGroup);
        } catch (Exception e) {
            Logger.printException(() -> "addActionBarButton failure", e);
        }
    }

}
