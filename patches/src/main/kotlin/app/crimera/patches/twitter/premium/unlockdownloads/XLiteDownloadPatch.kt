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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private fun MutableMethod.forceSubscriptionFeatureResults() {
    val subscriptionChecks = instructions.filter { instruction ->
        val reference = instruction.getReference<MethodReference>()
        reference?.definingClass == "Lcom/x/subscriptions/SubscriptionsFeatures;" &&
            reference.returnType == "Z"
    }.reversed()

    subscriptionChecks.forEach { subscriptionCheck ->
        val resultIndex = subscriptionCheck.location.index + 1
        val resultInstruction = instructions.getOrNull(resultIndex) ?: return@forEach
        if (resultInstruction.opcode != Opcode.MOVE_RESULT) return@forEach

        val resultRegister = resultInstruction.registersUsed[0]
        addInstruction(resultIndex + 1, "const/16 v$resultRegister, 0x1")
    }
}

@Suppress("unused")
val xLiteDownloadPatch =
    bytecodePatch(
        name = "Unlock X-Lite downloads",
        description = "Unlocks media and offline-video downloads in X-Lite.",
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

                // The same handler calls SubscriptionsFeatures directly for both
                // Add to Offline visibility and its click-time premium gate.
                match.method.forceSubscriptionFeatureResults()
            }

            // Target 1: Bypass timeline download event handler premium gates.
            XLiteDownloadEventHandlerFingerprint.method.forceSubscriptionFeatureResults()

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
