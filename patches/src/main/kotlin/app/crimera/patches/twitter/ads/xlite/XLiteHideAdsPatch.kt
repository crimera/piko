/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.ads.xlite

import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val xLiteHideAdsPatch =
    bytecodePatch(
        name = "Remove X-Lite Ads",
        description = "Hooks UrtTimelineState\$Success to filter promoted posts and modules from X-Lite timeline.",
    ) {
        execute {
            val method = XLiteTimelineSuccessFingerprint.method

            method.addInstructions(
                0,
                """
                invoke-static {p2}, $PATCHES_DESCRIPTOR/TimelineEntry;->filterUrtTimelineItems(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object p2
                """.trimIndent(),
            )
        }
    }
