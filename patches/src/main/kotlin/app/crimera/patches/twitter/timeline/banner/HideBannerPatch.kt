/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.banner

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private object HideBannerFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/timeline/newtweetsbanner/BaseNewTweetsBannerPresenter;",
    returnType = "Z",
    filters =
        listOf(
            opcode(Opcode.RETURN),
        ),
)

private object XLiteNewPostsPillFingerprint : Fingerprint(
    parameters =
        listOf(
            "Lcom/x/models/timelines/URTTimelineInstruction\$ShowInstructions\$TimelineShowAlert;",
            "L",
            "Landroidx/compose/ui/Modifier;",
            "Lkotlin/jvm/functions/Function0;",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
    returnType = "V",
)

@Suppress("unused")
val hideBannerPatch =
    bytecodePatch(
        name = "Hide Banner",
        description = "Hide new post banner and X-Lite new posts pill",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = HideBannerFingerprint.method
            val instuctions = method.instructions

            val loc = instuctions.first { it.opcode == Opcode.IF_NEZ }.location.index

            val HIDE_BANNER_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->hideBanner()Z"

            method.addInstructions(
                loc,
                """
                $HIDE_BANNER_DESCRIPTOR
                move-result v0
                """.trimIndent(),
            )

            XLiteNewPostsPillFingerprint.matchOrNull()?.let { match ->
                val firstInstruction = match.method.instructions.first()
                match.method.addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->hideBanner()Z
                    move-result v0
                    if-nez v0, :piko_hide_banner_continue
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_hide_banner_continue", firstInstruction),
                )
            }

            enableSettings("hideBanner")
        }
    }
