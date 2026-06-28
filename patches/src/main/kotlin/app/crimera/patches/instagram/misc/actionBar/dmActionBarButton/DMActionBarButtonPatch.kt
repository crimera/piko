/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.dmActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object DMActionBarBuilderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(resourceLiteral(ResourceType.LAYOUT, "layout_direct_thread_header")),
)

val dmActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on DM action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(resourceMappingPatch)

        execute {

            DMActionBarBuilderFingerprint.let {
                it.method
                    .apply {
                        val viewGroupInstruction = getInstruction(indexOfFirstInstruction(Opcode.CHECK_CAST))
                        val viewGroupRegister = viewGroupInstruction.registersUsed[0]

                        val layoutIndex = it.instructionMatches.first().index

                        val fistMoveResultObjectAfterLayoutIndex = indexOfFirstInstruction(layoutIndex, Opcode.MOVE_RESULT_OBJECT)

                        addInstruction(
                            fistMoveResultObjectAfterLayoutIndex + 1,
                            """
                            invoke-static {v$viewGroupRegister}, $ACTIONBAR_DESCRIPTOR/DMActionBar;->addActionBarButton(Landroid/view/ViewGroup;)V
                            """.trimIndent(),
                        )
                    }
            }
        }
    }
