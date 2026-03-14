/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.roundOffNumbers

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.shared.misc.mapping.ResourceType
import app.morphe.shared.misc.mapping.resourceLiteral
import com.android.tools.smali.dexlib2.Opcode

private object RoundOffNumbersFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    filters = listOf(
        resourceLiteral(ResourceType.INTEGER,"abbr_number_divider_billions"),
        resourceLiteral(ResourceType.INTEGER,"abbr_number_divider_millions"),
        resourceLiteral(ResourceType.INTEGER,"abbr_number_divider_thousands"),
        resourceLiteral(ResourceType.STRING, "abbr_number_unit_billions"),
        resourceLiteral(ResourceType.STRING, "abbr_number_unit_millions"),
        resourceLiteral(ResourceType.STRING, "abbr_number_unit_thousands"),
        )
)

@Suppress("unused")
val roundOffNumbersPatch =
    bytecodePatch(
        name = "Round off numbers",
        description = "Enable or disable rounding off numbers",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            RoundOffNumbersFingerprint.method.apply {
                val move_res_obj = instructions.first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index
                val inv_vir = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }
                val sget_obj = instructions.first { it.opcode == Opcode.SGET_OBJECT }

                addInstructionsWithLabels(
                    move_res_obj + 1,
                    """
                        sget-boolean v1, $PREF_DESCRIPTOR;->ROUND_OFF_NUMBERS:Z
                        if-nez v1, :cond
                        goto :here
                        """.trimIndent(),
                    ExternalLabel("here", sget_obj),
                    ExternalLabel("cond", inv_vir),
                )

                SettingsStatusLoadFingerprint.enableSettings("roundOffNumbers")
            }
        }
    }
