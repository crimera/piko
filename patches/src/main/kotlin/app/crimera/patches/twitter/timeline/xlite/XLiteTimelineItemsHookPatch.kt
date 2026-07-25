/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.xlite

import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

internal val xLiteTimelineItemsHookPatch =
    bytecodePatch(
        description = "Hooks the shared X-Lite timeline item list.",
    ) {
        execute {
            val matches = XLiteTimelineSuccessFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite timeline Success constructor, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().method.addInstructions(
                0,
                """
                    invoke-static {p2}, $PATCHES_DESCRIPTOR/TimelineEntry;->filterUrtTimelineItems(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object p2
                """.trimIndent(),
            )
        }
    }
