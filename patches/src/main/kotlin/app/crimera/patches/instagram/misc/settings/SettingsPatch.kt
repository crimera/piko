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
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.UI_CONSTANTS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
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
        dependsOn(sharedExtensionPatch, addSettingsActivityPatch)
        execute {

            // Add button to the existing image views.
            AddButtonOnProfileBarFingerprint.method.apply {
                val firstGoto = instructions.indexOfFirst { it.opcode == Opcode.GOTO }
                val viewGroupRegistry = instructions[firstGoto - 1].registersUsed[0]

                addInstruction(
                    firstGoto,
                    """
                    invoke-static {v$viewGroupRegistry}, ${UI_CONSTANTS_DESCRIPTOR}->addPikoSettingsImageView(Landroid/view/ViewGroup;)V
                    """.trimIndent(),
                )
            }

            ExtensionsUtilsFingerprint.method.addInstruction(
                0,
                SSTS_DESCRIPTOR.format("load"),
            )
        }
    }
