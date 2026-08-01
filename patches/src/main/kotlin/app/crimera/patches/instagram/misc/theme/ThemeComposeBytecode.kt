/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.findFreeRegister
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val MATERIAL_YOU_TOGGLE_NAME = "pikoMaterialYouToggle"
private const val MATERIAL_YOU_WRAPPER_NAME = "pikoMaterialYouToggleRow"

private data class StringResourceCall(
    val literalIndex: Int,
    val resourceRegister: Int,
    val reference: MethodReference,
)

private data class SettingRowCalls(
    val dark: InvokeCall,
    val light: InvokeCall,
    val system: InvokeCall,
)

private data class ComposeRadioBinding(
    val titleGetter: MethodReference,
    val titleResourceRegister: Int,
    val titleRegister: Int,
    val itemFactory: MethodReference,
    val itemFactoryIndex: Int,
    val callbackRegister: Int,
    val selectedRegister: Int,
    val itemRegister: Int,
    val rowReference: MethodReference,
    val composerRegister: Int,
    val styleRegister: Int,
)

context(patchContext: BytecodePatchContext)
internal fun installComposeMaterialYouToggle(
    titleId: Int,
) {
    val darkModeMatch = DarkModeSectionFingerprint.matchAll(1..1).single()
    val reduceMotionMatch = ReduceMotionToggleFingerprint.matchAll(1..1).single()
    val darkModeSection = darkModeMatch.method
    val source = reduceMotionMatch.method

    validateComposableMethods(
        darkModeSection = darkModeSection,
        source = source,
    )

    val owner = source.definingClass
    val composerType = source.parameterTypes[0].toString()
    val ownerClass = patchContext.mutableClassDefBy(owner)
    if (
        ownerClass.methods.any {
            it.name == MATERIAL_YOU_TOGGLE_NAME ||
                it.name == MATERIAL_YOU_WRAPPER_NAME
        }
    ) {
        throw PatchException(
            "Material You toggle methods already exist in $owner",
        )
    }

    val copy =
        MutableMethod(source).apply {
            name = MATERIAL_YOU_TOGGLE_NAME
        }
    validateMethodCopy(source, copy)
    val wrapper =
        createMaterialYouToggleWrapper(
            owner = owner,
            composerType = composerType,
        )

    val stringCalls = findStringResourceCalls(copy, composerType)
    val initialSettingRows =
        findSettingRowCalls(
            method = darkModeSection,
            composerType = composerType,
            composerRegister = firstParameterRegister(darkModeSection),
        )
    installComposeNativeThemeModeSync(
        method = darkModeSection,
        beforeIndex = initialSettingRows.dark.index,
    )
    val settingRows =
        findSettingRowCalls(
            method = darkModeSection,
            composerType = composerType,
            composerRegister = firstParameterRegister(darkModeSection),
        )
    val radioBinding =
        deriveComposeRadioBinding(
            method = darkModeSection,
            composerType = composerType,
            settingRows = settingRows,
        )
    val nativeCallbackRegister =
        parameterRegister(
            method = darkModeSection,
            parameterIndex =
                darkModeSection.parameterTypes.indexOfFirst {
                    it.toString() == FUNCTION1_DESCRIPTOR
                },
        )
    if (nativeCallbackRegister !in 0..0xf) {
        throw PatchException("Compose native theme callback requires a 4-bit register")
    }
    val insertIndex = settingRows.system.index + 1
    val freeRegisters = findTwoLocalRegisters(darkModeSection, insertIndex)
    val stateRegister = freeRegisters[0]
    val callbackRegister = freeRegisters[1]

    copy.replaceInstruction(
        stringCalls[0].literalIndex,
        "const v${stringCalls[0].resourceRegister}, $titleId",
    )
    replaceStringResourceResultWithNull(
        method = copy,
        literalIndex = stringCalls[1].literalIndex,
    )
    redirectMaterialYouRestartCallback(
        source = source,
        copy = copy,
        composerType = composerType,
        targetName = MATERIAL_YOU_TOGGLE_NAME,
    )

    if (!ownerClass.methods.add(copy)) {
        throw PatchException(
            "Failed to add copied method $owner->$MATERIAL_YOU_TOGGLE_NAME",
        )
    }
    if (!ownerClass.methods.add(wrapper)) {
        throw PatchException(
            "Failed to add wrapper method $owner->$MATERIAL_YOU_WRAPPER_NAME",
        )
    }
    val originalInstruction = darkModeSection.getInstruction(insertIndex)
    darkModeSection.addInstructionsWithLabels(
        insertIndex,
        """
        invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->isAvailable()Z
        move-result v$stateRegister
        if-eqz v$stateRegister, :piko_material_you_end

        invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->getToggleCallback()Lkotlin/jvm/functions/Function1;
        move-result-object v$callbackRegister
        invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->isEnabled()Z
        move-result v$stateRegister
        invoke-static {p0, v$callbackRegister, v$stateRegister}, $owner->$MATERIAL_YOU_WRAPPER_NAME(${composerType}Lkotlin/jvm/functions/Function1;Z)V
        """.trimIndent(),
        ExternalLabel("piko_material_you_end", originalInstruction),
    )
    val materialYouInstruction = darkModeSection.getInstruction(insertIndex)
    darkModeSection.addInstructionsWithLabels(
        insertIndex,
        """
        invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->isAmoledAvailable()Z
        move-result v${radioBinding.selectedRegister}
        if-eqz v${radioBinding.selectedRegister}, :piko_amoled_radio_end

        ${composeAmoledTitleInvocation()}
        move-result-object v${radioBinding.titleRegister}
        invoke-static {}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->isAmoledEnabled()Z
        move-result v${radioBinding.selectedRegister}
        invoke-static {v$nativeCallbackRegister}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->getAmoledRadioCallback(Lkotlin/jvm/functions/Function1;)Lkotlin/jvm/functions/Function0;
        move-result-object v${radioBinding.callbackRegister}
        invoke-static {v${radioBinding.callbackRegister}, v${radioBinding.selectedRegister}}, ${radioBinding.itemFactory}
        move-result-object v${radioBinding.itemRegister}
        invoke-static {v${radioBinding.composerRegister}, v${radioBinding.styleRegister}, v${radioBinding.itemRegister}, v${radioBinding.titleRegister}}, ${radioBinding.rowReference}
        """.trimIndent(),
        ExternalLabel("piko_amoled_radio_end", materialYouInstruction),
    )
    darkModeSection.addInstructions(
        radioBinding.itemFactoryIndex,
        """
        invoke-static {v${radioBinding.selectedRegister}}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->shouldSelectNativeDark(Z)Z
        move-result v${radioBinding.selectedRegister}
        """.trimIndent(),
    )
    darkModeSection.addInstructions(
        0,
        """
        invoke-static {v$nativeCallbackRegister}, Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->wrapNativeThemeCallback(Lkotlin/jvm/functions/Function1;)Lkotlin/jvm/functions/Function1;
        move-result-object v$nativeCallbackRegister
        """.trimIndent(),
    )
}

