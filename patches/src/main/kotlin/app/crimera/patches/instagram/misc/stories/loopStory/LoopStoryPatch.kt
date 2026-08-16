/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.loopStory

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.util.indexOfFirstStringInstructionOrThrow
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal object StoryProgressCompletedFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Linstagram/features/stories/fragment/ReelViewerFragment;",
    strings = listOf("userSession"),
    parameters = listOf("Ljava/lang/Object;"),
)

@Suppress("unused")
val loopStoryPatch =
    bytecodePatch(
        name = "Loop story",
        description = "Replay the current story when it ends",
    ) {
        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            StoryProgressCompletedFingerprint.method.apply {
                val entryCast = getInstruction(indexOfFirstInstructionOrThrow(Opcode.CHECK_CAST))
                val reelItemRegister = entryCast.registersUsed[0]
                val reelItemType = entryCast.getReference<TypeReference>()!!.type

                val restartIndex =
                    indexOfFirstInstructionReversedOrThrow(
                        indexOfFirstStringInstructionOrThrow("resume"),
                        Opcode.INVOKE_STATIC,
                    )

                val seekInstruction =
                    getInstruction(
                        indexOfFirstInstructionOrThrow(restartIndex) {
                            opcode == Opcode.INVOKE_INTERFACE &&
                                getReference<MethodReference>()?.let { reference ->
                                    reference.returnType == "V" &&
                                        reference.parameterTypes.map(CharSequence::toString) ==
                                        listOf("I", "Z")
                                } == true
                        },
                    )
                val positionRegister = seekInstruction.registersUsed[1]
                val playingRegister = seekInstruction.registersUsed[2]

                check(
                    p0Register > 0 &&
                        reelItemRegister > 0 &&
                        positionRegister < p0Register &&
                        playingRegister < p0Register,
                ) {
                    "Story restart block does not keep its state in local registers"
                }

                addInstructionsWithLabels(
                    0,
                    """
                    ${PREF_CALL_DESCRIPTOR}->loopStory()Z
                    move-result v0
                    if-eqz v0, :piko
                    check-cast v$reelItemRegister, $reelItemType
                    const/4 v$playingRegister, 0x1
                    const/4 v$positionRegister, 0x0
                    goto :piko_loop
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                    ExternalLabel("piko_loop", getInstruction(restartIndex)),
                )
            }
            enableSettings("loopStory")
        }
    }
