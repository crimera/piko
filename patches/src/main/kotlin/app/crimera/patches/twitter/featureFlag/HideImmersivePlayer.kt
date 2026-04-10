/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.featureFlag

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.featureFlagPatch
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.flagSettings
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
