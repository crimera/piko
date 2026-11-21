/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm

import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.typeReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private object CallingButtonsReferenceFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(
        opcode(Opcode.CONST_CLASS),
        string(
            "createCallingButtons(Lcom/instagram/direct/fragment/thread/actionbar/viewmodel/DirectThreadActionBarViewModel;Landroid/content/Context;)Ljava/util/List;",
            location = MatchAfterImmediately(),
        ),
        string("createCallingButtons"),
    ),
)

val hideChatCallButtonsPatch = patch {
    execute {
        val builderType = CallingButtonsReferenceFingerprint.instructionMatches.first().typeReference
        val ongoing = Fingerprint(
            definingClass = builderType,
            isStatic = true,
            returnType = "Ljava/util/ArrayList;",
            parameters = listOf("Landroid/content/Context;", "L", builderType),
            filters = listOf(
                opcode(Opcode.RETURN_OBJECT),
                opcode(Opcode.IF_NEZ),
                opcode(Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
                opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
                opcode(Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
                opcode(Opcode.SGET_OBJECT),
                opcode(Opcode.GOTO, location = MatchAfterImmediately()),
            ),
        ).match()

        val regular = Fingerprint(
            filters = listOf(
                opcode(Opcode.IF_EQZ),
                opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
                opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
                opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
                opcode(Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
                methodCall(smali = "Ljava/util/Collections;->singletonList(Ljava/lang/Object;)Ljava/util/List;", location = MatchAfterImmediately()),
            ),
        ).match(ongoing.originalMethod)

        // The combined button tests both capabilities. Its fallback reuses them for the individual buttons.
        val (combined, audio, video) = regular.instructionMatches
        regular.method.addInstructions(
            combined.index,
            visibilityCheck(audio.instruction.registersUsed.single(), "showChatAudioCallButton") + "\n" +
                visibilityCheck(video.instruction.registersUsed.single(), "showChatVideoCallButton"),
        )

        // At either ongoing action, the condition register is true and can be reused for the visibility check.
        val matches = ongoing.instructionMatches
        val empty = ExternalLabel("empty", ongoing.method.getInstruction(matches[0].index))
        val ongoingActions = listOf(
            Triple(matches[5], matches[1], "showChatAudioCallButton"),
            Triple(matches[4], matches[3], "showChatVideoCallButton"),
        )
        for ((action, condition, hook) in ongoingActions) {
            val register = condition.instruction.registersUsed.single()
            ongoing.method.addInstructionsWithLabels(
                action.index + 1,
                visibilityCheck(register, hook) + "\nif-eqz v$register, :empty",
                empty,
            )
        }
    }
}

private fun visibilityCheck(register: Int, hook: String) = """
    invoke-static/range {v$register .. v$register}, $PREF_DESCRIPTOR->$hook(Z)Z
    move-result v$register
""".trimIndent()
