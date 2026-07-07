/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.instagram.links.privacy

import app.utsavrajput.patches.instagram.links.interceptUriPatch
import app.utsavrajput.patches.instagram.misc.actionBar.dmActionBarButton.dmActionBarButtonPatch
import app.utsavrajput.patches.instagram.misc.settings.settingsPatch
import app.utsavrajput.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.utsavrajput.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val viewLiveAnonymouslyPatch =
    bytecodePatch(
        name = "View live anonymously",
    ) {
        dependsOn(settingsPatch, interceptUriPatch, dmActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("viewLiveAnonymously")
        }
    }
