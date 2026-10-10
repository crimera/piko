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
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val hideSaveButtonsPatch = bytecodePatch(
    name = "Hide save buttons",
    description = "Hides save buttons on posts and reels",
) {
    dependsOn(settingsPatch, resourceMappingPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        val state = Fingerprint(
            name = "toString",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
            filters = listOf(
                string("MediaUfiUiState(mediaForActionHandler="),
                string(", isSaveEnabled="),
            ),
        ).matchSingle()
        val code = state.method.implementation!!.instructions
        val label = state.instructionMatches[1].index
        val append = (label + 1 until code.size)
            .takeWhile { code[it].getReference<StringReference>() == null }
            .firstOrNull {
                code[it].getReference<MethodReference>()?.toString() ==
                    "Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;"
            } ?: throw PatchException("Could not resolve the feed save state value")
        val appendRegisters = code[append].registersUsed
        if (appendRegisters.size != 2) throw PatchException("Unexpected feed save append registers")
        var register = appendRegisters[1]
        var before = append
        var field: FieldReference? = null
        // The state toString() moves values through temporary registers before appending them.
        while (field == null) {
            val index = (before - 1 downTo 0).firstOrNull {
                code[it].opcode.setsRegister() && code[it].registersUsed.firstOrNull() == register
            } ?: throw PatchException("Could not trace the feed save state field")
            val read = code[index]
            when (read.opcode) {
                Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16 -> {
                    val registers = read.registersUsed
                    if (registers.size != 2) throw PatchException("Unexpected feed save move registers")
                    register = registers[1]
                    before = index
                }
                Opcode.IGET_BOOLEAN -> {
                    field = read.getReference<FieldReference>()
                    if (field?.type != "Z" || field.definingClass != state.classDef.type) {
                        throw PatchException("Unexpected feed save state field")
                    }
                }
                else -> throw PatchException("Unexpected feed save state instruction")
            }
        }
        val saveField = field.toString()
        val saveId = getResourceId(ResourceType.ID, "row_feed_button_save")
        val visualSearch = Fingerprint(
            filters = listOf(string("getVisualSearchButtonComponent")),
        ).matchSingle().method
        val readers = buildList {
            classDefForEach { classDef ->
                if (classDef.type != state.classDef.type) {
                    classDef.methods.filterTo(this) { method ->
                        method.toString() != visualSearch.toString() && method.implementation?.instructions?.any {
                            it.opcode == Opcode.IGET_BOOLEAN &&
                                it.getReference<FieldReference>()?.toString() == saveField
                        } == true
                    }
                }
            }
        }
        if (readers.size != 3) throw PatchException("Expected three feed save renderers, found ${readers.size}")
        var lithoRenderers = 0
        for (reader in readers) {
            val method = mutableClassDefBy(reader.definingClass).methods.single { it.toString() == reader.toString() }
            val instructions = method.implementation!!.instructions
            val readIndex = instructions.indices.singleOrNull {
                instructions[it].opcode == Opcode.IGET_BOOLEAN &&
                    instructions[it].getReference<FieldReference>()?.toString() == saveField
            } ?: throw PatchException("Expected one save state read in $method")
            val readRegisters = instructions[readIndex].registersUsed
            if (readRegisters.size != 2) throw PatchException("Unexpected save state registers")
            val value = readRegisters[0]
            val branch = instructions.getOrNull(readIndex + 1)
            if (branch?.opcode != Opcode.IF_EQZ || branch.registersUsed.singleOrNull() != value) {
                throw PatchException("Could not resolve the feed save visibility branch")
            }
            if (instructions.any { (it as? WideLiteralInstruction)?.wideLiteral == saveId }) {
                lithoRenderers++
                val end = (branch as BuilderOffsetInstruction).target.location.index
                val saveIndex = (readIndex + 2 until end).lastOrNull {
                    (instructions[it] as? WideLiteralInstruction)?.wideLiteral == saveId
                } ?: throw PatchException("Save resource is outside its visibility branch")
                val addIndex = (saveIndex + 1 until end).lastOrNull {
                    instructions[it].getReference<MethodReference>()?.toString() ==
                        "Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z"
                } ?: throw PatchException("Could not resolve the save component insertion")
                if (addIndex + 1 != end) throw PatchException("Unexpected save component continuation")
                val scratch = method.findFreeRegister(addIndex, instructions[addIndex].registersUsed)
                if (scratch > 255) throw PatchException("No encodable register for the save setting")
                // Download media creates a separate component inside the same native save branch.
                // Skip only the final save insertion so the download component remains available.
                method.addInstructionsWithLabels(
                    addIndex,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR->hideSaveButtons()Z
                    move-result v$scratch
                    if-nez v$scratch, :skip_save
                    """.trimIndent(),
                    ExternalLabel("skip_save", method.getInstruction(end)),
                )
            } else {
                method.addInstructions(
                    readIndex + 1,
                    """
                    invoke-static/range {v$value .. v$value}, $PREF_DESCRIPTOR->showSaveButton(Z)Z
                    move-result v$value
                    """.trimIndent(),
                )
            }
        }
        if (lithoRenderers != 1) throw PatchException("Expected one Litho save component renderer")

        val reels = Fingerprint(
            name = "invoke",
            returnType = "Ljava/lang/Object;",
            parameters = emptyList(),
            filters = listOf(string("ClipsSaveButtonUseCase")),
        ).matchSingle().method
        val reelsCode = reels.implementation!!.instructions
        val enabledIndex = reelsCode.indices.singleOrNull { reelsCode[it].opcode == Opcode.IGET_BOOLEAN }
            ?: throw PatchException("Expected one Reels save visibility field")
        val enabled = reelsCode[enabledIndex]
        val enabledField = enabled.getReference<FieldReference>()
        val enabledRegisters = enabled.registersUsed
        if (enabledField?.type != "Z" || enabledField.definingClass != reels.definingClass || enabledRegisters.size != 2) {
            throw PatchException("Unexpected Reels save visibility field")
        }
        val enabledRegister = enabledRegisters[0]
        reels.addInstructions(
            enabledIndex + 1,
            """
            invoke-static/range {v$enabledRegister .. v$enabledRegister}, $PREF_DESCRIPTOR->showSaveButton(Z)Z
            move-result v$enabledRegister
            """.trimIndent(),
        )
        enableSettings("hideSaveButtons")
    }
}
