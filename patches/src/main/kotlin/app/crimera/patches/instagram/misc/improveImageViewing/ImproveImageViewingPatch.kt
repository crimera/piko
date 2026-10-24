/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.improveImageViewing

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.registersUsed

@Suppress("unused")
val improveImageViewingPatch =
    bytecodePatch(
        name = "Improve image viewing",
        description = "Fetches max resolution images from server.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {
            var PREF_CALL =
                """
                invoke-static {v#reg}, $PREF_DESCRIPTOR->improveImageViewing(I)I
                    move-result v#reg
                """.trimIndent()

            ReturnExtendedImageUrlFingerprint.method.apply {

                val firstIfNe = ReturnExtendedImageUrlFingerprint.instructionMatches.last().index
                val ifNeRegisters = ReturnExtendedImageUrlFingerprint.instructionMatches.last().instruction.registersUsed

                val heightRegister = ifNeRegisters[0]
                val widthRegister = ifNeRegisters[1]

                addInstructions(
                    firstIfNe,
                    """
                    ${PREF_CALL.replace("#reg",heightRegister.toString())}
                    ${PREF_CALL.replace("#reg", widthRegister.toString())}
                    """.trimIndent(),
                )
            }

            PREF_CALL =
                """
                invoke-static {v#reg}, $PREF_DESCRIPTOR->improveImageViewing(Ljava/lang/Integer;)Ljava/lang/Integer;
                    move-result-object v#reg
                """.trimIndent()

            SetDPIMetricsFingerprint.method.apply {
                val filledNewArrayIndex = SetDPIMetricsFingerprint.instructionMatches.last().index

                val registers = SetDPIMetricsFingerprint.instructionMatches.last().instruction.registersUsed

                val heightRegister = registers[1].toString()
                val widthRegister = registers[2].toString()

                addInstructions(
                    filledNewArrayIndex,
                    """
                    ${PREF_CALL.replace("#reg",heightRegister)}
                    ${PREF_CALL.replace("#reg", widthRegister)}
                    """.trimIndent(),
                )
            }

            enableSettings("improveImageViewing")
        }
    }
