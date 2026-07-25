package app.crimera.patches.xlite.premium

import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private fun MutableMethod.forceSubscriptionFeatureResults(): Int {
    val subscriptionChecks =
        instructions
            .filter { instruction ->
                val reference = instruction.getReference<MethodReference>()
                reference?.definingClass == "Lcom/x/subscriptions/SubscriptionsFeatures;" &&
                    reference.returnType == "Z"
            }.reversed()

    var patchedResults = 0
    subscriptionChecks.forEach { subscriptionCheck ->
        val resultIndex = subscriptionCheck.location.index + 1
        val resultInstruction = instructions.getOrNull(resultIndex) ?: return@forEach
        if (resultInstruction.opcode != Opcode.MOVE_RESULT) return@forEach

        val resultRegister = resultInstruction.registersUsed[0]
        addInstruction(resultIndex + 1, "const/16 v$resultRegister, 0x1")
        patchedResults++
    }
    return patchedResults
}

private fun requireMatches(
    label: String,
    matches: Collection<Match>,
    expectedCount: Int,
): Collection<Match> {
    if (matches.size == expectedCount) return matches
    throw PatchException(
        "Expected $expectedCount $label matches, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}

@Suppress("unused")
val xLiteDownloadPatch =
    bytecodePatch(
        name = "Unlock X-Lite downloads",
        description = "Unlocks media downloads and offline video saving in X-Lite.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        execute {
            requireMatches(
                "X-Lite premium subscription checker",
                XLitePremiumSubscriptionCheckerFingerprint.matchAll(),
                expectedCount = 2,
            ).forEach { match ->
                match.method.addInstructions(
                    0,
                    """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent(),
                )
            }

            requireMatches(
                "X-Lite video download handler",
                XLiteVideoTabDownloadHandlerFingerprint.matchAll(),
                expectedCount = 2,
            ).forEach { match ->
                val premiumResult = match.instructionMatches[1]
                val premiumRegister =
                    (premiumResult.instruction as OneRegisterInstruction).registerA
                match.method.addInstruction(
                    premiumResult.index + 1,
                    "const/16 v$premiumRegister, 0x1",
                )
                if (match.method.forceSubscriptionFeatureResults() == 0) {
                    throw PatchException(
                        "X-Lite video download handler has no subscription checks: " +
                            match.originalMethod,
                    )
                }
            }

            if (XLiteDownloadEventHandlerFingerprint.method.forceSubscriptionFeatureResults() == 0) {
                throw PatchException("X-Lite timeline download handler has no subscription checks")
            }

            SubscriptionsFeaturesHasAnyPremiumFingerprint.method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent(),
            )
            MediaContentVideoIsDownloadableFingerprint.method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent(),
            )
            MediaContentGifIsDownloadableFingerprint.method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent(),
            )
        }
    }
