/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.timeline.hideNavbarBadges

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private object setBadgeNumberFingerprint : Fingerprint(
    definingClass = "/BadgeableTabView;",
    name = "setBadgeNumber",
)

@Suppress("unused")
val hideNavBarBadgesPatch =
    bytecodePatch(
        name = "Hide badges from navigation bar icons",
        description = "Hides notification nudges & counts from navigation bar icons",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            setBadgeNumberFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    invoke-static {p1}, ${Constants.PREF_DESCRIPTOR};->navbarBadgeCount(I)I
                    move-result p1
                    """.trimIndent(),
                )
                enableSettings("hideNavbarBadge")
            }
        }
    }
