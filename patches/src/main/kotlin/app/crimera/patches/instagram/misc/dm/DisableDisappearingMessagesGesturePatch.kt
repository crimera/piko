/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private object ToggleDisappearingMessagesFingerprint : Fingerprint(
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        string("disappearing_messages"),
        methodCall(parameters = listOf(USER_SESSION_CLASS, "L"), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    ),
)

private object DisappearingMessagesGestureFingerprint : Fingerprint(
    classFingerprint = ToggleDisappearingMessagesFingerprint,
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        methodCall(parameters = listOf(USER_SESSION_CLASS, "L"), returnType = "Z"),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        methodCall(definingClass = "Landroid/view/View;", name = "setOnTouchListener"),
        methodCall(
            definingClass = "Lcom/instagram/ui/widget/refresh/RefreshableNestedScrollingParent;",
            name = "setRefreshingDistance",
        ),
        methodCall(definingClass = "this", parameters = emptyList(), returnType = "V"),
    ),
)

@Suppress("unused")
val disableDisappearingMessagesGesturePatch = bytecodePatch(
    name = "Disable disappearing messages swipe",
    description = "Prevents swiping up in a chat from enabling disappearing messages",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        DisappearingMessagesGestureFingerprint.apply {
            if (instructionMatches[0].methodReference != ToggleDisappearingMessagesFingerprint.instructionMatches[1].methodReference) {
                throw PatchException("Disappearing messages state check does not match the gesture controller")
            }
            val stateMatch = instructionMatches[1]
            val activeRegister = stateMatch.instruction.registersUsed.single()
            val index = stateMatch.index + 1
            val scratchRegister = method.findFreeRegister(index)

            // Keep swipe-to-disable available; use Instagram's own gesture cleanup when inactive.
            method.addInstructionsWithLabels(
                index,
                """
                if-nez v$activeRegister, :piko_continue
                $PREF_CALL_DESCRIPTOR->disableDisappearingMessagesGesture()Z
                move-result v$scratchRegister
                if-nez v$scratchRegister, :piko_disable_gesture
                """.trimIndent(),
                ExternalLabel("piko_continue", method.getInstruction(index)),
                ExternalLabel("piko_disable_gesture", method.getInstruction(instructionMatches.last().index)),
            )
        }
        enableSettings("disableDisappearingMessagesGesture")
    }
}