internal fun composeAmoledTitleInvocation(): String =
    "invoke-static {}, " +
        "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->" +
        "getAmoledTitle()Ljava/lang/String;"

internal fun installComposeNativeThemeModeSync(
    method: MutableMethod,
    beforeIndex: Int,
) {
    val instructions = method.instructions
    val candidates =
        instructions.withIndex().mapNotNull { (index, instruction) ->
            if (
                index >= beforeIndex ||
                instruction.opcode !in
                setOf(
                    Opcode.INVOKE_INTERFACE,
                    Opcode.INVOKE_INTERFACE_RANGE,
                )
            ) {
                return@mapNotNull null
            }
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapNotNull null
            if (
                reference.name != "getInt" ||
                reference.parameterTypes.map(CharSequence::toString) !=
                listOf(STRING_DESCRIPTOR, "I") ||
                reference.returnType != "I"
            ) {
                return@mapNotNull null
            }
            val registers = instruction.registersUsed
            if (registers.size != 3 || index + 1 >= instructions.size) {
                return@mapNotNull null
            }
            val defaultRegister = registers[2]
            val defaultValue =
                instructions
                    .subList(maxOf(0, index - 8), index)
                    .asReversed()
                    .mapNotNull { previous ->
                        val register = (previous as? OneRegisterInstruction)?.registerA
                        val literal = previous as? NarrowLiteralInstruction
                        if (register == defaultRegister) literal?.narrowLiteral else null
                    }.firstOrNull()
            if (defaultValue != -1) {
                return@mapNotNull null
            }
            val result = instructions[index + 1]
            val resultRegister = (result as? OneRegisterInstruction)?.registerA
            if (result.opcode != Opcode.MOVE_RESULT || resultRegister !in 0..0xf) {
                return@mapNotNull null
            }
            index + 1 to resultRegister
        }
    val (resultIndex, resultRegister) =
        candidates.singleOrNull()
            ?: throw PatchException(
                "Expected one Compose native theme setting read, found ${candidates.size}",
            )
    method.addInstruction(
        resultIndex + 1,
        "invoke-static {v$resultRegister}, " +
            "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;->" +
            "observeComposeNativeThemeMode(I)V",
    )
}

