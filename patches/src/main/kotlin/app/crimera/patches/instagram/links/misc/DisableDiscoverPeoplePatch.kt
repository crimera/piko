/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableDiscoverPeoplePatch =
    bytecodePatch(
        name = "Disable discover people",
        description = "Disables discover people section on user profile",
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("disableDiscoverPeople")
        }
    }
