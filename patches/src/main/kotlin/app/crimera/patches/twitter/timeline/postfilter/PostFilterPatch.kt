/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.postfilter

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.timeline.xlite.xLiteTimelineItemsHookPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val postFilterPatch =
    bytecodePatch(
        name = "Filter X-Lite posts by keyword",
        description = "Filters X-Lite posts using user-defined keywords.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(
            xLiteTimelineItemsHookPatch,
            postFilterResourcePatch,
            settingsPatch,
        )

        execute {
            enableSettings("postFilter")
        }
    }
