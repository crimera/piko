/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderSwitchElement
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

private data class RestartRegistration(
    val literalIndex: Int,
    val selectorRegister: Int,
    val selector: Int,
    val helperReference: MethodReference,
)

private data class NewCallbackSelector(
    val selector: Int,
    val callbackInvoke: MutableMethod,
    val switchIndex: Int,
    val originalSwitch: BuilderInstruction31t,
    val selectorRegister: Int,
    val receiverRegister: Int,
    val firstArgumentRegister: Int,
    val secondArgumentRegister: Int,
    val composerRegister: Int,
    val booleanRegister: Int,
    val callbackRegister: Int,
    val flagsRegister: Int,
    val composerConverter: MethodReference,
    val booleanField: FieldReference,
    val callbackField: FieldReference,
    val flagsHelper: MethodReference,
    val returnInstructionIndex: Int,
)

context(patchContext: BytecodePatchContext)
internal fun redirectMaterialYouRestartCallback(
    source: MutableMethod,
    copy: MutableMethod,
    composerType: String,
    targetName: String,
) {
    val sourceRegistration = findRestartRegistration(source, composerType)
    val copiedRegistration = findRestartRegistration(copy, composerType)
    if (
        sourceRegistration.literalIndex != copiedRegistration.literalIndex ||
        sourceRegistration.selectorRegister != copiedRegistration.selectorRegister ||
        sourceRegistration.selector != copiedRegistration.selector ||
        !sourceRegistration.helperReference.sameMethodAs(
            copiedRegistration.helperReference,
        )
    ) {
        throw PatchException(
            "Copied ReduceMotionToggle restart registration did not match its source",
        )
    }

    val selector =
        findNewCallbackSelector(
            source = source,
            registration = sourceRegistration,
            composerType = composerType,
        )
    val targetDescriptor =
        "${source.definingClass}->$targetName(" +
            source.parameterTypes.joinToString(separator = "") +
            ")${source.returnType}"
    val returnInstruction =
        selector.callbackInvoke.getInstruction(selector.returnInstructionIndex)

    copy.replaceInstruction(
        copiedRegistration.literalIndex,
        "const v${copiedRegistration.selectorRegister}, ${selector.selector}",
    )
    selector.callbackInvoke.addInstructionsWithLabels(
        selector.switchIndex,
        """
        add-int/lit8 v${selector.selectorRegister}, v${selector.selectorRegister}, -${selector.selector}
        if-eqz v${selector.selectorRegister}, :piko_material_you_restart
        add-int/lit8 v${selector.selectorRegister}, v${selector.selectorRegister}, ${selector.selector}
        goto/32 :piko_original_restart_switch

        :piko_material_you_restart
        invoke-static {v${selector.firstArgumentRegister}, v${selector.secondArgumentRegister}}, ${selector.composerConverter}
        move-result-object v${selector.composerRegister}
        iget-boolean v${selector.booleanRegister}, v${selector.receiverRegister}, ${selector.booleanField}
        iget-object v${selector.callbackRegister}, v${selector.receiverRegister}, ${selector.callbackField}
        check-cast v${selector.callbackRegister}, $FUNCTION1_DESCRIPTOR
        invoke-static {v${selector.receiverRegister}}, ${selector.flagsHelper}
        move-result v${selector.flagsRegister}
        invoke-static {v${selector.composerRegister}, v${selector.callbackRegister}, v${selector.flagsRegister}, v${selector.booleanRegister}}, $targetDescriptor
        goto/32 :piko_restart_return
        """.trimIndent(),
        ExternalLabel("piko_original_restart_switch", selector.originalSwitch),
        ExternalLabel("piko_restart_return", returnInstruction),
    )
}

