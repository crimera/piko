/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.font

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val customEmojiFont =
    bytecodePatch(
        name = "Custom emoji font",
        description = "Customise emoji font style",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(customFontHook, settingsPatch)

        execute {
            enableSettings("customEmojiFont")
        }
    }