private fun createMaterialYouToggleWrapper(
    owner: String,
    composerType: String,
): MutableMethod {
    val implementation =
        MethodImplementationBuilder(4).apply {
            addInstruction("const/4 v0, 0x0".toInstruction())
            addInstruction(
                (
                    "invoke-static {v1, v2, v0, v3}, " +
                        "$owner->$MATERIAL_YOU_TOGGLE_NAME(" +
                        "${composerType}${FUNCTION1_DESCRIPTOR}IZ)V"
                ).toInstruction(),
            )
            addInstruction("return-void".toInstruction())
        }.methodImplementation
    val accessFlags =
        AccessFlags.PRIVATE.value or
            AccessFlags.STATIC.value or
            AccessFlags.FINAL.value
    val wrapper =
        MutableMethod(
            ImmutableMethod(
                owner,
                MATERIAL_YOU_WRAPPER_NAME,
                listOf(
                    ImmutableMethodParameter(composerType, emptySet(), null),
                    ImmutableMethodParameter(FUNCTION1_DESCRIPTOR, emptySet(), null),
                    ImmutableMethodParameter("Z", emptySet(), null),
                ),
                "V",
                accessFlags,
                emptySet(),
                emptySet(),
                implementation,
            ),
        )
    val wrapperParameters = wrapper.parameterTypes.map(CharSequence::toString)
    if (
        wrapper.definingClass != owner ||
        wrapper.name != MATERIAL_YOU_WRAPPER_NAME ||
        wrapperParameters != listOf(composerType, FUNCTION1_DESCRIPTOR, "Z") ||
        wrapper.returnType != "V" ||
        !AccessFlags.PRIVATE.isSet(wrapper.accessFlags) ||
        !AccessFlags.STATIC.isSet(wrapper.accessFlags) ||
        wrapper.implementation?.registerCount != 4 ||
        firstParameterRegister(wrapper) != 1 ||
        wrapper.instructions.any { instruction ->
            instruction.registersUsed.any { it !in 0..0xf }
        }
    ) {
        throw PatchException("Invalid Material You toggle wrapper")
    }
    return wrapper
}

