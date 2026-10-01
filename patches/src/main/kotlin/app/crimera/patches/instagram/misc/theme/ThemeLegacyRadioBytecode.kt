/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.shared.parameterRegisterStart
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.findFreeRegister
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private data class LegacyRadioItemBinding(
    val idField: FieldReference,
    val lightId: Int,
    val darkId: Int,
    val systemId: Int,
)

private data class LegacyItemRecord(
    var nativeMode: Int? = null,
    var id: String? = null,
)

context(patchContext: BytecodePatchContext)
internal fun installLegacyNativeThemeModeSync() {
    val fragmentConstructor =
        LegacyDarkModeFragmentConstructorFingerprint
            .matchAll(1..1)
            .single()
            .method
    val owner = fragmentConstructor.definingClass
    val ownerClass = patchContext.mutableClassDefBy(owner)
    val onCreate =
        ownerClass.methods.singleOrNull { method ->
            method.name == "onCreate" &&
                method.parameterTypes.map(CharSequence::toString) == listOf(BUNDLE_DESCRIPTOR) &&
                method.returnType == "V" &&
                !AccessFlags.STATIC.isSet(method.accessFlags)
        } ?: throw PatchException("Expected one legacy theme onCreate(Bundle) method in $owner")
    val itemBinding =
        deriveLegacyRadioItemBinding(
            itemType = deriveLegacyRadioItemType(onCreate),
        )

    val onResumeBinding =
        ownerClass.methods.mapNotNull { method ->
            if (
                method.name == "onResume" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "V" &&
                !AccessFlags.STATIC.isSet(method.accessFlags)
            ) {
                findLegacyBinding(method)
            } else {
                null
            }
        }.singleOrNull()
            ?: throw PatchException(
                "Expected one legacy theme onResume RadioGroup/adapter binding in $owner",
            )
    installLegacyOnResumeThemeSync(
        binding = onResumeBinding,
        itemBinding = itemBinding,
    )
}

private fun deriveLegacyRadioItemType(method: MutableMethod): String {
    data class AddCandidate(
        val index: Int,
        val listRegister: Int,
        val itemType: String,
        val addReference: MethodReference,
    )

    val candidates =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (
                index == 0 ||
                instruction.opcode !in setOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_VIRTUAL)
            ) {
                return@mapIndexedNotNull null
            }
            val addReference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapIndexedNotNull null
            val addRegisters = instruction.registersUsed
            if (
                addReference.name != "add" ||
                addReference.parameterTypes.map(CharSequence::toString) !=
                listOf(THEME_OBJECT_DESCRIPTOR) ||
                addReference.returnType != "Z" ||
                addRegisters.size != 2
            ) {
                return@mapIndexedNotNull null
            }

            val itemInstruction = method.getInstruction(index - 1)
            val itemField =
                (itemInstruction as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@mapIndexedNotNull null
            val itemRegisters = itemInstruction.registersUsed
            if (
                itemInstruction.opcode != Opcode.SGET_OBJECT ||
                itemRegisters.size != 1 ||
                itemRegisters[0] != addRegisters[1] ||
                !itemField.type.isObjectDescriptor()
            ) {
                return@mapIndexedNotNull null
            }

            AddCandidate(
                index = index,
                listRegister = addRegisters[0],
                itemType = itemField.type,
                addReference = addReference,
            )
        }
    val groups =
        candidates
            .groupBy { Triple(it.listRegister, it.itemType, it.addReference.toString()) }
            .values
            .filter { group ->
                group.size == 3 &&
                    group.sortedBy(AddCandidate::index).zipWithNext().all { (first, second) ->
                        second.index == first.index + 2
                    }
            }
    if (groups.size != 1) {
        throw PatchException(
            "Expected one legacy onCreate sequence with three RadioItem List.add calls, " +
                "found ${groups.size}",
        )
    }
    return groups.single().first().itemType
}

