/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.focusLock

import app.crimera.patches.instagram.links.distractionFree.disableExplorePatch
import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.navigation.navigationBarPatch
import app.crimera.patches.instagram.misc.reels.disableReelsScrollingPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val focusLockPatch =
    bytecodePatch(
        name = "Focus Lock",
        description = "Commitment mode for cutting down on Instagram. Pick what to block (Reels, Explore) and a " +
            "duration; once locked those protections are forced on and cannot be switched off. Reels shared with " +
            "you still open. Unlocking early requires a 24 hour cooling-off period, and resetting or importing " +
            "settings is blocked while locked.",
    ) {
        dependsOn(
            settingsPatch,
            interceptUriPatch,
            navigationBarPatch,
            disableReelsScrollingPatch,
            disableExplorePatch,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("focusLock")
        }
    }
