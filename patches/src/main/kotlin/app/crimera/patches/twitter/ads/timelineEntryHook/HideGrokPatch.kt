/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideGrokPatch =
    bytecodePatch(
        name = "Hide Grok",
        description = "Hides Grok from the sidebar, search suggestions, and timeline.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {
            // Hides Grok items.
            enableSettings("hideGrok")
        }
    }