private fun findRestartRegistration(
    method: MutableMethod,
    composerType: String,
): RestartRegistration {
    val firstParameter = firstParameterRegister(method)
    val composerParameter = firstParameter
    val callbackParameter = firstParameter + 1
    val flagsParameter = firstParameter + 2
    val booleanParameter = firstParameter + 3
    val instructions = method.instructions
    val candidates =
        instructions.mapIndexedNotNull { index, instruction ->
            if (
                index < 4 ||
                instruction.opcode !in
                setOf(
                    Opcode.INVOKE_STATIC,
                    Opcode.INVOKE_STATIC_RANGE,
                )
            ) {
                return@mapIndexedNotNull null
            }
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapIndexedNotNull null
            val parameterTypes = reference.parameterTypes.map(CharSequence::toString)
            val registers = instruction.registersUsed
            if (
                reference.returnType != "V" ||
                parameterTypes.size != 5 ||
                !parameterTypes[0].isReferenceDescriptor() ||
                parameterTypes.drop(1) != listOf(OBJECT_DESCRIPTOR, "I", "I", "Z") ||
                registers.size != 5 ||
                registers[1] != callbackParameter ||
                registers[2] != flagsParameter ||
                registers[4] != booleanParameter
            ) {
                return@mapIndexedNotNull null
            }

            val literalInstruction = instructions[index - 1]
            val literalRegister =
                (literalInstruction as? OneRegisterInstruction)?.registerA
                    ?: return@mapIndexedNotNull null
            val selector =
                (literalInstruction as? NarrowLiteralInstruction)?.narrowLiteral
                    ?: return@mapIndexedNotNull null
            if (
                literalInstruction.opcode !in
                setOf(Opcode.CONST, Opcode.CONST_4, Opcode.CONST_16) ||
                literalRegister != registers[3]
            ) {
                return@mapIndexedNotNull null
            }

            val nullCheck = instructions[index - 2] as? OneRegisterInstruction
            val moveResult = instructions[index - 3] as? OneRegisterInstruction
            val scopeGetterInstruction = instructions[index - 4]
            val scopeGetter =
                (scopeGetterInstruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapIndexedNotNull null
            if (
                instructions[index - 2].opcode != Opcode.IF_EQZ ||
                nullCheck?.registerA != registers[0] ||
                instructions[index - 3].opcode != Opcode.MOVE_RESULT_OBJECT ||
                moveResult?.registerA != registers[0] ||
                scopeGetterInstruction.opcode !in
                setOf(
                    Opcode.INVOKE_INTERFACE,
                    Opcode.INVOKE_INTERFACE_RANGE,
                ) ||
                scopeGetter.parameterTypes.isNotEmpty() ||
                scopeGetter.returnType != parameterTypes[0] ||
                scopeGetter.definingClass != composerType ||
                scopeGetterInstruction.registersUsed != listOf(composerParameter)
            ) {
                return@mapIndexedNotNull null
            }

            RestartRegistration(
                literalIndex = index - 1,
                selectorRegister = literalRegister,
                selector = selector,
                helperReference = reference,
            )
        }

    if (candidates.size != 1) {
        throw PatchException(
            "Expected exactly one ReduceMotionToggle restart registration, " +
                "found ${candidates.size}",
        )
    }
    return candidates.single()
}

context(patchContext: BytecodePatchContext)
private fun findNewCallbackSelector(
    source: MutableMethod,
    registration: RestartRegistration,
    composerType: String,
): NewCallbackSelector {
    val helperReference = registration.helperReference
    val helperClass = patchContext.mutableClassDefBy(helperReference.definingClass)
    val helperMethods =
        helperClass.methods.filter { it.sameMethodAs(helperReference) }
    if (helperMethods.size != 1) {
        throw PatchException(
            "Expected exactly one restart callback helper, found ${helperMethods.size}",
        )
    }
    val helper = helperMethods.single()
    if (!AccessFlags.STATIC.isSet(helper.accessFlags)) {
        throw PatchException("Restart callback helper must be static")
    }

    val callbackClassDescriptor = findCallbackClassDescriptor(helper, helperReference)
    val callbackClass = patchContext.mutableClassDefBy(callbackClassDescriptor)
    val callbackInvokes =
        callbackClass.methods.filter { method ->
            method.name == "invoke" &&
                method.parameterTypes.map(CharSequence::toString) ==
                listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR) &&
                method.returnType == OBJECT_DESCRIPTOR &&
                !AccessFlags.STATIC.isSet(method.accessFlags)
        }
    if (callbackInvokes.size != 1) {
        throw PatchException(
            "Expected exactly one restart callback invoke method, found ${callbackInvokes.size}",
        )
    }
    val callbackInvoke = callbackInvokes.single()
    val switches =
        callbackInvoke.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.PACKED_SWITCH) {
                return@mapIndexedNotNull null
            }
            val switchInstruction = instruction as? BuilderInstruction31t
                ?: throw PatchException("Restart callback switch is not mutable")
            val payload =
                switchInstruction.target.location.instruction as? SwitchPayload
                    ?: throw PatchException("Restart callback switch payload is missing")
            Triple(index, switchInstruction, payload)
        }
    if (switches.size != 1) {
        throw PatchException(
            "Expected exactly one packed switch in restart callback, found ${switches.size}",
        )
    }

    val (switchIndex, switchInstruction, payload) = switches.single()
    val switchElements = payload.switchElements
    if (switchElements.isEmpty()) {
        throw PatchException("Restart callback switch has no cases")
    }
    val sourceElements =
        switchElements.filter { it.key == registration.selector }
    if (sourceElements.size != 1) {
        throw PatchException(
            "Expected one restart callback case for selector ${registration.selector}, " +
                "found ${sourceElements.size}",
        )
    }
    val sourceElement = sourceElements.single() as? BuilderSwitchElement
        ?: throw PatchException("Restart callback switch case is not mutable")
    val caseIndex = sourceElement.target.location.index

    val selectorFieldInstruction =
        callbackInvoke.instructions
            .take(switchIndex)
            .filter { instruction ->
                instruction.opcode == Opcode.IGET &&
                    (instruction as? OneRegisterInstruction)?.registerA ==
                    switchInstruction.registerA
            }
    if (selectorFieldInstruction.size != 1) {
        throw PatchException(
            "Expected one restart callback selector field read, " +
                "found ${selectorFieldInstruction.size}",
        )
    }
    val selectorRead = selectorFieldInstruction.single()
    val selectorField =
        (selectorRead as? ReferenceInstruction)?.reference as? FieldReference
            ?: throw PatchException("Restart callback selector field reference is missing")
    val receiverRegister =
        (selectorRead as? TwoRegisterInstruction)?.registerB
            ?: throw PatchException("Restart callback selector receiver is missing")
    if (
        selectorField.definingClass != callbackClassDescriptor ||
        selectorField.type != "I" ||
        !callbackInvoke.hasObjectMoveFromParameter(
            destination = receiverRegister,
            parameterIndex = 0,
            beforeIndex = switchIndex,
        )
    ) {
        throw PatchException("Unexpected restart callback selector field access")
    }

    val switchKeys = switchElements.map { it.key }.toSet()
    val nextSelector =
        switchKeys.maxOrNull()
            ?.takeIf { it != Int.MAX_VALUE }
            ?.plus(1)
            ?: throw PatchException("Restart callback selector range is exhausted")
    val comparedSelectors =
        findComparedSelectorValues(callbackClass.methods, selectorField)
    if (nextSelector in switchKeys || nextSelector in comparedSelectors) {
        throw PatchException(
            "New restart callback selector $nextSelector collides with an existing selector",
        )
    }
    if (
        nextSelector !in Byte.MIN_VALUE..Byte.MAX_VALUE ||
        -nextSelector !in Byte.MIN_VALUE..Byte.MAX_VALUE
    ) {
        throw PatchException(
            "New restart callback selector $nextSelector does not fit add-int/lit8",
        )
    }

    val caseInstructions =
        (0..8).map { offset ->
            callbackInvoke.instructions.getOrNull(caseIndex + offset)
                ?: throw PatchException("Restart callback case is truncated")
        }
    val composerConverter =
        caseInstructions[0].staticCall()
            ?: throw PatchException("Restart callback composer conversion is missing")
    val composerRegisters = caseInstructions[0].registersUsed
    val composerResult =
        (caseInstructions[1] as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("Restart callback composer result is missing")
    if (
        caseInstructions[1].opcode != Opcode.MOVE_RESULT_OBJECT ||
        composerConverter.parameterTypes.map(CharSequence::toString) !=
        listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR) ||
        composerConverter.returnType != composerType ||
        composerRegisters.size != 2 ||
        !callbackInvoke.hasObjectMoveFromParameter(
            destination = composerRegisters[0],
            parameterIndex = 1,
            beforeIndex = switchIndex,
        ) ||
        !callbackInvoke.hasObjectMoveFromParameter(
            destination = composerRegisters[1],
            parameterIndex = 2,
            beforeIndex = switchIndex,
        )
    ) {
        throw PatchException("Unexpected restart callback composer conversion")
    }

    val booleanInstruction = caseInstructions[2]
    val booleanField =
        (booleanInstruction as? ReferenceInstruction)?.reference as? FieldReference
            ?: throw PatchException("Restart callback boolean field is missing")
    val booleanRegisters = booleanInstruction as? TwoRegisterInstruction
        ?: throw PatchException("Restart callback boolean registers are missing")
    if (
        booleanInstruction.opcode != Opcode.IGET_BOOLEAN ||
        booleanField.definingClass != callbackClassDescriptor ||
        booleanField.type != "Z" ||
        booleanRegisters.registerB != receiverRegister
    ) {
        throw PatchException("Unexpected restart callback boolean field access")
    }

    val callbackInstruction = caseInstructions[3]
    val callbackField =
        (callbackInstruction as? ReferenceInstruction)?.reference as? FieldReference
            ?: throw PatchException("Restart callback captured callback field is missing")
    val callbackRegisters = callbackInstruction as? TwoRegisterInstruction
        ?: throw PatchException("Restart callback captured callback registers are missing")
    val callbackCast =
        (caseInstructions[4] as? ReferenceInstruction)?.reference as? TypeReference
            ?: throw PatchException("Restart callback Function1 cast is missing")
    if (
        callbackInstruction.opcode != Opcode.IGET_OBJECT ||
        callbackField.definingClass != callbackClassDescriptor ||
        callbackField.type != OBJECT_DESCRIPTOR ||
        callbackRegisters.registerB != receiverRegister ||
        caseInstructions[4].opcode != Opcode.CHECK_CAST ||
        (caseInstructions[4] as? OneRegisterInstruction)?.registerA !=
        callbackRegisters.registerA ||
        callbackCast.type != FUNCTION1_DESCRIPTOR
    ) {
        throw PatchException("Unexpected restart callback captured callback access")
    }

    val flagsHelper =
        caseInstructions[5].staticCall()
            ?: throw PatchException("Restart callback flags helper is missing")
    val flagsResult =
        (caseInstructions[6] as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("Restart callback flags result is missing")
    if (
        flagsHelper.definingClass != callbackClassDescriptor ||
        flagsHelper.parameterTypes.map(CharSequence::toString) !=
        listOf(callbackClassDescriptor) ||
        flagsHelper.returnType != "I" ||
        caseInstructions[5].registersUsed != listOf(receiverRegister) ||
        caseInstructions[6].opcode != Opcode.MOVE_RESULT
    ) {
        throw PatchException("Unexpected restart callback flags calculation")
    }

    val targetCall =
        caseInstructions[7].staticCall()
            ?: throw PatchException("Restart callback ReduceMotionToggle call is missing")
    val targetRegisters = caseInstructions[7].registersUsed
    if (
        !targetCall.sameMethodAs(source) ||
        targetRegisters !=
        listOf(
            composerResult,
            callbackRegisters.registerA,
            flagsResult,
            booleanRegisters.registerA,
        )
    ) {
        throw PatchException(
            "Restart callback case does not call the matched ReduceMotionToggle",
        )
    }

    val caseExit = caseInstructions[8] as? BuilderOffsetInstruction
        ?: throw PatchException("Restart callback case exit is missing")
    if (
        caseInstructions[8].opcode !in
        setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
    ) {
        throw PatchException("Restart callback case does not end with a direct branch")
    }
    val returnInstructionIndex = caseExit.target.location.index
    val unitRead = callbackInvoke.instructions.getOrNull(returnInstructionIndex)
    val unitReturn = callbackInvoke.instructions.getOrNull(returnInstructionIndex + 1)
    val unitRegister = (unitRead as? OneRegisterInstruction)?.registerA
    val returnedRegister = (unitReturn as? OneRegisterInstruction)?.registerA
    val unitField = (unitRead as? ReferenceInstruction)?.reference as? FieldReference
    if (
        unitRead?.opcode != Opcode.SGET_OBJECT ||
        unitField == null ||
        !unitField.type.isReferenceDescriptor() ||
        unitReturn?.opcode != Opcode.RETURN_OBJECT ||
        unitRegister == null ||
        returnedRegister != unitRegister
    ) {
        throw PatchException("Restart callback common Unit return is missing")
    }

    val injectedRegisters =
        listOf(
            switchInstruction.registerA,
            receiverRegister,
            composerResult,
            booleanRegisters.registerA,
            callbackRegisters.registerA,
            flagsResult,
        ) + composerRegisters
    if (
        injectedRegisters.any { it !in 0..0xf }
    ) {
        throw PatchException(
            "Restart callback dispatch requires safe local 4-bit registers; " +
                "found $injectedRegisters",
        )
    }

    return NewCallbackSelector(
        selector = nextSelector,
        callbackInvoke = callbackInvoke,
        switchIndex = switchIndex,
        originalSwitch = switchInstruction,
        selectorRegister = switchInstruction.registerA,
        receiverRegister = receiverRegister,
        firstArgumentRegister = composerRegisters[0],
        secondArgumentRegister = composerRegisters[1],
        composerRegister = composerResult,
        booleanRegister = booleanRegisters.registerA,
        callbackRegister = callbackRegisters.registerA,
        flagsRegister = flagsResult,
        composerConverter = composerConverter,
        booleanField = booleanField,
        callbackField = callbackField,
        flagsHelper = flagsHelper,
        returnInstructionIndex = returnInstructionIndex,
    )
}