private fun validateComposableMethods(
    darkModeSection: MutableMethod,
    source: MutableMethod,
) {
    val darkParameters = darkModeSection.parameterTypes.map(CharSequence::toString)
    val sourceParameters = source.parameterTypes.map(CharSequence::toString)
    if (
        darkModeSection.definingClass != source.definingClass ||
        darkModeSection.returnType != source.returnType ||
        darkModeSection.returnType != "V" ||
        darkParameters.isEmpty() ||
        sourceParameters !=
        listOf(
            darkParameters[0],
            FUNCTION1_DESCRIPTOR,
            "I",
            "Z",
        ) ||
        darkParameters.count { it == FUNCTION1_DESCRIPTOR } != 1 ||
        darkParameters[0] != sourceParameters[0] ||
        !darkParameters[0].isObjectDescriptor() ||
        !AccessFlags.STATIC.isSet(darkModeSection.accessFlags) ||
        !AccessFlags.STATIC.isSet(source.accessFlags)
    ) {
        throw PatchException(
            "Unexpected DarkModeSection/ReduceMotionToggle signatures: " +
                "${darkModeSection.definingClass}->${darkModeSection.name}" +
                "(${darkParameters.joinToString()})${darkModeSection.returnType}, " +
                "${source.definingClass}->${source.name}" +
                "(${sourceParameters.joinToString()})${source.returnType}",
        )
    }
}

private fun validateMethodCopy(
    source: MutableMethod,
    copy: MutableMethod,
) {
    val sourceImplementation =
        source.implementation
            ?: throw PatchException("ReduceMotionToggle has no implementation")
    val copyImplementation =
        copy.implementation
            ?: throw PatchException("Copied ReduceMotionToggle has no implementation")
    val sourceInstructions = source.instructions
    val copyInstructions = copy.instructions
    val sourceBranchOffsets =
        sourceInstructions.filterIsInstance<OffsetInstruction>().map { it.codeOffset }
    val copyBranchOffsets =
        copyInstructions.filterIsInstance<OffsetInstruction>().map { it.codeOffset }

    if (
        sourceImplementation.registerCount != copyImplementation.registerCount ||
        sourceInstructions.size != copyInstructions.size ||
        sourceBranchOffsets != copyBranchOffsets
    ) {
        throw PatchException(
            "ReduceMotionToggle copy did not preserve registers, instructions, and branches",
        )
    }
}

private fun findStringResourceCalls(
    method: MutableMethod,
    composerType: String,
): List<StringResourceCall> {
    val invokes = method.invokeCalls()
    val calls =
        invokes.mapNotNull { call ->
            val parameterTypes = call.reference.parameterTypes.map(CharSequence::toString)
            if (
                call.reference.returnType != STRING_DESCRIPTOR ||
                parameterTypes != listOf(composerType, "I") ||
                !call.reference.definingClass.isObjectDescriptor() ||
                call.registers.size != 2 ||
                call.index == 0
            ) {
                return@mapNotNull null
            }

            val literalInstruction = method.instructions[call.index - 1]
            val oneRegisterInstruction =
                literalInstruction as? OneRegisterInstruction
                    ?: return@mapNotNull null
            val narrowLiteralInstruction =
                literalInstruction as? NarrowLiteralInstruction
                    ?: return@mapNotNull null
            if (
                literalInstruction.opcode != Opcode.CONST ||
                oneRegisterInstruction.registerA != call.registers[1] ||
                narrowLiteralInstruction.narrowLiteral == 0
            ) {
                return@mapNotNull null
            }

            StringResourceCall(
                literalIndex = call.index - 1,
                resourceRegister = oneRegisterInstruction.registerA,
                reference = call.reference,
            )
        }

    val candidates =
        calls
            .groupBy { it.reference.toString() }
            .values
            .filter { group ->
                group.size == 2 &&
                    invokes.count { it.reference.toString() == group[0].reference.toString() } == 2
            }
    if (candidates.size != 1) {
        throw PatchException(
            "Expected exactly one string getter called by two resource literals, " +
                "found ${candidates.size}",
        )
    }

    return candidates.single().sortedBy(StringResourceCall::literalIndex)
}

