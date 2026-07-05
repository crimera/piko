/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.registersUsed

object InboxActionBarBuilderFingerprint : Fingerprint(
    filters =
        listOf(
            literal(147457),
            string("PrebindActionBar"),
        ),
)

val inboxActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on Inbox action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(resourceMappingPatch)

        execute {

            InboxActionBarBuilderFingerprint.apply {
                val literalIndex = instructionMatches.first().index

                method.apply {
                    val viewGroupRegister = getInstruction(literalIndex - 1).registersUsed[0]
                    addInstructions(
                        literalIndex,
                        """
                        invoke-static {v$viewGroupRegister}, $ACTIONBAR_DESCRIPTOR->inboxActionBarButton(Landroid/view/ViewGroup;)V
                        """.trimIndent(),
                    )
                }
            }
        }
    }
