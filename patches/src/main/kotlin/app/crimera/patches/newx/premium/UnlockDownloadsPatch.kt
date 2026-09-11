package app.crimera.patches.newx.premium

import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
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

// Each video-tab handler has three direct subscription checks.
private const val VIDEO_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT = 3
// The shared URT/Compose timeline handler has nine checks.
private const val TIMELINE_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT = 9

// Override subscription results at the actual media-action call sites.
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

private fun forceBooleanResult(match: Match) {
    match.method.addInstructions(
        0,
        """
            const/4 v0, 0x1
            return v0
        """.trimIndent(),
    )
}

private fun forceMediaClassDownloadable(match: Match) {
    val booleanFields =
        match.method.instructions
            .mapNotNull { it.getReference<FieldReference>() }
            .filter { it.type == "Z" }
            .distinctBy(FieldReference::toString)
    if (booleanFields.size != 1) {
        throw PatchException(
            "Expected one NewX downloadable boolean field in ${match.originalMethod}, found " +
                "${booleanFields.size}: ${booleanFields.joinToString()}",
        )
    }
    val downloadableField = booleanFields.single()

    forceBooleanResult(match)

    val constructors = match.classDef.methods.filter { it.name == "<init>" }
    if (constructors.isEmpty()) {
        throw PatchException("NewX downloadable media class has no constructors: ${match.originalClassDef.type}")
    }

    var patchedConstructorWrites = 0
    for (constructor in constructors) {
        val matchingIputs =
            constructor.instructions
                .filter { instruction ->
                    instruction.opcode == Opcode.IPUT_BOOLEAN &&
                        instruction.getReference<FieldReference>()?.toString() == downloadableField.toString()
                }.reversed()

        for (iput in matchingIputs) {
            val register =
                (iput as? TwoRegisterInstruction)?.registerA
                    ?: throw PatchException(
                        "NewX downloadable field write has an unexpected instruction shape: $iput",
                    )
            constructor.addInstruction(iput.location.index, "const/4 v$register, 0x1")
            patchedConstructorWrites++
        }
    }
    if (patchedConstructorWrites == 0) {
        throw PatchException(
            "NewX downloadable field is never initialized in a constructor: $downloadableField",
        )
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
val newXDownloadPatch =
    bytecodePatch(
        name = "NewX: Unlock downloads",
        description = "Unlocks media downloads and offline video saving in NewX.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        execute {
            val videoDownloadMatches =
                NewXVideoTabDownloadHandlerFingerprint.scopedMatchAllOrNull().orEmpty()
            if (videoDownloadMatches.isNotEmpty()) {
                requireMatches(
                    "NewX video download handler",
                    videoDownloadMatches,
                    expectedCount = 2,
                ).forEach { match ->
                    val patchedResults = match.method.forceSubscriptionFeatureResults()
                    if (patchedResults != VIDEO_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT) {
                        throw PatchException(
                            "Expected $VIDEO_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT subscription checks in " +
                                "the NewX video download handler, found $patchedResults: " +
                                match.originalMethod,
                        )
                    }
                }
            }

            val timelineHandler = requireExactlyOne(
                "NewX timeline download handler",
                NewXDownloadEventHandlerFingerprint.scopedMatchAll(),
            )
            val timelinePatchedResults = timelineHandler.method.forceSubscriptionFeatureResults()
            if (timelinePatchedResults != TIMELINE_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT) {
                throw PatchException(
                    "Expected $TIMELINE_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT subscription checks in " +
                        "the NewX timeline download handler, found $timelinePatchedResults: " +
                        timelineHandler.originalMethod,
                )
            }

            requireMatches(
                "NewX premium subscription checker",
                SubscriptionsFeaturesHasAnyPremiumFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceBooleanResult)
            requireMatches(
                "NewX offline-video premium checker",
                SubscriptionsFeaturesOfflinePremiumFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceBooleanResult)
            requireMatches(
                "NewX offline-video feature gate",
                SubscriptionsFeaturesOfflineVideoEnabledFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceBooleanResult)

            requireMatches(
                "NewX video media downloadability method",
                MediaContentVideoIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
            requireMatches(
                "NewX GIF media downloadability method",
                MediaContentGifIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
            requireMatches(
                "NewX image media downloadability method",
                MediaContentImageIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
        }
    }
