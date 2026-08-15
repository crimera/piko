package app.crimera.patches.xlite.premium

import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
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
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private fun MutableMethod.forceSubscriptionFeatureResults(): Int {
    val subscriptionChecks =
        instructions
            .filter { instruction ->
                val reference = instruction.getReference<MethodReference>()
                reference?.definingClass?.startsWith("Lcom/x/subscriptions/") == true &&
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

private fun forceMediaClassDownloadable(match: Match) {
    val fieldName =
        match.method.instructions
            .mapNotNull { it.getReference<FieldReference>() }
            .firstOrNull { it.type == "Z" }
            ?.name

    match.method.addInstructions(
        0,
        """
            const/4 v0, 0x1
            return v0
        """.trimIndent(),
    )

    if (fieldName == null) return

    val constructors = match.classDef.methods.filter { it.name == "<init>" }
    for (constructor in constructors) {
        val matchingIputs =
            constructor.instructions
                .filter { instruction ->
                    instruction.opcode == Opcode.IPUT_BOOLEAN &&
                        instruction.getReference<FieldReference>()?.name == fieldName
                }.reversed()

        for (iput in matchingIputs) {
            val register = (iput as TwoRegisterInstruction).registerA
            constructor.addInstruction(iput.location.index, "const/4 v$register, 0x1")
        }
    }
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
        name = "X-Lite: Unlock downloads",
        description = "Unlocks media downloads and offline video saving in X-Lite.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        execute {
            requireMatches(
                "X-Lite video download handler",
                XLiteVideoTabDownloadHandlerFingerprint.scopedMatchAll(),
                expectedCount = 2,
            ).forEach { match ->
                val patchedResults = match.method.forceSubscriptionFeatureResults()
                if (patchedResults == 0) {
                    throw PatchException(
                        "X-Lite video download handler has no subscription checks: " +
                            match.originalMethod,
                    )
                }
            }

            val timelineHandler = requireMatches(
                "X-Lite timeline download handler",
                XLiteDownloadEventHandlerFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).single()
            val timelinePatchedResults = timelineHandler.method.forceSubscriptionFeatureResults()
            if (timelinePatchedResults == 0) {
                throw PatchException("X-Lite timeline download handler has no subscription checks")
            }

            SubscriptionsFeaturesHasAnyPremiumFingerprint
                .scopedMatchAll()
                .forEach { match ->
                    match.method.addInstructions(
                        0,
                        """
                            const/4 v0, 0x1
                            return v0
                        """.trimIndent(),
                    )
                }

            MediaContentVideoIsDownloadableFingerprint
                .scopedMatchAll()
                .forEach(::forceMediaClassDownloadable)
            MediaContentGifIsDownloadableFingerprint
                .scopedMatchAll()
                .forEach(::forceMediaClassDownloadable)
            MediaContentImageIsDownloadableFingerprint
                .scopedMatchAllOrNull()
                ?.forEach(::forceMediaClassDownloadable)
        }
    }