private fun findCallbackClassDescriptor(
    helper: MutableMethod,
    helperReference: MethodReference,
): String {
    val helperParameters = helperReference.parameterTypes.map(CharSequence::toString)
    if (
        helperReference.returnType != "V" ||
        helperParameters.size != 5 ||
        !helperParameters[0].isReferenceDescriptor() ||
        helperParameters.drop(1) != listOf(OBJECT_DESCRIPTOR, "I", "I", "Z")
    ) {
        throw PatchException("Unexpected restart callback helper signature")
    }
    val firstParameter = firstParameterRegister(helper)
    val expectedConstructorRegisters =
        listOf(
            firstParameter + 1,
            firstParameter + 2,
            firstParameter + 3,
            firstParameter + 4,
        )
    val newInstances =
        helper.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
            val descriptor =
                ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type
                    ?: return@mapNotNull null
            val register =
                (instruction as? OneRegisterInstruction)?.registerA
                    ?: return@mapNotNull null
            descriptor to register
        }
    val candidates =
        newInstances.filter { (descriptor, instanceRegister) ->
            helper.instructions.count { instruction ->
                if (
                    instruction.opcode !in
                    setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE)
                ) {
                    return@count false
                }
                val constructor =
                    (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@count false
                constructor.definingClass == descriptor &&
                    constructor.name == "<init>" &&
                    constructor.parameterTypes.map(CharSequence::toString) ==
                    listOf(OBJECT_DESCRIPTOR, "I", "I", "Z") &&
                    constructor.returnType == "V" &&
                    instruction.registersUsed ==
                    listOf(instanceRegister) + expectedConstructorRegisters
            } == 1
        }
    if (candidates.size != 1) {
        throw PatchException(
            "Expected exactly one restart callback allocation, found ${candidates.size}",
        )
    }

    val (callbackDescriptor, instanceRegister) = candidates.single()
    val storedCallbacks =
        helper.instructions.filter { instruction ->
            if (instruction.opcode != Opcode.IPUT_OBJECT) return@filter false
            val field =
                (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    ?: return@filter false
            field.definingClass == helperParameters[0] &&
                field.type.isReferenceDescriptor() &&
                instruction.registersUsed ==
                listOf(instanceRegister, firstParameter)
        }
    if (storedCallbacks.size != 1) {
        throw PatchException(
            "Expected exactly one restart callback scope assignment, " +
                "found ${storedCallbacks.size}",
        )
    }
    return callbackDescriptor
}

