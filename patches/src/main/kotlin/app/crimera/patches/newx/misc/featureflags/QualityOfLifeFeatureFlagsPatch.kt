/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.featureflags

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.utils.setNewXFeatureFlag
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXQualityOfLifeFeatureFlagsPatch =
    bytecodePatch(
        name = "NewX: Enable quality-of-life feature flags",
        description = "Enables profile Photos and Videos tabs and sharing post screenshots.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(featureFlagPatch)

        execute {
            setNewXFeatureFlag("x_lite_profile_photos_videos_tabs_enabled", enabled = true)
            setNewXFeatureFlag("x_lite_share_post_screenshot_enabled", enabled = true)
            setNewXFeatureFlag("grok_android_analyze_timelines_enabled", enabled = true)
        }
    }
