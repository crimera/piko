/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.posts.debugOverflowButton

import app.crimera.patches.instagram.misc.overflowMenuButton.posts.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.hookOverflowMenuButton
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val debugOverflowMenuButtonPatch =
    bytecodePatch(
        description = "Adds debug overflow menu button",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(hookOverflowMenuButton)
        execute {

            addOverflowMenuButtonAttributes("PIKO_DEBUG", "debugOverflowButton")
        }
    }