private fun findComparedSelectorValues(
    methods: Iterable<MutableMethod>,
    selectorField: FieldReference,
): Set<Int> =
    buildSet {
        methods.filter { it.name == "<init>" }.forEach { constructor ->
            val selectorSources =
                constructor.instructions.mapNotNull { instruction ->
                    if (instruction.opcode != Opcode.IPUT) return@mapNotNull null
                    val field =
                        (instruction as? ReferenceInstruction)?.reference as? FieldReference
                            ?: return@mapNotNull null
                    if (field.toString() != selectorField.toString()) {
                        return@mapNotNull null
                    }
                    (instruction as? TwoRegisterInstruction)?.registerA
                }.toSet()
            if (selectorSources.isEmpty()) return@forEach

            constructor.instructions.zipWithNext().forEach { (literal, branch) ->
                val literalRegister =
                    (literal as? OneRegisterInstruction)?.registerA
                        ?: return@forEach
                val literalValue =
                    (literal as? NarrowLiteralInstruction)?.narrowLiteral
                        ?: return@forEach
                val branchRegisters = branch.registersUsed
                if (
                    branch.opcode in setOf(Opcode.IF_EQ, Opcode.IF_NE) &&
                    literalRegister in branchRegisters &&
                    selectorSources.any { it in branchRegisters }
                ) {
                    add(literalValue)
                }
            }
        }
    }

private fun MutableMethod.hasObjectMoveFromParameter(
    destination: Int,
    parameterIndex: Int,
    beforeIndex: Int,
): Boolean {
    val parameterRegister = firstParameterRegister(this) + parameterIndex
    val matches =
        instructions.take(beforeIndex).filter { instruction ->
            instruction.opcode in
                setOf(
                    Opcode.MOVE_OBJECT,
                    Opcode.MOVE_OBJECT_FROM16,
                    Opcode.MOVE_OBJECT_16,
                ) &&
                (instruction as? TwoRegisterInstruction)?.let {
                    it.registerA == destination && it.registerB == parameterRegister
                } == true
        }
    return matches.size == 1
}

private fun MethodReference.sameMethodAs(other: MethodReference): Boolean =
    definingClass == other.definingClass &&
        name == other.name &&
        parameterTypes.map(CharSequence::toString) ==
        other.parameterTypes.map(CharSequence::toString) &&
        returnType == other.returnType

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.staticCall():
    MethodReference? {
    if (opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) {
        return null
    }
    return (this as? ReferenceInstruction)?.reference as? MethodReference
}

private fun String.isReferenceDescriptor(): Boolean =
    (length >= 3 && startsWith("L") && endsWith(";")) || startsWith("[")
