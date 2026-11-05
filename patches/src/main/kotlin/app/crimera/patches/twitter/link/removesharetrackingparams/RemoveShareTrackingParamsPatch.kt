/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.link.removesharetrackingparams

import app.crimera.patches.twitter.link.cleartrackingparams.AddSessionTokenFingerprint
import app.crimera.patches.twitter.link.cleartrackingparams.clearTrackingParamsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val removeShareTrackingParamsPatch =
    bytecodePatch(
        name = "Remove share tracking params",
        description = "Removes the a= and s= attribution parameters from shared post links",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(clearTrackingParamsPatch)

        execute {
            // The app appends `s=` in this method and may already have appended `a=` to the
            // input link. Sanitize the input before the early return added by `Clear tracking
            // params`, so both parameters are gone from the returned link.
            AddSessionTokenFingerprint.method.addInstructions(
                0,
                """
                invoke-static/range {p0 .. p0}, $PATCHES_DESCRIPTOR/links/Urls;->removeTrackingParams(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                """.trimIndent(),
            )
        }
    }