internal fun replaceStringResourceResultWithNull(
    method: MutableMethod,
    literalIndex: Int,
) {
    val invokeIndex = literalIndex + 1
    val resultIndex = literalIndex + 2
    val instructions = method.instructions
    val invokeReference =
        (instructions.getOrNull(invokeIndex) as? ReferenceInstruction)
            ?.reference as? MethodReference
    val resultInstruction =
        instructions.getOrNull(resultIndex) as? OneRegisterInstruction
    val resultRegister = resultInstruction?.registerA
    if (
        invokeReference?.returnType != STRING_DESCRIPTOR ||
        resultInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT ||
        resultRegister !in 0..0xf
    ) {
        throw PatchException(
            "Expected a String getter followed by a 4-bit move-result-object",
        )
    }

    method.replaceInstruction(
        resultIndex,
        "const/4 v$resultRegister, 0x0",
    )
}

private fun findSettingRowCalls(
    method: MutableMethod,
    composerType: String,
    composerRegister: Int,
): SettingRowCalls {
    val invokes = method.invokeCalls()
    val fourArgumentGroups =
        invokes
            .filter { call ->
                val parameterTypes = call.reference.parameterTypes.map(CharSequence::toString)
                call.reference.returnType == "V" &&
                    call.reference.definingClass.isObjectDescriptor() &&
                    parameterTypes.size == 4 &&
                    parameterTypes[0] == composerType &&
                    parameterTypes.last() == STRING_DESCRIPTOR &&
                    call.registers.size == 4 &&
                    call.registers[0] == composerRegister
            }.groupBy { it.reference.toString() }
            .values
            .filter { it.size == 2 }

    val candidates =
        fourArgumentGroups.mapNotNull { group ->
            val ordered = group.sortedBy(InvokeCall::index)
            val reference = ordered[0].reference
            val parameterTypes = reference.parameterTypes.map(CharSequence::toString)
            val fiveArgumentTypes = parameterTypes + STRING_DESCRIPTOR
            val fiveArgumentCalls =
                invokes.filter { call ->
                    call.reference.definingClass == reference.definingClass &&
                        call.reference.returnType == reference.returnType &&
                        call.reference.parameterTypes.map(CharSequence::toString) ==
                        fiveArgumentTypes &&
                        call.registers.size == 5 &&
                        call.registers[0] == composerRegister
                }
            if (
                fiveArgumentCalls.size != 1 ||
                ordered[0].index >= ordered[1].index ||
                ordered[1].index >= fiveArgumentCalls[0].index
            ) {
                return@mapNotNull null
            }

            SettingRowCalls(
                dark = ordered[0],
                light = ordered[1],
                system = fiveArgumentCalls[0],
            )
        }

    if (candidates.size != 1) {
        throw PatchException(
            "Expected one ordered setting-row family with two 4-argument calls " +
                "and one 5-argument call, found ${candidates.size}",
        )
    }

    return candidates.single()
}