context(patchContext: BytecodePatchContext)
private fun deriveLegacyRadioItemBinding(itemType: String): LegacyRadioItemBinding {
    val itemClass = patchContext.mutableClassDefBy(itemType)
    val classInitializer =
        itemClass.methods.singleOrNull { method ->
            method.name == "<clinit>" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "V" &&
                AccessFlags.STATIC.isSet(method.accessFlags)
        } ?: throw PatchException("Expected one RadioItem class initializer in $itemType")
    val instructions = classInitializer.instructions
    val instanceFields =
        instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IPUT && instruction.opcode != Opcode.IPUT_OBJECT) {
                return@mapNotNull null
            }
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@mapNotNull null
            reference.takeIf { it.definingClass == itemType }
        }.distinctBy(FieldReference::fieldKey)
    val intFields = instanceFields.filter { it.type == "I" }
    val idFields = instanceFields.filter { it.type == STRING_DESCRIPTOR }
    if (intFields.size != 2 || idFields.size != 1) {
        throw PatchException(
            "Expected RadioItem fields (two int and one String) in $itemType, found " +
                "${intFields.size} and ${idFields.size}",
        )
    }

    val intValues =
        intFields.associateWith { field ->
            instructions.mapIndexedNotNull { index, instruction ->
                val reference =
                    (instruction as? ReferenceInstruction)?.reference as? FieldReference
                        ?: return@mapIndexedNotNull null
                if (instruction.opcode != Opcode.IPUT || !reference.sameField(field)) {
                    return@mapIndexedNotNull null
                }
                val sourceRegister =
                    instruction.registersUsed.firstOrNull()
                        ?: return@mapIndexedNotNull null
                findPreviousNarrowLiteral(classInitializer, index, sourceRegister)
            }
        }
    val nativeModeFields =
        intValues.filterValues { values ->
            values.size == 3 && values.toSet() == setOf(2, 1, -1)
        }.keys
    if (nativeModeFields.size != 1) {
        throw PatchException(
            "Expected one RadioItem native-mode field with values 2, 1, -1, " +
                "found ${nativeModeFields.size}",
        )
    }
    val nativeModeField = nativeModeFields.single()
    val idField = idFields.single()

    val currentItems = mutableMapOf<Int, LegacyItemRecord>()
    val records = mutableListOf<LegacyItemRecord>()
    instructions.forEachIndexed { index, instruction ->
        if (
            instruction.opcode == Opcode.NEW_INSTANCE &&
            (instruction as? ReferenceInstruction)?.reference.toString() == itemType
        ) {
            val register =
                (instruction as? OneRegisterInstruction)?.registerA
                    ?: throw PatchException("RadioItem new-instance has no destination register")
            val record = LegacyItemRecord()
            currentItems[register] = record
            records += record
            return@forEachIndexed
        }

        val field =
            (instruction as? ReferenceInstruction)?.reference as? FieldReference
                ?: return@forEachIndexed
        if (
            instruction.opcode !in setOf(Opcode.IPUT, Opcode.IPUT_OBJECT) ||
            field.definingClass != itemType
        ) {
            return@forEachIndexed
        }
        val registers = instruction.registersUsed
        if (registers.size != 2) {
            throw PatchException("RadioItem field assignment has unexpected register count")
        }
        val record =
            currentItems[registers[1]]
                ?: throw PatchException("RadioItem field assignment has no matching new-instance")
        when {
            field.sameField(nativeModeField) ->
                record.nativeMode =
                    findPreviousNarrowLiteral(classInitializer, index, registers[0])
            field.sameField(idField) ->
                record.id = findPreviousStringLiteral(classInitializer, index, registers[0])
        }
    }
    if (records.size != 3 || records.any { it.nativeMode == null || it.id == null }) {
        throw PatchException("Expected three complete native-mode/id RadioItem records")
    }

    fun idFor(nativeMode: Int, label: String): Int {
        val matching = records.filter { it.nativeMode == nativeMode }
        if (matching.size != 1) {
            throw PatchException("Expected one native $label RadioItem")
        }
        return matching.single().id?.toIntOrNull()
            ?: throw PatchException("Native $label RadioItem has no numeric id")
    }

    val lightId = idFor(1, "Light")
    val darkId = idFor(2, "Dark")
    val systemId = idFor(-1, "System-default")
    if (listOf(lightId, darkId, systemId).distinct().size != 3) {
        throw PatchException("Legacy native theme RadioItem ids must be distinct")
    }

    return LegacyRadioItemBinding(
        idField = idField,
        lightId = lightId,
        darkId = darkId,
        systemId = systemId,
    )
}

