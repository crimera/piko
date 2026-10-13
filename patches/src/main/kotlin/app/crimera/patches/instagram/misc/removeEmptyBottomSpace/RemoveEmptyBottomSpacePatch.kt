/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.removeEmptyBottomSpace

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal object NavigationBarAdjusterFingerprint : Fingerprint(
    strings = listOf("config_showNavigationBar", "_hasNavigationBar_notFound"),
    filters = listOf(
        opcode(Opcode.IF_GTZ),
        fieldAccess(opcode = Opcode.SGET_BOOLEAN, location = MatchAfterImmediately()),
        string("android"),
        // Navigation bar size.
        fieldAccess(opcode = Opcode.SPUT),
    ),
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
                method.apply {
                    val index = instructionMatches.first().index
                    val freeRegister = getInstruction<OneRegisterInstruction>(index + 1).registerA
                    val firstSPutIndexAfterStr = instructionMatches.last().index

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
