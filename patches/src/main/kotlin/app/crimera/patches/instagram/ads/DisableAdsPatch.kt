/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.ads

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.Opcode

private const val MEDIA_CLASS = "Lcom/instagram/feed/media/Media;"

internal object DisableAdsFingerprint : Fingerprint(
    strings = listOf("Is ad pod"),
)

internal object ContextualFeedFilterFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        opcode(Opcode.CHECK_CAST, location = MatchFirst()),
        string("feed_contextual_self_profile", location = MatchAfterImmediately()),
        methodCall(parameters = listOf(), returnType = MEDIA_CLASS),
    ),
)

internal object FeedAdInsertionFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("instagram_ad_async_ad_controller_action_success"),
        string("timeline_request"),
        methodCall(definingClass = MEDIA_CLASS, parameters = listOf(), returnType = "Z"),
    ),
)

@Suppress("unused")
val disableAdsPatch =
    bytecodePatch(
        name = "Disable ads",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            hookFlagsPatch,
        )
        execute {

            DisableAdsFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->disableAds()Z
                    move-result v0
                    return v0
                    """.trimIndent(),
                )
            }

            val isAd = FeedAdInsertionFingerprint.matchSingle().instructionMatches.last().methodReference

            // Following can receive ads in the response itself, bypassing the injection hook above.
            ContextualFeedFilterFingerprint.matchSingle().let { match ->
                val getMedia = match.instructionMatches.last().methodReference

                match.method.addInstructionsWithLabels(
                    1,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->disableAds()Z
                    move-result v0
                    if-eqz v0, :piko_original
                    if-eqz p1, :piko_original
                    invoke-virtual/range {p1 .. p1}, $getMedia
                    move-result-object v0
                    if-eqz v0, :piko_original
                    invoke-virtual {v0}, $isAd
                    move-result v0
                    if-eqz v0, :piko_original
                    const/4 v0, 0x0
                    return v0
                    """.trimIndent(),
                    ExternalLabel("piko_original", match.method.getInstruction(1)),
                )
            }

            enableSettings("disableAds")
            addFlags("adsFlags")
        }
    }
