/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.twitter.featureFlag

import app.utsavrajput.patches.twitter.featureFlag.featureFlagPatch.featureFlagPatch
import app.utsavrajput.patches.twitter.misc.settings.settingsPatch
import app.utsavrajput.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.utsavrajput.patches.twitter.utils.enableSettings
import app.utsavrajput.patches.twitter.utils.flagSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideImmersivePlayer =
    bytecodePatch(
        name = "Hide immersive player",
        description = "Removes swipe up for more videos in video player",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            flagSettings("immersivePlayer")

            enableSettings("hideImmersivePlayer")
        }
    }
