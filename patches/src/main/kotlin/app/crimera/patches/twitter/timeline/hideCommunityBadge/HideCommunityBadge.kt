/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.hideCommunityBadge

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object HideCommunityBadgeFingerprint : Fingerprint(
    filters =
        listOf(
            string("CommunityBadge"),
        ),
)

val hideCommunityBadgePatch =
    bytecodePatch(
        name = "Hide community badge",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            HideCommunityBadgeFingerprint.method.apply {
                val iputObj = instructions.lastOrNull { it.opcode == Opcode.IPUT_OBJECT }
                    ?: throw PatchException("Failed to find IPUT_OBJECT in HideCommunityBadgeFingerprint")

                replaceInstruction(
                    iputObj.location.index,
                    "const/4 v0, 0x0",
                    "iput-object v0, p0, ${iputObj.fieldExtractor().definingClass}->${iputObj.fieldExtractor().name}:${iputObj.fieldExtractor().type}",
                )
            }
        }
    }
