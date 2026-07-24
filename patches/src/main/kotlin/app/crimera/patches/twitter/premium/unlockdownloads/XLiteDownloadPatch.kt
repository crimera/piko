/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.unlockdownloads

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val xLiteDownloadPatch =
    bytecodePatch(
        name = "Unlock X-Lite downloads",
        description = "Unlocks media downloads in X-Lite timeline and subscription features.",
    ) {
        execute {
            // New X-Lite video-player UI: bypass the checker branch in the
            // VideoDownloadClicked handler. This is separate from p4's timeline path.
            XLitePremiumSubscriptionCheckerFingerprint.matchAllOrNull()?.forEach { match ->
                match.method.addInstructions(
                    0,
                    """
                    const/4 v0, 0x1
                    return v0
                    """.trimIndent(),
                )
            }

            XLiteVideoTabDownloadHandlerFingerprint.matchAll().forEach { match ->
                val premiumResult = match.instructionMatches[1]
                val premiumRegister =
                    (premiumResult.instruction as OneRegisterInstruction).registerA
                match.method.addInstruction(
                    premiumResult.index + 1,
                    "const/16 v$premiumRegister, 0x1",
                )
            }

            // Target 1: Bypass timeline download event handler premium gate (p4.smali)
            XLiteDownloadEventHandlerFingerprint.method.apply {
                val invokeLocations = instructions.filter { inst ->
                    val ref = inst.getReference<MethodReference>()
                    ref != null && ref.definingClass.contains("SubscriptionsFeatures") && ref.returnType == "Z"
                }.reversed()

                for (invokeInst in invokeLocations) {
                    val nextIdx = invokeInst.location.index + 1
                    if (nextIdx < instructions.size) {
                        val moveResInst = instructions[nextIdx]
                        if (moveResInst.opcode == Opcode.MOVE_RESULT) {
                            val reg = moveResInst.registersUsed[0]
                            addInstruction(
                                nextIdx + 1,
                                "const/16 v$reg, 0x1",
                            )
                        }
                    }
                }
            }

            // Target 2: Unlock SubscriptionsFeatures.hasAnyPremiumSubscription
            SubscriptionsFeaturesHasAnyPremiumFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    const/4 v0, 0x1
                    return v0
                    """.trimIndent(),
                )
            }

            // Target 3: Force all videos to be downloadable (bypass poster download restriction)
            MediaContentVideoIsDownloadableFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    const/4 v0, 0x1
                    return v0
                    """.trimIndent(),
                )
            }

            // Target 4: Force all GIFs to be downloadable (bypass poster download restriction)
            MediaContentGifIsDownloadableFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    const/4 v0, 0x1
                    return v0
                    """.trimIndent(),
                )
            }
        }
    }
