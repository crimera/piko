/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

object MainFeedStoryTrayBinderGroupViewBinderFingerprint : Fingerprint(
    strings = listOf("MainFeedStoryTrayBinderGroup", "litho_main_feed_stories_tray", "floating_tray_spacer"),
)

@Suppress("unused")
val hideStoriesTrayPatch =
    bytecodePatch(
        name = "Hide stories tray",
        description = "Hides stories tray from main feed.",
    ) {
        dependsOn(settingsPatch, resourceMappingPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            MainFeedStoryTrayBinderGroupViewBinderFingerprint.apply {
                val strIndex = stringMatches.last().index
                method.apply {
                    // Guard against double execution: this patch's execute block has been observed
                    // running twice in a single patching run (same framework issue worked around in
                    // instagramButtonEntity). A second injection interleaves with the first and lands
                    // a branch on a move-result, producing "invalid use of move-result as branch
                    // target" — a VerifyError in this ViewBinder's bindView that crashes the main
                    // feed on load. Skip re-injecting if our call is already present.
                    val alreadyInjected = instructions.any {
                        it.opcode == Opcode.INVOKE_STATIC &&
                            (it as? ReferenceInstruction)?.reference?.toString()?.contains("hideStoriesTray") == true
                    }
                    if (!alreadyInjected) {
                        val iGetObjectInstruction = instructions.last { it.opcode == Opcode.IGET_OBJECT && it.location.index < strIndex }
                        val iGetObjectIndex = iGetObjectInstruction.location.index
                        val targetIndex = iGetObjectIndex + 1

                        val storiesTrayViewGroupRegistry = iGetObjectInstruction.registersUsed[0]
                        val freeRegister = getInstruction(strIndex).registersUsed[0]

                        addInstructionsWithLabels(
                            targetIndex,
                            """
                            $PREF_CALL_DESCRIPTOR->hideStoriesTray()Z
                            move-result v$freeRegister
                            if-eqz v$freeRegister, :piko
                            const v$storiesTrayViewGroupRegistry, 0x0
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(targetIndex + 1)),
                        )
                    }

                    enableSettings("hideStoriesTray")
                }
            }
        }
    }
