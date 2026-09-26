/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

object BindMainFeedActionBarFingerprint : Fingerprint(
    strings = listOf("BindMainFeedActionBar"),
    returnType = "Ljava/lang/Object;",
)

private object HomeActionModelFingerprint : Fingerprint(
    name = "<init>",
    strings = listOf("share", "news", "quick_snap", "manage_feeds"),
)

val hideHomeActionButtonsPatch = bytecodePatch {
    execute {
        val method = HomeActionModelFingerprint.matchAll(0..Int.MAX_VALUE)
            .singleOrNull()?.method
            ?: throw PatchException("Expected one home action model builder")
        val callIndex = method.instructions.withIndex().filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.parameterTypes?.map(CharSequence::toString) ==
                listOf("Lcom/instagram/common/session/UserSession;", "I") &&
                reference.returnType == "Ljava/lang/String;"
        }.singleOrNull()?.index
            ?: throw PatchException("Expected one home action name lookup")
        val result = method.getInstruction(callIndex + 1)
        val nullCheck = method.getInstruction(callIndex + 2)
        if (result.opcode != Opcode.MOVE_RESULT_OBJECT ||
            result.registersUsed.size != 1 || nullCheck.opcode != Opcode.IF_EQZ ||
            nullCheck.registersUsed.singleOrNull() != result.registersUsed.single()
        ) throw PatchException("Expected home action name null guard")
        val register = result.registersUsed.single()
        method.addInstructions(
            callIndex + 2,
            """
            invoke-static/range {v$register .. v$register}, $ACTIONBAR_DESCRIPTOR->filterHomeAction(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v$register
            """.trimIndent(),
        )
    }
}

val mainFeedActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on main feed action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            BindMainFeedActionBarFingerprint.apply {
                val strIndex = stringMatches.first().index
                method.apply {
                    val allIfEqz = instructions.filter { it.location.index > strIndex && it.opcode == Opcode.IF_EQZ }
                    allIfEqz.firstOrNull {
                        val index = it.location.index
                        val prevInstruction = getInstruction(index - 1)
                        val prevInstructionOpcode = prevInstruction.opcode
                        if (prevInstructionOpcode == Opcode.IGET_OBJECT) {
                            val layoutRegister = prevInstruction.registersUsed[0]
                            addInstruction(
                                index,
                                """
                                invoke-static {v$layoutRegister}, $ACTIONBAR_DESCRIPTOR->mainFeedActionBarButton(Landroid/view/ViewGroup;)V
                                """.trimIndent(),
                            )
                            addFlags("mainFeedActionBarFlags")
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }
    }
