/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.removePremiumUpsell

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object RemovePremiumUpsellFingerprint : Fingerprint(
    filters =
        listOf(
            string("getPremiumUpsellConfig"),
        ),
)

val removePremiumUpsellPatch =
    bytecodePatch(
        name = "Remove premium upsell",
    ) {
        compatibleWith(COMPATIBILITY_X)
        execute {
            RemovePremiumUpsellFingerprint.method.apply {
                val invokeVirtual = instructions.firstOrNull { it.opcode == Opcode.INVOKE_VIRTUAL }
                    ?: throw PatchException("Failed to find INVOKE_VIRTUAL in RemovePremiumUpsellFingerprint")

                replaceInstruction(
                    invokeVirtual.location.index,
                    "const/4 v0, 0x0",
                )
            }
        }
    }
