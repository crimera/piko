/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.hdr

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.patch.bytecodePatch

// Both flags are just server-controlled brightness boosts on top of the normal image/video
// pipeline (see #1817), not a rendering mode the app needs elsewhere - forcing them off client
// side is enough, no bytecode fingerprinting against app code required.
@Suppress("unused")
val removeForcedHdrPatch =
    bytecodePatch(
        name = "Remove forced HDR",
        description = "Disables Instagram's always-on Ultra HDR photos and the forced SDR brightness boost on Reels/videos on HDR-capable screens.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(hookFlagsPatch)

        execute {
            addFlags("hdrFlags")
        }
    }
