package app.crimera.patches.twitter.timeline.hideHiddenReplies

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private val hideHiddenRepliesFingerprint =
    fingerprint {
        returns("Ljava/lang/Object;")

        custom { it, _ ->
            it.definingClass == "Lcom/twitter/model/json/timeline/urt/JsonTimelineTweet;"
        }
    }

@Suppress("unused")
val hideHiddenRepliesPatch =
    bytecodePatch(
        name = "Hide hidden replies",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = hideHiddenRepliesFingerprint.method
            val instructions = method.instructions

            val get_bool = instructions.last { it.opcode == Opcode.IGET_BOOLEAN }.location.index
            val reg = method.getInstruction<TwoRegisterInstruction>(get_bool).registerA

            val M = "invoke-static {v$reg}, $PREF_DESCRIPTOR;->hideHiddenReplies(Z)Z"

            method.addInstructions(
                get_bool + 1,
                """
                $M
                move-result v$reg
                """.trimIndent(),
            )

            settingsStatusLoadFingerprint.enableSettings("hideHiddenReplies")
        }
    }
