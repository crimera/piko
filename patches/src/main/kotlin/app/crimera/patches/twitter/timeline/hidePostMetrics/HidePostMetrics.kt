package app.crimera.patches.twitter.timeline.hidePostMetrics

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private object InlineActionViewTextFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/ui/tweet/inlineactions/InlineActionView;",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Z"),
)

private object tweetTweetStatViewTextFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/ui/tweet/",
    returnType = "V",
    parameters = listOf(
        "Lcom/twitter/ui/tweet/TweetStatView;",
        "Ljava/lang/String;",
        "Ljava/lang/String;"
    )
)

@Suppress("unused")
val hidePostMetrics =
    bytecodePatch(
        name = "Hide post metrics",
        description = "Hides like, reposts etc counts.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val DESCRIPTOR = "sget-boolean v0, $PREF_DESCRIPTOR;->"

            var patch1 = false
            var patch2 = false
            InlineActionViewTextFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    $DESCRIPTOR HIDE_INLINE_METRICS:Z
                    if-eqz v0, :cond
                    return-void
                    """.trimIndent(),
                    ExternalLabel("cond", instructions.first { it.opcode == Opcode.CONST_16 }),
                )

                patch1 = true
            }

            tweetTweetStatViewTextFingerprint.method.apply {

                val ifNezLoc = instructions.first { it.opcode == Opcode.IF_NEZ }.location.index

                addInstructionsWithLabels(
                    ifNezLoc + 1,
                    """
                    $DESCRIPTOR HIDE_DETAILED_METRICS:Z
                    if-eqz v0, :cond
                    const-string p1, ""
                    """.trimIndent(),
                    ExternalLabel("cond", instructions.first { it.opcode == Opcode.CONST_4 }),
                )
                patch2 = true
            }

            if (patch1 && patch2) {
                SettingsStatusLoadFingerprint.enableSettings("hidePostMetrics")
            }
        }
    }
