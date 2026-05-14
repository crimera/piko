/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.removeEmptyBottomSpace

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object NavigationBarAdjusterFingerprint : Fingerprint(
    strings = listOf("android", "config_showNavigationBar", "_hasNavigationBar_notFound"),
)

// Thanks to MyInsta
@Suppress("unused")
val removeEmptyBottomSpacePatch =
    bytecodePatch(
        name = "Remove empty bottom space",
        description = "Removes empty space below bottom navigation bar",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // Thanks to MyInsta.
            NavigationBarAdjusterFingerprint.apply {
                val strIndex = stringMatches[0].index

                method.apply {
                    val lastIfGtzInstructionBeforeStr =
                        instructions.last {
                            it.location.index < strIndex &&
                                it.opcode == Opcode.IF_GTZ
                        }
                    val index = lastIfGtzInstructionBeforeStr.location.index

                    val firstSPutIndexAfterStr = indexOfFirstInstruction(strIndex, Opcode.SPUT)

                    val freeRegister = getInstruction(index + 1).registersUsed[0]

                    addInstructionsWithLabels(
                        index + 1,
                        """
                        invoke-static {}, $PREF_DESCRIPTOR->removeEmptyBottomSpace()Z
                        move-result v$freeRegister
                        if-nez v$freeRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(firstSPutIndexAfterStr)),
                    )
                    enableSettings("removeEmptyBottomSpace")
                }
            }
        }
    }
