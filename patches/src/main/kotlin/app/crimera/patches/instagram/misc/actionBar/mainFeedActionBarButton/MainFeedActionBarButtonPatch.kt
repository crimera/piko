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
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val MAIN_FEED_ACTION_BAR = "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;"

object BindMainFeedActionBarFingerprint : Fingerprint(
    filters = listOf(string("BindMainFeedActionBar")),
    returnType = "Ljava/lang/Object;",
)

private object ActionButtonsContainerFingerprint : Fingerprint(
    definingClass = MAIN_FEED_ACTION_BAR,
    name = "<init>",
    filters = listOf(
        resourceLiteral(ResourceType.ID, "action_bar_action_buttons"),
        methodCall(smali = "Landroid/view/View;->findViewById(I)Landroid/view/View;", location = MatchAfterImmediately()),
        checkCast("Landroid/widget/LinearLayout;", location = MatchAfterWithin(3)),
        fieldAccess(
            definingClass = MAIN_FEED_ACTION_BAR,
            type = "Landroid/widget/LinearLayout;",
            opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterImmediately(),
        ),
    ),
)

private object HomeActionModelFingerprint : Fingerprint(
    name = "<init>",
    filters = listOf(string("news"), string("share")),
)

val hideHomeActionButtonsPatch = patch {
    execute {
        val method = HomeActionModelFingerprint.method
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
    patch(
        description = "This patch is adds support for adding buttons on main feed action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val binding = BindMainFeedActionBarFingerprint.method
            val container = ActionButtonsContainerFingerprint.instructionMatches.last()
                .instruction.getReference<FieldReference>()!!
            val candidates = mutableListOf(binding)
            // Newer versions move the layout update into a method on the action bar itself.
            val calls = binding.instructions.mapNotNull {
                it.getReference<MethodReference>()?.takeIf { reference ->
                    reference.definingClass == MAIN_FEED_ACTION_BAR && reference.returnType == "V" &&
                        USER_SESSION_CLASS in reference.parameterTypes
                }
            }.distinctBy { it.toString() }
            for (call in calls) {
                candidates += mutableClassDefBy(MAIN_FEED_ACTION_BAR).methods.single { method ->
                    method.name == call.name && method.parameterTypes == call.parameterTypes &&
                        method.returnType == call.returnType
                }
            }
            val containers = candidates.flatMap { method ->
                method.instructions.withIndex()
                    .filter { (_, read) ->
                        read.opcode == Opcode.IGET_OBJECT && read.getReference<FieldReference>() == container
                    }
                    .map { (index, _) -> method to index }
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
