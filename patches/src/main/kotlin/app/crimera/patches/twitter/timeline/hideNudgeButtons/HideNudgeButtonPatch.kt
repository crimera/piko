package app.crimera.patches.twitter.timeline.hideNudgeButtons

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c

internal val hideNudgeButtonPatchFingerprint =
    fingerprint {
        strings("viewDelegate", "viewModel")

        custom { _, classDef ->
            classDef.type.endsWith("FollowNudgeButtonViewDelegateBinder;")
        }
    }

@Suppress("unused")
val hideNudgeButtonPatch =
    bytecodePatch(
        name = "Hide nudge button",
        description = "Hides follow/subscribe/follow back buttons on posts",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val HOOK_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->hideNudgeButton()Z"

            val method = hideNudgeButtonPatchFingerprint.method
            val instructions = method.instructions

            val newInst4 = instructions.filter { it.opcode == Opcode.NEW_INSTANCE }[3]
            val newInst4Index = newInst4.location.index
            val dummyReg = method.getInstruction<BuilderInstruction21c>(newInst4Index).registerA

            method.addInstructionsWithLabels(
                newInst4Index - 2,
                """
                $HOOK_DESCRIPTOR
                move-result v$dummyReg
                if-eqz v$dummyReg, :piko
                const/16 v$dummyReg, 0x8
                invoke-virtual {p1, v$dummyReg}, Landroidx/appcompat/widget/AppCompatButton;->setVisibility(I)V
                """.trimIndent(),
                ExternalLabel(
                    "piko",
                    instructions.last {
                        it.opcode == Opcode.INVOKE_STATIC &&
                            it.location.index < newInst4Index
                    },
                ),
            )

            settingsStatusLoadFingerprint.method.enableSettings("hideNudgeButton")
        }
    }
