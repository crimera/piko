/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

object BindMainFeedActionBarFingerprint : Fingerprint(
    filters = listOf(string("BindMainFeedActionBar")),
    returnType = "Ljava/lang/Object;",
)

private object HomeActionModelFingerprint : Fingerprint(
    name = "<init>",
    strings = listOf("share", "news", "quick_snap", "manage_feeds"),
)

val hideHomeActionButtonsPatch = bytecodePatch {
    execute {
        val method = HomeActionModelFingerprint.matchSingle().method
        val callIndex = method.instructions.withIndex().filter { (_, instruction) ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference?.parameterTypes?.map(CharSequence::toString) ==
                listOf(USER_SESSION_CLASS, "I") &&
                reference.returnType == "Ljava/lang/String;"
        }.singleOrNull()?.index
            ?: throw PatchException("Expected one home action name lookup")
        val result = method.getInstruction(callIndex + 1)
        if (result.opcode != Opcode.MOVE_RESULT_OBJECT || result.registersUsed.size != 1) {
            throw PatchException("Expected home action name result")
        }
        val register = result.registersUsed.single()
        val next = method.getInstruction(callIndex + 2)
        val filter = """
            invoke-static/range {v$register .. v$register}, $ACTIONBAR_DESCRIPTOR->filterHomeAction(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v$register
            """.trimIndent()
        if (next.opcode == Opcode.IF_EQZ && next.registersUsed == listOf(register)) {
            method.addInstructions(callIndex + 2, filter)
        } else {
            // Newer versions collect names before building the action buttons.
            val append = next.getReference<MethodReference>()
            val loop = method.getInstruction(callIndex + 3) as? BuilderOffsetInstruction
            val loopTarget = loop?.target?.location?.index
            val loopHead = loopTarget?.let { method.getInstruction(it) }
            val hasNext = loopHead?.getReference<MethodReference>()
            if (next.opcode != Opcode.INVOKE_VIRTUAL ||
                append?.definingClass != "Ljava/util/AbstractCollection;" || append.name != "add" ||
                append.parameterTypes.map(CharSequence::toString) != listOf("Ljava/lang/Object;") ||
                append.returnType != "Z" || next.registersUsed.size != 2 ||
                next.registersUsed[1] != register || next.registersUsed[0] == register ||
                loop?.opcode != Opcode.GOTO || loopTarget == null || loopTarget >= callIndex ||
                loopHead?.opcode != Opcode.INVOKE_INTERFACE || loopHead.registersUsed.size != 1 ||
                hasNext?.definingClass != "Ljava/util/Iterator;" || hasNext.name != "hasNext" ||
                hasNext.parameterTypes.isNotEmpty() || hasNext.returnType != "Z"
            ) throw PatchException("Expected home action name null guard or collection loop")
            method.addInstructionsWithLabels(
                callIndex + 2,
                "$filter\nif-eqz v$register, :next_action",
                ExternalLabel("next_action", loop),
            )
        }
    }
}

val mainFeedActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on main feed action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val binding = BindMainFeedActionBarFingerprint.matchSingle().method
            val actionBar = "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;"
            val candidates = mutableListOf(binding)
            // Newer versions move the layout update into a method on the action bar itself.
            val calls = binding.instructions.mapNotNull {
                it.getReference<MethodReference>()?.takeIf { reference ->
                    reference.definingClass == actionBar && reference.returnType == "V" &&
                        USER_SESSION_CLASS in reference.parameterTypes
                }
            }.distinctBy { it.toString() }
            for (call in calls) {
                candidates += mutableClassDefBy(actionBar).methods.single { method ->
                    method.name == call.name && method.parameterTypes == call.parameterTypes &&
                        method.returnType == call.returnType
                }
            }
            val containers = candidates.flatMap { method ->
                val code = method.instructions.toList()
                (0 until code.size - 2).mapNotNull { index ->
                    val read = code[index]
                    val field = read.getReference<FieldReference>()
                    val visibility = code[index + 2].getReference<MethodReference>()
                    if (read.opcode == Opcode.IGET_OBJECT &&
                        field?.definingClass == actionBar && field.type == "Landroid/widget/LinearLayout;" &&
                        code[index + 1].opcode == Opcode.IF_EQZ &&
                        visibility?.name == "setVisibility" && visibility.parameterTypes == listOf("I") &&
                        visibility.returnType == "V" &&
                        code[index + 2].registersUsed.firstOrNull() == read.registersUsed.firstOrNull()
                    ) method to index else null
                }
            }
            val (method, index) = containers.singleOrNull()
                ?: throw PatchException("Expected one main feed action button container")
            val layoutRegister = method.getInstruction(index).registersUsed.first()
            method.addInstruction(
                index + 1,
                "invoke-static/range {v$layoutRegister .. v$layoutRegister}, " +
                    "$ACTIONBAR_DESCRIPTOR->mainFeedActionBarButton(Landroid/view/ViewGroup;)V",
            )
            addFlags("mainFeedActionBarFlags")
        }
    }
