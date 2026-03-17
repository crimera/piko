/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.shared.misc.mapping.ResourceType
import app.morphe.shared.misc.mapping.resourceLiteral
import app.morphe.shared.misc.mapping.resourceMappingPatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object ReelsTrayContainerViewGroupFingerprint : Fingerprint(
    filters =
        listOf(
            resourceLiteral(ResourceType.ID, "reels_tray_container"),
        ),
)

@Suppress("unused")
val hideStoriesTrayPatch =
    bytecodePatch(
        name = "Hide stories tray",
        description = "Hides stories tray from main feed.",
    ) {
        dependsOn(settingsPatch, resourceMappingPatch)
        compatibleWith("com.instagram.android")

        execute {

            ReelsTrayContainerViewGroupFingerprint.method.apply {
                val iPutObjectIndex = indexOfFirstInstruction(Opcode.IPUT_OBJECT)
                val iPutObjectInstruction = getInstruction(iPutObjectIndex)
                val storiesTrayViewGroupRegistry = iPutObjectInstruction.registersUsed[0]

                addInstructionsWithLabels(
                    iPutObjectIndex,
                    """
                    ${PREF_CALL_DESCRIPTOR}->hideStoriesTray()Z
                    move-result v2
                    if-eqz v2, :piko
                    const v$storiesTrayViewGroupRegistry, 0x0
                    """.trimIndent(),
                    ExternalLabel("piko", iPutObjectInstruction),
                )

                enableSettings("hideStoriesTray")
            }
        }
    }
