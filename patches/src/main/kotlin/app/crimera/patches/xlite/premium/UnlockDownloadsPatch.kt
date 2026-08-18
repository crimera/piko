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

// ALPHA-ONLY PATH: each video-tab handler has three direct subscription checks.
private const val VIDEO_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT = 3
// ALPHA + BETA PATH: the shared URT/Compose timeline handler has nine checks.
private const val TIMELINE_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT = 9

// ALPHA + BETA: override subscription results at the actual media-action call sites.
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
            "Expected one X-Lite downloadable boolean field in ${match.originalMethod}, found " +
                "${booleanFields.size}: ${booleanFields.joinToString()}",
        )
    }
    val downloadableField = booleanFields.single()

    forceBooleanResult(match)

    val constructors = match.classDef.methods.filter { it.name == "<init>" }
    if (constructors.isEmpty()) {
        throw PatchException("X-Lite downloadable media class has no constructors: ${match.originalClassDef.type}")
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
                        "X-Lite downloadable field write has an unexpected instruction shape: $iput",
                    )
            constructor.addInstruction(iput.location.index, "const/4 v$register, 0x1")
            patchedConstructorWrites++
        }
    }
    if (patchedConstructorWrites == 0) {
        throw PatchException(
            "X-Lite downloadable field is never initialized in a constructor: $downloadableField",
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
val xLiteDownloadPatch =
    bytecodePatch(
        name = "X-Lite: Unlock downloads",
        description = "Unlocks media downloads and offline video saving in X-Lite.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        execute {
            // ALPHA PATH: patches the legacy video-tab download callbacks below.
            // BETA PATH: this is intentionally empty; shared hooks below still must execute.
            // TODO: Remove this fingerprint chain when alpha compatibility is deprecated.
            val videoDownloadMatches =
                XLiteVideoTabDownloadHandlerFingerprint.scopedMatchAllOrNull().orEmpty()
            if (videoDownloadMatches.isNotEmpty()) {
                requireMatches(
                    "X-Lite video download handler",
                    videoDownloadMatches,
                    expectedCount = 2,
                ).forEach { match ->
                    val patchedResults = match.method.forceSubscriptionFeatureResults()
                    if (patchedResults != VIDEO_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT) {
                        throw PatchException(
                            "Expected $VIDEO_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT subscription checks in " +
                                "the X-Lite video download handler, found $patchedResults: " +
                                match.originalMethod,
                        )
                    }
                }
            }

            // ALPHA + BETA PATH: shared URT/Compose timeline media-action handler.
            val timelineHandler = requireMatches(
                "X-Lite timeline download handler",
                XLiteDownloadEventHandlerFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).single()
            val timelinePatchedResults = timelineHandler.method.forceSubscriptionFeatureResults()
            if (timelinePatchedResults != TIMELINE_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT) {
                throw PatchException(
                    "Expected $TIMELINE_DOWNLOAD_HANDLER_SUBSCRIPTION_CHECK_COUNT subscription checks in " +
                        "the X-Lite timeline download handler, found $timelinePatchedResults: " +
                        timelineHandler.originalMethod,
                )
            }

            // ALPHA: e()Z. BETA: Q()Z. Global all-tier premium status used by media saving.
            requireMatches(
                "X-Lite premium subscription checker",
                SubscriptionsFeaturesHasAnyPremiumFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceBooleanResult)
            // ALPHA: g()Z. BETA: M()Z. Offline/media-gallery premium gate.
            requireMatches(
                "X-Lite offline-video premium checker",
                SubscriptionsFeaturesOfflinePremiumFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceBooleanResult)
            // ALPHA: i()Z. BETA: s()Z. Feature flag shared by timeline, gallery, and video-tab paths.
            requireMatches(
                "X-Lite offline-video feature gate",
                SubscriptionsFeaturesOfflineVideoEnabledFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceBooleanResult)

            // ALPHA: q4/u4/x4 h()Z. BETA: MediaContent nested classes isDownloadable()Z.
            requireMatches(
                "X-Lite video media downloadability method",
                MediaContentVideoIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
            requireMatches(
                "X-Lite GIF media downloadability method",
                MediaContentGifIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
            requireMatches(
                "X-Lite image media downloadability method",
                MediaContentImageIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
        }
    }
