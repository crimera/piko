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
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.util.matchSingle
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
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

        // Download media creates a separate component inside the same native save branch,
        // so the Litho renderer skips only the final save insertion.
        val lithoMatch = Fingerprint(
            filters = listOf(
                fieldAccess(reference = saveField, opcode = Opcode.IGET_BOOLEAN),
                opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
                resourceLiteral(ResourceType.ID, "row_feed_button_save"),
            ),
        ).matchSingle()
        val renderers = Fingerprint(
            filters = listOf(
                fieldAccess(reference = saveField, opcode = Opcode.IGET_BOOLEAN),
                opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            ),
        ).matchAll(3..3).filter { it.originalMethod != lithoMatch.originalMethod }

        lithoMatch.method.apply {
            val end = getInstruction<BuilderOffsetInstruction>(lithoMatch.instructionMatches[1].index).target.location.index
            // Last insertion of the save branch, after the download component.
            val addIndex = indexOfFirstInstructionReversedOrThrow(
                end - 1,
                methodCall(smali = "Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z"),
            )
            val scratch = findFreeRegister(addIndex, getInstruction(addIndex).registersUsed)
            addInstructionsWithLabels(
                addIndex,
                """
                invoke-static {}, $PREF_DESCRIPTOR->hideSaveButtons()Z
                move-result v$scratch
                if-nez v$scratch, :skip_save
                """.trimIndent(),
                ExternalLabel("skip_save", getInstruction(end)),
            )
        }

        renderers.forEach { match ->
            val readIndex = match.instructionMatches.first().index
            val value = match.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA
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
