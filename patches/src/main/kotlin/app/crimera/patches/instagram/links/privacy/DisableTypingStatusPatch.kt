/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.misc.actionBar.dmActionBarButton.dmActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object DMAfterTextChangedFingerprint : Fingerprint(
    name = "afterTextChanged",
    filters =
        listOf(
            literal(8388659L), // 0x800032
            literal(8388627L), // 0x800013
        ),
)

internal object DMOnTextChangedFingerprint : Fingerprint(
    name = "onTextChanged",
    classFingerprint = DMAfterTextChangedFingerprint,
)

// Thanks to MyInsta.
@Suppress("unused")
val disableTypingStatusPatch =
    bytecodePatch(
        name = "Disable typing status",
    ) {
        dependsOn(settingsPatch, resourceMappingPatch, dmActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            DMOnTextChangedFingerprint.method.apply {

                instructions.filter { it.opcode == Opcode.IGET_BOOLEAN }.firstOrNull {
                    val index = it.location.index
                    val nextInstruction = getInstruction(index + 1)
                    if (nextInstruction.opcode == Opcode.IF_NEZ) {
                        val register = nextInstruction.registersUsed[0]
                        addInstructionsWithLabels(
                            index,
                            """
                            $PREF_CALL_DESCRIPTOR->disableTypingStatus()Z
                            move-result v$register
                            if-eqz v$register, :piko
                            return-void
                            """.trimIndent(),
                            ExternalLabel("piko", nextInstruction),
                        )
                        true
                    } else {
                        false
                    }
                }
            }

            enableSettings("disableTypingStatus")
        }
    }
