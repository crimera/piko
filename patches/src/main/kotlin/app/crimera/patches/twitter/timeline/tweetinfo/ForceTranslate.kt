/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.timeline.tweetinfo

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val forceTranslate =
    bytecodePatch(
        name = "Force enable translate",
        description = "Get translate option for all posts",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, tweetInfoHook)

        execute {
            SettingsStatusLoadFingerprint.enableSettings("forceTranslate")
        }
    }
