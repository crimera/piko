/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

val addSettingsActivityPatch =
    resourcePatch(
        description = "Settings resource patch.",
    ) {
        execute {
            copyResources(
                "instagram/settings",
                ResourceGroup(
                    "drawable",
                    "piko_settings_shortcut_icon.xml",
                    "piko_ghost_icon.xml",
                ),
                ResourceGroup(
                    "raw",
                    "piko_tabler_icons_license.txt",
                ),
            )
        }
    }