private fun installLegacyOnResumeThemeSync(
    binding: LegacyBinding,
    itemBinding: LegacyRadioItemBinding,
) {
    val method = binding.method
    val constructor = binding.radioConstructor
    if (constructor.registers.size != 4 || constructor.index == 0) {
        throw PatchException("Legacy RadioGroup row constructor has an unexpected shape")
    }
    val rowRegister = constructor.registers[0]
    val listenerRegister = constructor.registers[1]
    val selectedIdRegister = constructor.registers[2]
    val rowNewIndex = constructor.index - 1
    val rowNewInstruction = method.getInstruction(rowNewIndex)
    if (
        rowNewInstruction.opcode != Opcode.NEW_INSTANCE ||
        (rowNewInstruction as? OneRegisterInstruction)?.registerA != rowRegister ||
        (rowNewInstruction as? ReferenceInstruction)?.reference.toString() !=
        constructor.reference.definingClass
    ) {
        throw PatchException("Could not derive the legacy RadioGroup row new-instance")
    }
    val selectedIdReads =
        method.instructions
            .take(rowNewIndex)
            .mapIndexedNotNull { index, instruction ->
                val field =
                    (instruction as? ReferenceInstruction)?.reference as? FieldReference
                        ?: return@mapIndexedNotNull null
                val registers = instruction.registersUsed
                if (
                    instruction.opcode == Opcode.IGET_OBJECT &&
                    field.sameField(itemBinding.idField) &&
                    registers.size == 2 &&
                    registers[0] == selectedIdRegister
                ) {
                    index
                } else {
                    null
                }
            }
    if (selectedIdReads.size != 1) {
        throw PatchException(
            "Expected one selected RadioItem id read, found ${selectedIdReads.size}",
        )
    }

    val packedIdsRegister =
        method.findFreeRegister(
            rowNewIndex,
            rowRegister,
            listenerRegister,
            selectedIdRegister,
        )
    val selectionTempRegister =
        method.findFreeRegister(
            selectedIdReads.single() + 1,
            selectedIdRegister,
        )
    val firstParameter = parameterRegisterStart(method)
    val injectedRegisters =
        listOf(
            listenerRegister,
            packedIdsRegister,
            selectedIdRegister,
            selectionTempRegister,
        )
    if (
        injectedRegisters.any { it !in 0..0xf || it >= firstParameter } ||
        selectionTempRegister == selectedIdRegister
    ) {
        throw PatchException("Legacy theme RadioGroup sync requires local 4-bit registers")
    }

    val packedIds =
        packLegacyNativeThemeIds(
            lightId = itemBinding.lightId,
            darkId = itemBinding.darkId,
            systemId = itemBinding.systemId,
        )
    method.addInstructions(
        rowNewIndex,
        """
        const v$packedIdsRegister, $packedIds
        ${legacyNativeThemeListenerInvocation(listenerRegister, packedIdsRegister)}
        move-result-object v$listenerRegister
        """.trimIndent(),
    )
    val selectedIdIndex = selectedIdReads.single()
    method.addInstructions(
        selectedIdIndex + 1,
        """
        const v$selectionTempRegister, $packedIds
        invoke-static {v$selectedIdRegister, v$selectionTempRegister}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->getLegacySelectedRadioId(Ljava/lang/String;I)Ljava/lang/String;
        move-result-object v$selectedIdRegister
        """.trimIndent(),
    )
}

internal fun packLegacyNativeThemeIds(
    lightId: Int,
    darkId: Int,
    systemId: Int,
): Int {
    val ids = listOf(lightId, darkId, systemId)
    if (ids.any { it !in 0..0xff }) {
        throw PatchException("Legacy theme radio IDs must fit in one unsigned byte")
    }
    return lightId or (darkId shl 8) or (systemId shl 16)
}

private fun findPreviousNarrowLiteral(
    method: MutableMethod,
    instructionIndex: Int,
    register: Int,
): Int {
    for (index in instructionIndex - 1 downTo maxOf(0, instructionIndex - 8)) {
        val instruction = method.getInstruction(index)
        if (
            instruction is OneRegisterInstruction &&
            instruction is NarrowLiteralInstruction &&
            instruction.registerA == register
        ) {
            return instruction.narrowLiteral
        }
    }
    throw PatchException(
        "Could not derive narrow literal for v$register before " +
            "${method.definingClass}->${method.name} instruction $instructionIndex",
    )
}

private fun findPreviousStringLiteral(
    method: MutableMethod,
    instructionIndex: Int,
    register: Int,
): String {
    for (index in instructionIndex - 1 downTo maxOf(0, instructionIndex - 8)) {
        val instruction = method.getInstruction(index)
        if (
            instruction.opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
            (instruction as? OneRegisterInstruction)?.registerA == register
        ) {
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? StringReference
                    ?: throw PatchException("String literal instruction has no StringReference")
            return reference.string
        }
    }
    throw PatchException(
        "Could not derive string literal for v$register before " +
            "${method.definingClass}->${method.name} instruction $instructionIndex",
    )
}

private fun FieldReference.fieldKey(): Triple<String, String, String> =
    Triple(definingClass, name, type)

private fun FieldReference.sameField(other: FieldReference): Boolean =
    fieldKey() == other.fieldKey()
