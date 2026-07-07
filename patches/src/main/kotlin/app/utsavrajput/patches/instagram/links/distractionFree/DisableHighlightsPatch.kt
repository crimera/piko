/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.instagram.links.distractionFree

import app.utsavrajput.patches.instagram.links.interceptUriPatch
import app.utsavrajput.patches.instagram.misc.settings.settingsPatch
import app.utsavrajput.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.utsavrajput.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableHighlightsPatch =
    bytecodePatch(
        name = "Disable highlights",
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("disableHighlights")
        }
    }
