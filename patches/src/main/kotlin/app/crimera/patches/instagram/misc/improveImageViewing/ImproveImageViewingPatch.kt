/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.improveImageViewing

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

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
            val PREF_CALL =
                """
                invoke-static/range {v#reg}, $PREF_DESCRIPTOR->improveImageViewing(I)I
                    move-result v#reg
                """.trimIndent()

            SetDPIMetricsFingerprint.method.apply {
                // First one is DPI which is not needed for us.
                val iGetInstructions = instructions.filter { it.opcode == Opcode.IGET }.drop(1)

                iGetInstructions.forEach { instruction ->
                    val register = instruction.registersUsed[0]

                    addInstructions(
                        instruction.location.index + 1,
                        """
                        ${PREF_CALL.replace("#reg",register.toString())}
                        """.trimIndent(),
                    )
                }
            }

            ReturnExtendedImageUrlFingerprint.method.apply {

                val firstIfNe = indexOfFirstInstruction(Opcode.IF_NE)
                val ifNeRegisters = getInstruction(firstIfNe).registersUsed

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

            enableSettings("improveImageViewing")
        }
    }
