/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object OnTextChangedFingerprint : Fingerprint(
    name = "onTextChanged",
    parameters = listOf("Ljava/lang/CharSequence;", "I", "I", "I"),
    returnType = "V",
    filters =
        OpcodesFilter.opcodesToFilters(
            Opcode.CONST_4,
            Opcode.INVOKE_STATIC,
            Opcode.IGET_OBJECT,
            Opcode.INVOKE_VIRTUAL,
            Opcode.MOVE_RESULT_OBJECT,
        ),
)

// Thanks to MyInsta.
@Suppress("unused")
val disableTypingStatusPatch =
    bytecodePatch(
        name = "Disable typing status",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            OnTextChangedFingerprint.method.apply {
                val firstIfNezIndex = indexOfFirstInstruction(Opcode.IF_NEZ)

                val register = getInstruction(firstIfNezIndex).registersUsed[0]
                val nextInstruction = getInstruction(firstIfNezIndex + 1)

                addInstructionsWithLabels(
                    firstIfNezIndex + 1,
                    """
                    $PREF_CALL_DESCRIPTOR->disableTypingStatus()Z
                    move-result v$register
                    if-eqz v$register, :piko
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko", nextInstruction),
                )
            }

            enableSettings("disableTypingStatus")
        }
    }
