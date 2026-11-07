/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.chatActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

object ChatActionBarBuilderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            resourceLiteral(ResourceType.ID, "direct_thread_header"),
            methodCall(smali = "Landroid/view/View;->findViewById(I)Landroid/view/View;", location = MatchAfterImmediately()),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            // Chat header.
            checkCast("Landroid/view/ViewGroup;", location = MatchAfterImmediately()),
            resourceLiteral(ResourceType.LAYOUT, "layout_direct_thread_header"),
            // Inflated chat action bar.
            opcode(Opcode.MOVE_RESULT_OBJECT),
        ),
)

val chatActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons inside chat action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            ChatActionBarBuilderFingerprint.let {
                val viewGroupRegister = it.instructionMatches[3].getInstruction<OneRegisterInstruction>().registerA

                it.method.addInstruction(
                    it.instructionMatches.last().index + 1,
                    """
                    invoke-static {v$viewGroupRegister}, $ACTIONBAR_DESCRIPTOR->chatActionBarButton(Landroid/view/ViewGroup;)V
                    """.trimIndent(),
                )
            }
        }
    }
