/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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

            BindMainFeedActionBarFingerprint.matchAll(1..1).single().method.apply {
                val code = instructions.toList()
                val markerIndex = code.withIndex().singleOrNull { (_, instruction) ->
                    instruction.getReference<StringReference>()?.string == "BindMainFeedActionBar"
                }?.index ?: throw PatchException("Expected one main feed action bar binding marker")
                val containerIndex = (markerIndex + 1 until code.size - 2).singleOrNull { index ->
                    val read = code[index]
                    val field = read.getReference<FieldReference>()
                    val visibility = code[index + 2].getReference<MethodReference>()
                    read.opcode == Opcode.IGET_OBJECT &&
                        field?.definingClass == "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;" &&
                        field.type == "Landroid/widget/LinearLayout;" &&
                        code[index + 1].opcode == Opcode.IF_EQZ &&
                        visibility?.name == "setVisibility" && visibility.parameterTypes == listOf("I") &&
                        visibility.returnType == "V" &&
                        code[index + 2].registersUsed.firstOrNull() == read.registersUsed.firstOrNull()
                } ?: throw PatchException("Expected one main feed action button container")
                val layoutRegister = code[containerIndex].registersUsed.first()
                addInstruction(
                    containerIndex + 1,
                    "invoke-static/range {v$layoutRegister .. v$layoutRegister}, $ACTIONBAR_DESCRIPTOR->mainFeedActionBarButton(Landroid/view/ViewGroup;)V",
                )
                addFlags("mainFeedActionBarFlags")
            }
        }
    }
