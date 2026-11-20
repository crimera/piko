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
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resource.resourceId
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

private const val FIND_VIEW = "Landroid/view/View;->findViewById(I)Landroid/view/View;"

object ChatActionBarBuilderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            resourceLiteral(ResourceType.ID, "direct_thread_header"),
            methodCall(smali = FIND_VIEW, location = MatchAfterImmediately()),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            checkCast("Landroid/widget/LinearLayout;", location = MatchAfterImmediately()),
            resourceLiteral(ResourceType.LAYOUT, "layout_direct_thread_header_preinflated"),
            methodCall(smali = "Landroid/view/ViewGroup;->addView(Landroid/view/View;)V"),
            resourceLiteral(ResourceType.LAYOUT, "layout_direct_thread_header"),
            methodCall(opcode = Opcode.INVOKE_VIRTUAL_RANGE, returnType = "Landroid/view/View;"),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        ),
)

val chatActionBarButtonPatch =
    patch(
        description = "This patch is adds support for adding buttons inside chat action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            ChatActionBarBuilderFingerprint.match().let { match ->
                val root = match.instructionMatches[1].instruction.registersUsed.first()
                val join = match.instructionMatches.last()
                val (scratch, receiver) = join.instruction.registersUsed
                if (root > 15 || scratch == root || scratch == receiver) {
                    throw PatchException("Expected a free destination at the chat header layout join")
                }
                val headerId = resourceId(ResourceType.ID, "direct_thread_header")

                // Both layouts now have their native children. Inflation can overwrite the original header register.
                match.method.addInstructionsAtControlFlowLabel(
                    join.index,
                    """
                    const v$scratch, $headerId
                    invoke-virtual {v$root, v$scratch}, $FIND_VIEW
                    move-result-object v$scratch
                    check-cast v$scratch, Landroid/widget/LinearLayout;
                    invoke-static {v$scratch}, $ACTIONBAR_DESCRIPTOR->chatActionBarButton(Landroid/view/ViewGroup;)V
                    """.trimIndent(),
                )
            }
        }
    }
