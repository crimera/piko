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

// Credits to @iKirby
@Suppress("unused")
val removeViewCountPatch =
    bytecodePatch(
        name = "Remove view count",
        description = "Removes the view count from the bottom of tweets",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(featureFlagPatch, settingsPatch)
        execute {

            flagSettings("viewCount")
            enableSettings("hideViewCount")
        }
    }
