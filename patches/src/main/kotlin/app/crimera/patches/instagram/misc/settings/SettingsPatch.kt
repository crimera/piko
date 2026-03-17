/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.utils.Constants.ACTIVITY_HOOK_CLASS
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.shared.misc.extension.ExtensionsUtilsFingerprint
import app.morphe.util.findFreeRegister
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val settingsPatch =
    bytecodePatch(
        name = "Add settings",
        description = "Adds settings to control preferences are patching",
        use = true,
    ) {
        compatibleWith("com.instagram.android")
        dependsOn(sharedExtensionPatch)
        execute {

            // Add button to the existing image views.
            AddButtonOnProfileBarFingerprint.method.apply {
                val firstGoto = instructions.indexOfFirst { it.opcode == Opcode.GOTO }
                val viewGroupRegistry = instructions[firstGoto - 1].registersUsed[0]

                addInstruction(
                    firstGoto,
                    """
                    invoke-static {v$viewGroupRegistry}, $ACTIVITY_HOOK_CLASS->addPikoSettingsImageView(Landroid/view/ViewGroup;)V
                    """.trimIndent(),
                )
            }

            // Hook onCreate method to launch settings.
            OnCreateFingerprint.method.apply {
                val invokeSuperInstruction =
                    instructions.first {
                        it.opcode ==
                            Opcode
                                .INVOKE_SUPER
                    }
                val invokeSupeIndex = invokeSuperInstruction.location.index

                val activityRegistry = invokeSuperInstruction.registersUsed[0]
                val freeRegistry = findFreeRegister(invokeSupeIndex + 1)

                addInstructionsWithLabels(
                    invokeSupeIndex + 1,
                    """
                    invoke-static {v$activityRegistry}, $ACTIVITY_HOOK_CLASS->hook(Landroid/app/Activity;)Z
                    move-result v$freeRegistry
                    if-nez v9, :piko
                    """.trimIndent(),
                    ExternalLabel("piko", instructions.first { it.opcode == Opcode.RETURN_VOID }),
                )
            }

            ExtensionsUtilsFingerprint.method.addInstruction(
                0,
                SSTS_DESCRIPTOR.format("load"),
            )
        }
    }
