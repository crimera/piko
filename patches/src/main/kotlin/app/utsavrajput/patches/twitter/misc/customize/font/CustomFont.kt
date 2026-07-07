/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.twitter.misc.customize.font

import app.utsavrajput.patches.twitter.misc.settings.settingsPatch
import app.utsavrajput.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.utsavrajput.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val customFont =
    bytecodePatch(
        name = "Custom font",
        description = "Customise font style",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(customFontHook, settingsPatch)

        execute {
            enableSettings("customFont")
        }
    }
