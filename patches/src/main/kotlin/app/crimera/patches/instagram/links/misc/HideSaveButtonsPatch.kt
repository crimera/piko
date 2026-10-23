/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.feedUfiStateField
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val hideSaveButtonsPatch = bytecodePatch(
    name = "Hide save buttons",
    description = "Hides save buttons on posts and reels",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        val saveField = feedUfiStateField(", isSaveEnabled=")

        // Save-only component factories return null when disabled; view renderers skip their binding branch.
        val renderers = listOf(Opcode.IF_EQZ, Opcode.IF_NEZ).flatMap { branch ->
            Fingerprint(
                filters = listOf(
                    fieldAccess(reference = saveField, opcode = Opcode.IGET_BOOLEAN),
                    opcode(branch, location = MatchAfterWithin(2)),
                ),
            ).matchAll(2..2)
        }

        renderers.forEach { match ->
            val readIndex = match.instructionMatches.first().index
            val value = match.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA
            if (match.instructionMatches.last().instruction.registersUsed != listOf(value)) {
                throw PatchException("Save renderer branches on an unexpected register: ${match.method}")
            }
            match.method.addInstructions(
                readIndex + 1,
                """
                invoke-static/range {v$value .. v$value}, $PREF_DESCRIPTOR->showSaveButton(Z)Z
                move-result v$value
                """.trimIndent(),
            )
        }

        Fingerprint(
            name = "invoke",
            returnType = "Ljava/lang/Object;",
            parameters = emptyList(),
            filters = listOf(
                fieldAccess(definingClass = "this", type = "Z", opcode = Opcode.IGET_BOOLEAN),
                string("ClipsSaveButtonUseCase"),
            ),
        ).matchSingle().let { match ->
            val enabledIndex = match.instructionMatches.first().index
            val enabledRegister = match.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA
            match.method.addInstructions(
                enabledIndex + 1,
                """
                invoke-static/range {v$enabledRegister .. v$enabledRegister}, $PREF_DESCRIPTOR->showSaveButton(Z)Z
                move-result v$enabledRegister
                """.trimIndent(),
            )
        }
        enableSettings("hideSaveButtons")
    }
}
