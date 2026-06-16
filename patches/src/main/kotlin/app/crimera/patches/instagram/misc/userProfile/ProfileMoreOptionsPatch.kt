/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.misc.actionBar.userProfileActionBarButton.userProfileActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val profileMoreOptionsPatch =
    bytecodePatch(
        name = "More options on profile",
        description = "Adds a new button to handle user related data like copy handle, download profile picture etc",
    ) {
        dependsOn(settingsPatch, userProfileActionBarButtonPatch, userProfileButtonPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("moreOptionsOnProfile")
        }
    }
