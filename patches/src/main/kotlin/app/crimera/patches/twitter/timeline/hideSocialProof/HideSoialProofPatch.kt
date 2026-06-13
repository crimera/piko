/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.hideSocialProof

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.ExternalLabel
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object HideSocialProofFingerprint : Fingerprint(
    filters =
        listOf(
            string("SocialContext"),
        ),
)

val hideSocialProofPatch =
    bytecodePatch(
        name = "Hide social proof",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            HideSocialProofFingerprint.method.apply {
                val const4 = instructions.firstOrNull { it.opcode == Opcode.CONST_4 }
                    ?: throw PatchException("Failed to find CONST_4 in HideSocialProofFingerprint")

                labels =
                    listOf(
                        ExternalLabel("piko", const4),
                    )
                replaceInstruction(
                    0,
                    "invoke-static {}, $PATCHES_DESCRIPTOR/TimelinePatch;->hideSocialProof()Z",
                    "move-result v0",
                    "if-nez v0, :piko",
                    "return-void",
                )
            }
            enableSettings("hideSocialProof")
        }
    }