private fun deriveComposeRadioBinding(
    method: MutableMethod,
    composerType: String,
    settingRows: SettingRowCalls,
): ComposeRadioBinding {
    val dark = settingRows.dark
    val instructions = method.instructions
    if (dark.registers.size != 4 || dark.index < 2) {
        throw PatchException("Dark setting row has an unexpected call shape")
    }
    val rowParameterTypes = dark.reference.parameterTypes.map(CharSequence::toString)
    if (
        rowParameterTypes.size != 4 ||
        rowParameterTypes[0] != composerType ||
        rowParameterTypes[3] != STRING_DESCRIPTOR
    ) {
        throw PatchException("Dark setting row has an unexpected method descriptor")
    }

    val itemFactoryInstruction = instructions[dark.index - 2]
    val itemFactory =
        (itemFactoryInstruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: throw PatchException("Dark setting row item factory has no method reference")
    val itemFactoryRegisters = itemFactoryInstruction.registersUsed
    val itemMoveInstruction = instructions[dark.index - 1]
    val itemMoveRegister =
        (itemMoveInstruction as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("Dark setting row item factory has no result register")
    if (
        itemFactoryInstruction.opcode != Opcode.INVOKE_STATIC ||
        itemFactory.parameterTypes.size != 2 ||
        itemFactory.parameterTypes[1].toString() != "Z" ||
        !itemFactory.returnType.isObjectDescriptor() ||
        !rowParameterTypes[2].isObjectDescriptor() ||
        itemFactoryRegisters.size != 2 ||
        itemMoveInstruction.opcode != Opcode.MOVE_RESULT_OBJECT ||
        itemMoveRegister != dark.registers[2]
    ) {
        throw PatchException("Could not derive the Dark setting row item factory")
    }

    val titleRegister = dark.registers[3]
    val titleGetterCalls =
        method.invokeCalls().filter { call ->
            if (
                call.index >= dark.index - 1 ||
                call.reference.returnType != STRING_DESCRIPTOR ||
                call.reference.parameterTypes.map(CharSequence::toString) !=
                listOf(composerType, "I") ||
                call.registers.size != 2 ||
                call.registers[0] != dark.registers[0] ||
                call.index + 1 >= instructions.size
            ) {
                return@filter false
            }
            val moveInstruction = instructions[call.index + 1]
            moveInstruction.opcode == Opcode.MOVE_RESULT_OBJECT &&
                (moveInstruction as? OneRegisterInstruction)?.registerA == titleRegister
        }
    val titleGetterCall =
        titleGetterCalls.lastOrNull()
            ?: throw PatchException("Could not derive the Dark setting row title getter")

    val registers =
        listOf(
            titleGetterCall.registers[1],
            titleRegister,
            itemFactoryRegisters[0],
            itemFactoryRegisters[1],
            dark.registers[2],
            dark.registers[0],
            dark.registers[1],
        )
    if (registers.any { it !in 0..0xf }) {
        throw PatchException("Compose AMOLED RadioItem requires 4-bit registers")
    }

    return ComposeRadioBinding(
        titleGetter = titleGetterCall.reference,
        titleResourceRegister = titleGetterCall.registers[1],
        titleRegister = titleRegister,
        itemFactory = itemFactory,
        itemFactoryIndex = dark.index - 2,
        callbackRegister = itemFactoryRegisters[0],
        selectedRegister = itemFactoryRegisters[1],
        itemRegister = dark.registers[2],
        rowReference = dark.reference,
        composerRegister = dark.registers[0],
        styleRegister = dark.registers[1],
    )
}

private fun findTwoLocalRegisters(
    method: MutableMethod,
    insertIndex: Int,
): List<Int> {
    val firstParameterRegister = firstParameterRegister(method)
    val stateRegister = method.findFreeRegister(insertIndex)
    val callbackRegister = method.findFreeRegister(insertIndex, stateRegister)
    val registers = listOf(stateRegister, callbackRegister)
    if (
        registers.distinct().size != registers.size ||
        registers.any { it < 0 || it >= firstParameterRegister || it > 0xf } ||
        firstParameterRegister > 0xf
    ) {
        throw PatchException(
            "Material You toggle requires two distinct local 4-bit registers " +
                "without overwriting parameter registers; found $registers",
        )
    }

    return registers
}

private fun parameterRegister(
    method: MutableMethod,
    parameterIndex: Int,
): Int {
    if (parameterIndex !in method.parameterTypes.indices) {
        throw PatchException(
            "Could not derive parameter register $parameterIndex for " +
                "${method.definingClass}->${method.name}",
        )
    }
    return firstParameterRegister(method) +
        method.parameterTypes.take(parameterIndex).sumOf { type ->
            if (type == "J" || type == "D") 2 else 1
        }
}
