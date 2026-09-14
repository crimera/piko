/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;

final class NavigationSettingsAccessWarning {
    interface SaveAction {
        boolean save();
    }

    private NavigationSettingsAccessWarning() {
    }

    static void show(Context context, SaveAction saveAction) {
        AlertDialog warningDialog = new AlertDialog.Builder(context)
                .setTitle(str("piko_navigation_settings_lockout_title"))
                .setMessage(str("piko_navigation_settings_lockout_message"))
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), null)
                .create();
        warningDialog.setOnShowListener(ignored ->
                warningDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            if (!saveAction.save()) return;
                            warningDialog.dismiss();
                        })
        );
        warningDialog.show();
    }
}
