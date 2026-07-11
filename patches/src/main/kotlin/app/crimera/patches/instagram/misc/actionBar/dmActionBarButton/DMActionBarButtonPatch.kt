/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.dmActionBarButton

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

// The layout literal alone is ambiguous: newer IG builds (e.g. v438) also emit
// `layout_direct_thread_header` inside an unrelated resource→config Map builder that has no
// ViewGroup cast, and that method sorts first — so a bare resourceLiteral filter matched it and
// the patch crashed on getInstruction(-1). Requiring the ViewGroup check-cast that precedes the
// layout inflate pins the match to the real header builder across versions.
object DMActionBarBuilderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            checkCast("Landroid/view/ViewGroup;"),
            resourceLiteral(ResourceType.LAYOUT, "layout_direct_thread_header"),
        ),
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
                        // instructionMatches[0] = the ViewGroup check-cast, [1] = the layout literal.
                        val viewGroupInstruction = getInstruction(it.instructionMatches[0].index)
                        val viewGroupRegister = viewGroupInstruction.registersUsed[0]

                        val layoutIndex = it.instructionMatches[1].index

                        val fistMoveResultObjectAfterLayoutIndex = indexOfFirstInstruction(layoutIndex, Opcode.MOVE_RESULT_OBJECT)

                        addInstruction(
                            fistMoveResultObjectAfterLayoutIndex + 1,
                            """
                            invoke-static {v$viewGroupRegister}, $PATCHES_DESCRIPTOR/actionbar/DMActionBar;->addActionBarButton(Landroid/view/ViewGroup;)V
                            """.trimIndent(),
                        )
                    }
            }
        }
    }
