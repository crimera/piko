/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
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
    val type: String,
    val objectConstructor: MethodReference,
    val titleField: FieldReference,
    val nativeModeField: FieldReference,
    val descriptionField: FieldReference,
    val idField: FieldReference,
    val lightId: Int,
    val darkId: Int,
    val systemId: Int,
    val amoledId: Int,
    val nativeTitleId: Int,
)

private data class LegacyTitleBinding(
    val displayIdRegister: Int,
    val titleResourceRegister: Int,
    val titleMoveIndex: Int,
    val titleStringRegister: Int,
)

private data class LegacyOnCreateBinding(
    val method: MutableMethod,
    val insertIndex: Int,
    val listRegister: Int,
    val itemRegister: Int,
    val addReference: MethodReference,
    val itemType: String,
    val listField: FieldReference,
    val receiverRegister: Int,
)

private data class LegacyItemRecord(
    var nativeMode: Int? = null,
    var id: String? = null,
)

context(patchContext: BytecodePatchContext)
internal fun installLegacyAmoledRadio() {
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
    val onCreateBinding = deriveLegacyOnCreateBinding(onCreate)
    val itemBinding =
        deriveLegacyRadioItemBinding(
            itemType = onCreateBinding.itemType,
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
    installLegacyOnResumeRadioHooks(
        binding = onResumeBinding,
        itemBinding = itemBinding,
    )
    installLegacyOnCreateAmoledItem(
        binding = onCreateBinding,
        itemBinding = itemBinding,
        titleId = itemBinding.nativeTitleId,
    )
}

private fun deriveLegacyOnCreateBinding(method: MutableMethod): LegacyOnCreateBinding {
    data class AddCandidate(
        val index: Int,
        val listRegister: Int,
        val itemRegister: Int,
        val addReference: MethodReference,
        val itemType: String,
    )

    val instructions = method.instructions
    val candidates =
        instructions.mapIndexedNotNull { index, instruction ->
            if (
                index == 0 ||
                (
                    instruction.opcode != Opcode.INVOKE_INTERFACE &&
                        instruction.opcode != Opcode.INVOKE_VIRTUAL
                    )
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

            val itemInstruction = instructions[index - 1]
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
                itemRegister = addRegisters[1],
                addReference = addReference,
                itemType = itemField.type,
            )
        }
    val groups =
        candidates
            .groupBy { Triple(it.listRegister, it.itemType, it.addReference.toString()) }
            .values
            .filter { group ->
                group.size == 3 &&
                    group.zipWithNext().all { (first, second) -> second.index == first.index + 2 }
            }
    if (groups.size != 1) {
        throw PatchException(
            "Expected one legacy onCreate sequence with three RadioItem List.add calls, " +
                "found ${groups.size}",
        )
    }
    val group = groups.single().sortedBy(AddCandidate::index)
    val first = group.first()
    val receiverRegister = firstParameterRegister(method)
    val listFields =
        instructions
            .take(first.index)
            .mapIndexedNotNull { index, instruction ->
                if (instruction.opcode != Opcode.IGET_OBJECT) {
                    return@mapIndexedNotNull null
                }
                val reference =
                    (instruction as? ReferenceInstruction)?.reference as? FieldReference
                        ?: return@mapIndexedNotNull null
                val registers = instruction.registersUsed
                if (
                    reference.definingClass == method.definingClass &&
                    reference.type == LIST_DESCRIPTOR &&
                    registers.size == 2 &&
                    registers[0] == first.listRegister &&
                    registers[1] == receiverRegister
                ) {
                    index to reference
                } else {
                    null
                }
            }
    if (listFields.size != 1) {
        throw PatchException(
            "Expected one legacy RadioItem List field read before the three additions, " +
                "found ${listFields.size}",
        )
    }

    val last = group.last()
    return LegacyOnCreateBinding(
        method = method,
        insertIndex = last.index + 1,
        listRegister = last.listRegister,
        itemRegister = last.itemRegister,
        addReference = last.addReference,
        itemType = last.itemType,
        listField = listFields.single().second,
        receiverRegister = receiverRegister,
    )
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
    val descriptionFields = instanceFields.filter { it.type == "Ljava/lang/Integer;" }
    val idFields = instanceFields.filter { it.type == STRING_DESCRIPTOR }
    if (intFields.size != 2 || descriptionFields.size != 1 || idFields.size != 1) {
        throw PatchException(
            "Expected RadioItem fields (two int, Integer, String) in $itemType, found " +
                "${intFields.size}, ${descriptionFields.size}, ${idFields.size}",
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
                val sourceRegister = instruction.registersUsed.firstOrNull()
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
    val titleFields = intFields.filterNot { it.sameField(nativeModeField) }
    if (
        titleFields.size != 1 ||
        intValues.getValue(titleFields.single()).size != 3 ||
        intValues.getValue(titleFields.single()).any { it == 0 }
    ) {
        throw PatchException("Expected one non-zero RadioItem title resource field")
    }
    val titleField = titleFields.single()
    val descriptionField = descriptionFields.single()
    val idField = idFields.single()

    val objectConstructorCalls =
        instructions.mapNotNull { instruction ->
            if (
                instruction.opcode != Opcode.INVOKE_DIRECT &&
                instruction.opcode != Opcode.INVOKE_DIRECT_RANGE
            ) {
                return@mapNotNull null
            }
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapNotNull null
            reference.takeIf {
                it.definingClass == THEME_OBJECT_DESCRIPTOR &&
                    it.name == "<init>" &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == "V"
            }
        }
    if (
        objectConstructorCalls.size != 3 ||
        objectConstructorCalls.distinctBy(MethodReference::toString).size != 1
    ) {
        throw PatchException(
            "Expected three RadioItem Object constructor calls, found " +
                objectConstructorCalls.size,
        )
    }

    val currentItems = mutableMapOf<Int, LegacyItemRecord>()
    val records = mutableListOf<LegacyItemRecord>()
    instructions.forEachIndexed { index, instruction ->
        if (
            instruction.opcode == Opcode.NEW_INSTANCE &&
            (instruction as? ReferenceInstruction)?.reference.toString() == itemType
        ) {
            val register = (instruction as? OneRegisterInstruction)?.registerA
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
            (instruction.opcode != Opcode.IPUT &&
                instruction.opcode != Opcode.IPUT_OBJECT) ||
            field.definingClass != itemType
        ) {
            return@forEachIndexed
        }
        val registers = instruction.registersUsed
        if (registers.size != 2) {
            throw PatchException("RadioItem field assignment has unexpected register count")
        }
        val record = currentItems[registers[1]]
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
    val numericIds =
        records.map { record ->
            record.id?.toIntOrNull()
                ?: throw PatchException("RadioItem id is not numeric: ${record.id}")
        }
    if (numericIds.sorted() != numericIds.indices.toList()) {
        throw PatchException("RadioItem ids are not the expected contiguous list indices")
    }
    val darkRecords = records.filter { it.nativeMode == 2 }
    if (darkRecords.size != 1) {
        throw PatchException("Expected one native Dark RadioItem")
    }
    val darkId =
        darkRecords.single().id?.toInt()
            ?: throw PatchException("Native Dark RadioItem has no numeric id")
    val lightRecords = records.filter { it.nativeMode == 1 }
    if (lightRecords.size != 1) {
        throw PatchException("Expected one native Light RadioItem")
    }
    val lightId =
        lightRecords.single().id?.toInt()
            ?: throw PatchException("Native Light RadioItem has no numeric id")
    val systemRecords = records.filter { it.nativeMode == -1 }
    if (systemRecords.size != 1) {
        throw PatchException("Expected one native System-default RadioItem")
    }
    val systemId =
        systemRecords.single().id?.toInt()
            ?: throw PatchException("Native System-default RadioItem has no numeric id")
    val amoledId = generateSequence(0) { it + 1 }.first { it !in numericIds }
    if (amoledId != records.size) {
        throw PatchException("Derived AMOLED id does not match the appended list index")
    }

    return LegacyRadioItemBinding(
        type = itemType,
        objectConstructor = objectConstructorCalls.first(),
        titleField = titleField,
        nativeModeField = nativeModeField,
        descriptionField = descriptionField,
        idField = idField,
        lightId = lightId,
        darkId = darkId,
        systemId = systemId,
        amoledId = amoledId,
        nativeTitleId = intValues.getValue(titleField).first(),
    )
}

private fun installLegacyOnCreateAmoledItem(
    binding: LegacyOnCreateBinding,
    itemBinding: LegacyRadioItemBinding,
    titleId: Int,
) {
    val method = binding.method
    val insertIndex = binding.insertIndex
    val tempRegister = binding.listRegister
    val registers = listOf(binding.listRegister, binding.itemRegister)
    if (
        registers.distinct().size != registers.size ||
        registers.any { it !in 0..0xf } ||
        binding.receiverRegister !in 0..0xf
    ) {
        throw PatchException("Legacy AMOLED item requires two distinct 4-bit registers")
    }
    val originalInstruction = method.getInstruction(insertIndex)
    method.addInstructionsWithLabels(
        insertIndex,
        """
        invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->isAmoledAvailable()Z
        move-result v${binding.itemRegister}
        if-eqz v${binding.itemRegister}, :piko_legacy_amoled_item_end

        new-instance v${binding.itemRegister}, ${itemBinding.type}
        invoke-direct {v${binding.itemRegister}}, ${itemBinding.objectConstructor}
        const v$tempRegister, $titleId
        iput v$tempRegister, v${binding.itemRegister}, ${itemBinding.titleField}
        const/4 v$tempRegister, 0x2
        iput v$tempRegister, v${binding.itemRegister}, ${itemBinding.nativeModeField}
        const/4 v$tempRegister, 0x0
        iput-object v$tempRegister, v${binding.itemRegister}, ${itemBinding.descriptionField}
        const-string v$tempRegister, "${itemBinding.amoledId}"
        iput-object v$tempRegister, v${binding.itemRegister}, ${itemBinding.idField}
        iget-object v${binding.listRegister}, v${binding.receiverRegister}, ${binding.listField}
        invoke-interface {v${binding.listRegister}, v${binding.itemRegister}}, ${binding.addReference}
        """.trimIndent(),
        ExternalLabel("piko_legacy_amoled_item_end", originalInstruction),
    )
}

private fun installLegacyOnResumeRadioHooks(
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

    val wrapperIdsRegister =
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
    val firstParameter = firstParameterRegister(method)
    val injectedRegisters =
        listOf(
            listenerRegister,
            wrapperIdsRegister,
            selectedIdRegister,
            selectionTempRegister,
        )
    if (
        injectedRegisters.any { it !in 0..0xf || it >= firstParameter } ||
        selectionTempRegister == selectedIdRegister
    ) {
        throw PatchException("Legacy AMOLED RadioGroup hooks require local 4-bit registers")
    }

    val packedIds =
        packLegacyRadioIds(
            amoledId = itemBinding.amoledId,
            lightId = itemBinding.lightId,
            darkId = itemBinding.darkId,
            systemId = itemBinding.systemId,
        )
    method.addInstructions(
        rowNewIndex,
        """
        const v$wrapperIdsRegister, $packedIds
        ${legacyNativeThemeListenerInvocation(listenerRegister, wrapperIdsRegister)}
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
    installLegacyAmoledTitleHook(
        method = method,
        rowNewIndex = rowNewIndex,
        idField = itemBinding.idField,
        titleField = itemBinding.titleField,
        amoledId = itemBinding.amoledId,
    )
}

internal fun installLegacyAmoledTitleHook(
    method: MutableMethod,
    rowNewIndex: Int,
    idField: FieldReference,
    titleField: FieldReference,
    amoledId: Int,
) {
    val firstParameter = firstParameterRegister(method)
    val bindings =
        (0 until rowNewIndex - 3).mapNotNull { idReadIndex ->
            val idRead = method.getInstruction(idReadIndex)
            val idReadField =
                (idRead as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@mapNotNull null
            val idRegisters = idRead.registersUsed
            if (
                idRead.opcode != Opcode.IGET_OBJECT ||
                !idReadField.sameField(idField) ||
                idRegisters.size != 2
            ) {
                return@mapNotNull null
            }

            val titleRead = method.getInstruction(idReadIndex + 1)
            val titleReadField =
                (titleRead as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@mapNotNull null
            val titleRegisters = titleRead.registersUsed
            if (
                titleRead.opcode != Opcode.IGET ||
                !titleReadField.sameField(titleField) ||
                titleRegisters.size != 2 ||
                titleRegisters[1] != idRegisters[1]
            ) {
                return@mapNotNull null
            }

            val titleGetter = method.getInstruction(idReadIndex + 2)
            val titleGetterReference =
                (titleGetter as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapNotNull null
            if (
                titleGetter.opcode != Opcode.INVOKE_VIRTUAL &&
                titleGetter.opcode != Opcode.INVOKE_VIRTUAL_RANGE ||
                titleGetterReference.definingClass !=
                "Landroidx/fragment/app/Fragment;" ||
                titleGetterReference.name != "getString" ||
                titleGetterReference.parameterTypes.map(CharSequence::toString) != listOf("I") ||
                titleGetterReference.returnType != STRING_DESCRIPTOR ||
                titleGetter.registersUsed != listOf(firstParameter, titleRegisters[0])
            ) {
                return@mapNotNull null
            }

            val titleMoveIndex = idReadIndex + 3
            val titleMove = method.getInstruction(titleMoveIndex)
            if (titleMove.opcode != Opcode.MOVE_RESULT_OBJECT) {
                return@mapNotNull null
            }
            val titleStringRegister =
                (titleMove as? OneRegisterInstruction)?.registerA
                    ?: return@mapNotNull null
            LegacyTitleBinding(
                displayIdRegister = idRegisters[0],
                titleResourceRegister = titleRegisters[0],
                titleMoveIndex = titleMoveIndex,
                titleStringRegister = titleStringRegister,
            )
        }
    if (bindings.size != 1) {
        throw PatchException(
            "Expected one displayed RadioItem title binding, found ${bindings.size}",
        )
    }
    val binding = bindings.single()
    if (
        listOf(
            binding.displayIdRegister,
            binding.titleResourceRegister,
            binding.titleStringRegister,
        ).let { registers ->
            registers.distinct().size != registers.size ||
                registers.any { it !in 0 until firstParameter || it > 0xf }
        }
    ) {
        throw PatchException(
            "Legacy AMOLED title hook requires distinct local 4-bit registers",
        )
    }

    method.addInstructions(
        binding.titleMoveIndex + 1,
        """
        const-string v${binding.titleResourceRegister}, "$amoledId"
        invoke-static {v${binding.displayIdRegister}, v${binding.titleResourceRegister}, v${binding.titleStringRegister}}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->getLegacyRadioTitle(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
        move-result-object v${binding.titleStringRegister}
        """.trimIndent(),
    )
}

internal fun packLegacyRadioIds(
    amoledId: Int,
    lightId: Int,
    darkId: Int,
    systemId: Int,
): Int {
    val ids = listOf(amoledId, lightId, darkId, systemId)
    if (ids.any { it !in 0..0xff }) {
        throw PatchException("Legacy theme radio IDs must fit in one unsigned byte")
    }
    return amoledId or
        (lightId shl 8) or
        (darkId shl 16) or
        (systemId shl 24)
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
            (instruction.opcode == Opcode.CONST_STRING ||
                instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
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
