/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.shared.declaredParameterRegister
import app.crimera.patches.shared.parameterRegisterStart
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal fun addNormalStorySeenObservationHook(method: MutableMethod) {
    val instructions = method.instructions
    val reelsIndex = instructions.indices.singleOrNull {
        instructions[it].getReference<StringReference>()?.string == "reels"
    } ?: throw PatchException("Expected one reels payload argument")
    val argument = instructions.getOrNull(reelsIndex + 1)
        ?: throw PatchException("Missing reels payload setter")
    val setter = argument.getReference<MethodReference>()
    val registers = argument.registersUsed
    if (
        argument.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) ||
        setter?.parameterTypes != listOf(STRING_CLASS, STRING_CLASS) ||
        setter.returnType != "V" || registers.size != 3 ||
        instructions[reelsIndex].registersUsed.singleOrNull() != registers[1] ||
        method.parameterTypes != listOf(USER_SESSION_CLASS)
    ) throw PatchException("Unexpected normal story request payload structure")

    val payloadRegister = registers.last()
    val sessionRegister = declaredParameterRegister(method, 0)
    val returnIndex = instructions.indices.singleOrNull {
        instructions[it].opcode == Opcode.RETURN_OBJECT
    } ?: throw PatchException("Expected one normal story request return")
    val requestRegister = instructions[returnIndex].registersUsed.single()
    val hookRegisters = listOf(requestRegister, sessionRegister, payloadRegister)
    if (
        returnIndex <= reelsIndex + 1 || hookRegisters.distinct().size != 3 ||
        hookRegisters.any { it !in 0..15 }
    ) throw PatchException("Unsupported normal story request observation registers")

    fun writesRegister(instruction: Instruction, register: Int): Boolean {
        if (!instruction.opcode.setsRegister()) return false
        val destination = instruction.registersUsed.firstOrNull() ?: return false
        return destination == register ||
            (instruction.opcode.setsWideRegister() && destination + 1 == register)
    }
    if (
        instructions.take(returnIndex).any { writesRegister(it, sessionRegister) } ||
        instructions.subList(reelsIndex + 2, returnIndex).any { writesRegister(it, payloadRegister) }
    ) throw PatchException("Normal story request observation arguments were overwritten")

    method.replaceInstruction(
        returnIndex,
        "invoke-static {v$requestRegister, v$sessionRegister, v$payloadRegister}, " +
            "$STORY_SEEN_BRIDGE_DESCRIPTOR->observeRequest(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)V",
    )
    method.addInstruction(returnIndex + 1, "return-object v$requestRegister")
}

internal data class StoryPredicateReferences(
    val excludedItem: MethodReference,
    val regularStory: MethodReference,
    val excludedReel: MethodReference,
)

internal data class AggregateInvocation(
    val index: Int,
    val reference: MethodReference,
    val ownerRegister: Int,
)

internal data class LocalStorySeenReferences(
    val reelModelField: FieldReference,
    val itemSeenTimestampMethod: MethodReference,
    val markLocalSeenMethod: MethodReference,
)

internal data class RequestScheduleReferences(
    val managerGetter: MethodReference,
    val scheduleRequest: MethodReference,
)

internal data class ExecutorRunHook(
    val requestLoadIndex: Int,
    val delegatedRunIndex: Int,
    val requestRegister: Int,
    val exceptionHandlerIndexes: List<Int>,
)

private val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private fun objectParameterAliasesBeforeEachInstruction(
    instructions: List<Instruction>,
    parameterRegister: Int,
): List<Set<Int>> {
    val aliases = mutableSetOf(parameterRegister)
    // Instagram copies high parameter registers into low locals. Only object moves preserve
    // identity; every other register write invalidates the alias, including both halves of a wide write.
    return buildList(instructions.size) {
        instructions.forEach { instruction ->
            add(aliases.toSet())
            val registers = instruction.registersUsed
            if (instruction.opcode in OBJECT_MOVE_OPCODES && registers.size == 2) {
                val destination = registers[0]
                val source = registers[1]
                if (source in aliases) {
                    aliases.add(destination)
                } else {
                    aliases.remove(destination)
                }
            } else if (instruction.opcode.setsRegister() && registers.isNotEmpty()) {
                aliases.remove(registers[0])
                if (instruction.opcode.setsWideRegister()) {
                    aliases.remove(registers[0] + 1)
                }
            }
        }
    }
}

internal fun deriveStoryPredicateReferences(
    method: MutableMethod,
    reelModelClass: String,
    endIndex: Int,
): StoryPredicateReferences {
    val instructions = method.instructions.take(endIndex)
    val invocations =
        instructions.mapIndexedNotNull { index, instruction ->
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            if (
                instruction.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) ||
                instruction.registersUsed.size != 1 ||
                reference.parameterTypes.isNotEmpty() ||
                reference.returnType != "Z"
            ) {
                return@mapIndexedNotNull null
            }
            IndexedValue(index, reference)
        }

    fun booleanBranchMatches(
        entry: IndexedValue<MethodReference>,
        inverted: Boolean,
        branchOpcode: Opcode,
    ): Boolean {
        val resultIndex = entry.index + 1
        val xorIndex = resultIndex + 1
        val branchIndex = xorIndex + if (inverted) 1 else 0
        if (branchIndex >= instructions.size) return false
        val result = instructions[resultIndex]
        if (result.opcode != Opcode.MOVE_RESULT || result.registersUsed.size != 1) return false
        val resultRegister = result.registersUsed.single()
        if (inverted) {
            val xor = instructions[xorIndex]
            if (
                xor.opcode != Opcode.XOR_INT_LIT8 ||
                xor.registersUsed.size != 2 ||
                xor.registersUsed.any { it != resultRegister }
            ) return false
        }
        val branch = instructions[branchIndex]
        return branch.opcode == branchOpcode &&
            branch.registersUsed.size == 1 &&
            branch.registersUsed.single() == resultRegister
    }

    fun uniquePredicate(
        definingClass: String,
        description: String,
        inverted: Boolean,
        branchOpcode: Opcode,
    ): MethodReference {
        val matches =
            invocations.filter { entry ->
                entry.value.definingClass == definingClass &&
                    booleanBranchMatches(entry, inverted, branchOpcode)
            }
        return matches.singleOrNull()?.value
            ?: throw PatchException("Expected one $description, found ${matches.size}")
    }

    return StoryPredicateReferences(
        excludedItem =
            uniquePredicate(
                REEL_ITEM_CLASS,
                "inverted excluded-item predicate",
                inverted = true,
                branchOpcode = Opcode.IF_EQZ,
            ),
        regularStory =
            uniquePredicate(
                REEL_ITEM_CLASS,
                "regular-story predicate",
                inverted = false,
                branchOpcode = Opcode.IF_EQZ,
            ),
        excludedReel =
            uniquePredicate(
                reelModelClass,
                "excluded-reel predicate",
                inverted = false,
                branchOpcode = Opcode.IF_NEZ,
            ),
    )
}

internal fun deriveAggregateInvocation(
    method: MutableMethod,
    startIndex: Int,
    pendingSeenClass: String,
    mediaRequestType: String,
): AggregateInvocation {
    val matches =
        method.instructions.drop(startIndex).mapIndexedNotNull { offset, instruction ->
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            val parameterTypes = reference.parameterTypes.map(CharSequence::toString)
            if (
                instruction.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) ||
                instruction.registersUsed.size != 4 ||
                reference.definingClass != pendingSeenClass ||
                reference.returnType != "V" ||
                parameterTypes !=
                listOf(mediaRequestType, USER_SESSION_CLASS, pendingSeenClass, STRING_CLASS)
            ) {
                return@mapIndexedNotNull null
            }
            AggregateInvocation(
                index = startIndex + offset,
                reference = reference,
                ownerRegister = instruction.registersUsed.last(),
            )
        }
    return matches.singleOrNull()
        ?: throw PatchException("Expected one exact pending-story aggregation call, found ${matches.size}")
}

internal fun deriveStoryMediaField(
    method: MutableMethod,
    mediaConversionIndex: Int,
): FieldReference {
    val conversionInstruction = method.instructions.getOrNull(mediaConversionIndex)
        ?: throw PatchException("Story media conversion index is unavailable")
    val conversionRegisters = conversionInstruction.registersUsed
    if (
        conversionInstruction.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) ||
        conversionRegisters.size != 3
    ) {
        throw PatchException("Story media conversion requires a virtual receiver and two arguments")
    }
    val mediaRegister = conversionRegisters.first()
    val matches =
        method.instructions.take(mediaConversionIndex).mapNotNull { instruction ->
            val reference = instruction.getReference<FieldReference>() ?: return@mapNotNull null
            val registers = instruction.registersUsed
            if (
                instruction.opcode == Opcode.IGET_OBJECT &&
                registers.size == 2 &&
                registers[0] == mediaRegister &&
                reference.definingClass == REEL_ITEM_CLASS &&
                reference.type == MEDIA_CLASS
            ) reference else null
        }.distinctBy { listOf(it.definingClass, it.name, it.type) }
    return matches.singleOrNull()
        ?: throw PatchException("Expected one data-flow matched ReelItem media field, found ${matches.size}")
}

internal fun deriveLocalStorySeenReferences(
    method: MutableMethod,
    reelParentClass: String,
    endIndex: Int,
): LocalStorySeenReferences {
    if (
        AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.size < 2 ||
        method.parameterTypes[0].toString() != REEL_ITEM_CLASS ||
        method.parameterTypes[1].toString() != reelParentClass
    ) {
        throw PatchException("Story consumption requires selected-item and reel parameters")
    }
    val instructions = method.instructions.take(endIndex)
    val itemRegister = declaredParameterRegister(method, 0)
    val reelRegister = declaredParameterRegister(method, 1)
    val itemAliases = objectParameterAliasesBeforeEachInstruction(instructions, itemRegister)
    val reelAliases = objectParameterAliasesBeforeEachInstruction(instructions, reelRegister)
    val matches =
        instructions.mapIndexedNotNull { markIndex, markInstruction ->
            val markReference =
                markInstruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            val markRegisters = markInstruction.registersUsed
            if (
                markInstruction.opcode !in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) ||
                markReference.parameterTypes.map(CharSequence::toString) !=
                listOf(USER_SESSION_CLASS, "J") ||
                markReference.returnType != "V" ||
                markRegisters.size != 4 ||
                markRegisters[3] != markRegisters[2] + 1
            ) {
                return@mapIndexedNotNull null
            }

            val reelModelFields =
                instructions.take(markIndex).mapIndexedNotNull { fieldIndex, instruction ->
                    val field =
                        instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
                    val registers = instruction.registersUsed
                    if (
                        instruction.opcode == Opcode.IGET_OBJECT &&
                        registers.size == 2 &&
                        registers[0] == markRegisters[0] &&
                        registers[1] in reelAliases[fieldIndex] &&
                        field.definingClass == reelParentClass &&
                        field.type == markReference.definingClass
                    ) field else null
                }.distinctBy { listOf(it.definingClass, it.name, it.type) }
            val reelModelField = reelModelFields.singleOrNull() ?: return@mapIndexedNotNull null

            val timestampMethods =
                instructions.take(markIndex - 1).mapIndexedNotNull { timestampIndex, instruction ->
                    val reference =
                        instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
                    val registers = instruction.registersUsed
                    val result = instructions.getOrNull(timestampIndex + 1)
                    if (
                        instruction.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                        registers.size == 1 &&
                        registers.single() in itemAliases[timestampIndex] &&
                        reference.definingClass == REEL_ITEM_CLASS &&
                        reference.parameterTypes.isEmpty() &&
                        reference.returnType == "J" &&
                        result?.opcode == Opcode.MOVE_RESULT_WIDE &&
                        result.registersUsed.size == 1 &&
                        result.registersUsed.single() == markRegisters[2]
                    ) reference else null
                }.distinctBy {
                    listOf(it.definingClass, it.name, it.parameterTypes, it.returnType)
                }
            val timestampMethod = timestampMethods.singleOrNull() ?: return@mapIndexedNotNull null

            LocalStorySeenReferences(reelModelField, timestampMethod, markReference)
        }.distinctBy { references ->
            listOf(
                references.reelModelField.toString(),
                references.itemSeenTimestampMethod.toString(),
                references.markLocalSeenMethod.toString(),
            )
        }
    return matches.singleOrNull()
        ?: throw PatchException("Expected one selected-story local seen data flow, found ${matches.size}")
}

internal fun deriveRequestScheduleReferences(
    method: MutableMethod,
    pendingSeenClass: String,
    requestBuilder: MethodReference,
): RequestScheduleReferences {
    if (method.parameterTypes.count { it.toString() == pendingSeenClass } != 1) {
        throw PatchException("Request scheduling requires one pending-story parameter")
    }
    val pendingParameterIndex =
        method.parameterTypes.indexOfFirst { it.toString() == pendingSeenClass }
    val pendingRegister = declaredParameterRegister(method, pendingParameterIndex)
    val instructions = method.instructions
    data class BuilderEntry(
        val resultIndex: Int,
        val sessionRegister: Int,
        val requestRegister: Int,
    )
    data class ManagerEntry(
        val resultIndex: Int,
        val sessionRegister: Int,
        val managerRegister: Int,
        val reference: MethodReference,
    )
    val builderEntries =
        instructions.mapIndexedNotNull { builderIndex, builderInstruction ->
            val builderReference =
                builderInstruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            val builderRegisters = builderInstruction.registersUsed
            val requestResult = instructions.getOrNull(builderIndex + 1)
            if (
                builderInstruction.opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                builderReference.matches(requestBuilder) &&
                builderRegisters.size == 2 &&
                builderRegisters[0] == pendingRegister &&
                requestResult?.opcode == Opcode.MOVE_RESULT_OBJECT &&
                requestResult.registersUsed.size == 1
            ) {
                BuilderEntry(
                    builderIndex + 1,
                    builderRegisters[1],
                    requestResult.registersUsed.single(),
                )
            } else {
                null
            }
        }
    val managerEntries =
        instructions.mapIndexedNotNull { managerIndex, managerInstruction ->
            val managerReference =
                managerInstruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            val managerRegisters = managerInstruction.registersUsed
            val managerResult = instructions.getOrNull(managerIndex + 1)
            if (
                managerInstruction.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
                managerReference.parameterTypes.map(CharSequence::toString) ==
                listOf(USER_SESSION_CLASS) &&
                managerReference.returnType.startsWith("L") &&
                managerRegisters.size == 1 &&
                managerResult?.opcode == Opcode.MOVE_RESULT_OBJECT &&
                managerResult.registersUsed.size == 1
            ) {
                ManagerEntry(
                    managerIndex + 1,
                    managerRegisters.single(),
                    managerResult.registersUsed.single(),
                    managerReference,
                )
            } else {
                null
            }
        }
    val matches =
        builderEntries.flatMap { builderEntry ->
            managerEntries.filter { managerEntry ->
                managerEntry.sessionRegister == builderEntry.sessionRegister
            }.flatMap { managerEntry ->
                val scheduleStart = maxOf(builderEntry.resultIndex, managerEntry.resultIndex) + 1
                instructions.drop(scheduleStart).mapNotNull { scheduleInstruction ->
                    val scheduleReference =
                        scheduleInstruction.getReference<MethodReference>() ?: return@mapNotNull null
                    if (
                        scheduleInstruction.opcode in
                        setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE) &&
                        scheduleReference.parameterTypes.map(CharSequence::toString) ==
                        listOf(requestBuilder.returnType) &&
                        scheduleReference.returnType == "V" &&
                        scheduleInstruction.registersUsed ==
                        listOf(managerEntry.managerRegister, builderEntry.requestRegister)
                    ) {
                        RequestScheduleReferences(managerEntry.reference, scheduleReference)
                    } else {
                        null
                    }
                }
            }
        }.distinctBy { references ->
            listOf(references.managerGetter.toString(), references.scheduleRequest.toString())
        }
    return matches.singleOrNull()
        ?: throw PatchException("Expected one request scheduling data flow, found ${matches.size}")
}

internal fun deriveReelOwnerIdField(
    method: MutableMethod,
    aggregateIndex: Int,
    reelModelClass: String,
    ownerRegister: Int,
): FieldReference {
    val matches =
        method.instructions.take(aggregateIndex).mapNotNull { instruction ->
            val reference = instruction.getReference<FieldReference>() ?: return@mapNotNull null
            val registers = instruction.registersUsed
            if (
                instruction.opcode == Opcode.IGET_OBJECT &&
                registers.size == 2 &&
                registers[0] == ownerRegister &&
                reference.definingClass == reelModelClass &&
                reference.type == STRING_CLASS
            ) reference else null
        }.distinctBy { listOf(it.definingClass, it.name, it.type) }
    return matches.singleOrNull()
        ?: throw PatchException("Expected one data-flow matched reel-owner id field, found ${matches.size}")
}

internal fun deriveExecutorRunHook(
    method: MutableMethod,
    wrappedRequestField: FieldReference,
    requestInterface: String,
): ExecutorRunHook {
    if (
        AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.isNotEmpty() ||
        method.returnType != "V"
    ) {
        throw PatchException("Executor wrapper requires an instance run() method")
    }
    val receiverRegister = parameterRegisterStart(method)
    val requestLoads =
        method.instructions.mapIndexedNotNull { index, instruction ->
            val reference = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
            val registers = instruction.registersUsed
            if (
                instruction.opcode == Opcode.IGET_OBJECT &&
                reference.matches(wrappedRequestField) &&
                registers.size == 2 &&
                registers[1] == receiverRegister
            ) IndexedValue(index, registers[0]) else null
        }
    val requestLoad =
        requestLoads.singleOrNull()
            ?: throw PatchException("Expected one exact wrapped executor request load, found ${requestLoads.size}")
    val delegatedRuns =
        method.instructions.mapIndexedNotNull { index, instruction ->
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            val registers = instruction.registersUsed
            if (
                index > requestLoad.index &&
                instruction.opcode in setOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE) &&
                reference.definingClass == requestInterface &&
                reference.name == "run" &&
                reference.parameterTypes.isEmpty() &&
                reference.returnType == "V" &&
                registers.size == 1 &&
                registers.single() == requestLoad.value
            ) IndexedValue(index, registers.single()) else null
        }
    val delegatedRun =
        delegatedRuns.singleOrNull()
            ?: throw PatchException("Expected one exact delegated executor request run, found ${delegatedRuns.size}")
    val delegatedRunAddress = method.instructions[delegatedRun.index].location.codeAddress
    val coveringTryBlocks =
        method.implementation!!.tryBlocks.filter { tryBlock ->
            delegatedRunAddress >= tryBlock.startCodeAddress &&
                delegatedRunAddress < tryBlock.startCodeAddress + tryBlock.codeUnitCount
        }
    val coveringHandlers =
        coveringTryBlocks.flatMap { it.exceptionHandlers }.distinctBy { it.handlerCodeAddress }
    if (coveringHandlers.none { handler ->
            handler.exceptionType == null || handler.exceptionType == "Ljava/lang/Throwable;"
        }
    ) {
        throw PatchException("Delegated executor request run is not covered by a catch-all handler")
    }
    val exceptionHandlerIndexes =
        coveringHandlers.map { handler ->
            val handlerIndex =
                method.instructions.indexOfFirst { instruction ->
                    instruction.location.codeAddress == handler.handlerCodeAddress
                }
            if (handlerIndex < 0) {
                throw PatchException("Executor exception handler address is unavailable")
            }
            val moveException = method.instructions[handlerIndex]
            if (
                moveException.opcode != Opcode.MOVE_EXCEPTION ||
                moveException.registersUsed.size != 1
            ) {
                throw PatchException("Executor exception handler must start with move-exception")
            }
            handlerIndex
        }.distinct()
    return ExecutorRunHook(
        requestLoadIndex = requestLoad.index,
        delegatedRunIndex = delegatedRun.index,
        requestRegister = delegatedRun.value,
        exceptionHandlerIndexes = exceptionHandlerIndexes,
    )
}

internal fun addStoryProgressCaptureHook(
    method: MutableMethod,
    bridgeMethodName: String,
) {
    if (
        AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.size != 2 ||
        method.parameterTypes.last() != "I"
    ) {
        throw PatchException("Story-progress capture requires an instance callback with an integer event")
    }

    val receiverRegister = parameterRegisterStart(method)
    method.addInstruction(
        0,
        "invoke-static/range {v$receiverRegister .. v$receiverRegister}, " +
            "$STORY_SEEN_BRIDGE_DESCRIPTOR->$bridgeMethodName(Ljava/lang/Object;)V",
    )
}

internal fun addStoryProgressBridgeHook(
    method: MutableMethod,
    controllerClass: String,
    sessionField: FieldReference,
    itemField: FieldReference,
    reelField: FieldReference,
    reelModelField: FieldReference,
    reelTypeField: FieldReference,
    rootViewField: FieldReference,
) {
    if (
        !AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.size != 5 ||
        method.parameterTypes.any { it != "Ljava/lang/Object;" }
    ) {
        throw PatchException("Story-progress bridge requires five object parameters")
    }

    method.addInstructions(
        0,
        """
        check-cast p0, $controllerClass
        iget-object p1, p0, $sessionField
        iget-object p2, p0, $itemField
        if-eqz p2, :piko_story_progress_unavailable
        iget-object p3, p0, $reelField
        if-eqz p3, :piko_story_progress_unavailable
        iget-object p4, p3, $reelModelField
        if-eqz p4, :piko_story_progress_unavailable
        iget-object p4, p4, $reelTypeField
        iget-object p0, p0, $rootViewField
        if-eqz p0, :piko_story_progress_unavailable
        invoke-static {p1, p2, p3, p4, p0}, $STORY_SEEN_BUTTON_DESCRIPTOR->captureCurrentStoryFromProgress(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
        return-void
        :piko_story_progress_unavailable
        invoke-static {}, $STORY_SEEN_BUTTON_DESCRIPTOR->invalidateCurrentStory()V
        return-void
        """.trimIndent(),
    )
}

internal fun Method.isStoryHeaderBindingMethod(reelParentClass: String): Boolean {
    val parameterDescriptors = parameterTypes.map(CharSequence::toString)
    return AccessFlags.STATIC.isSet(accessFlags) &&
        returnType == "V" &&
        parameterDescriptors.lastOrNull() == "Z" &&
        parameterDescriptors.count { it == USER_SESSION_CLASS } == 1 &&
        parameterDescriptors.count { it == REEL_ITEM_CLASS } == 1 &&
        parameterDescriptors.count { it == reelParentClass } == 1 &&
        hasString(STORY_HEADER_BIND_TRACE)
}

internal fun addStoryHeaderCaptureHook(
    method: MutableMethod,
    sessionParameterIndex: Int,
    itemParameterIndex: Int,
    reelParameterIndex: Int,
    holderParameterIndex: Int,
) {
    if (!AccessFlags.STATIC.isSet(method.accessFlags) || method.returnType != "V") {
        throw PatchException("Story-header capture requires a static void binder")
    }
    if (parameterRegisterStart(method) < 4) {
        throw PatchException("Story-header capture requires four local registers")
    }

    val sourceRegisters =
        listOf(
            sessionParameterIndex,
            itemParameterIndex,
            reelParameterIndex,
            holderParameterIndex,
        ).map { parameterIndex -> declaredParameterRegister(method, parameterIndex) }
    val captureInstructions =
        """
        move-object/from16 v0, v${sourceRegisters[0]}
        move-object/from16 v1, v${sourceRegisters[1]}
        move-object/from16 v2, v${sourceRegisters[2]}
        move-object/from16 v3, v${sourceRegisters[3]}
        invoke-static/range {v0 .. v3}, $STORY_SEEN_BRIDGE_DESCRIPTOR->captureFromHeaderBind(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
        """.trimIndent()
    method.addInstructions(0, captureInstructions)

    val returnIndexes =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode == Opcode.RETURN_VOID) index else null
        }
    if (returnIndexes.isEmpty()) {
        throw PatchException("Story-header binder has no return-void instruction")
    }
    returnIndexes.asReversed().forEach { returnIndex ->
        method.addInstructions(returnIndex, captureInstructions)
    }
}

internal fun addStoryHeaderBridgeHook(
    method: MutableMethod,
    holderClass: String,
    rootViewField: FieldReference,
    reelModelField: FieldReference,
    reelTypeField: FieldReference,
) {
    if (
        !AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.size != 6 ||
        method.parameterTypes.any { it != "Ljava/lang/Object;" }
    ) {
        throw PatchException("Story-header bridge requires six object parameters")
    }

    method.addInstructions(
        0,
        """
        check-cast p2, ${reelModelField.definingClass}
        check-cast p3, $holderClass
        if-eqz p1, :piko_story_header_unavailable
        if-eqz p2, :piko_story_header_unavailable
        if-eqz p3, :piko_story_header_unavailable
        iget-object p4, p3, $rootViewField
        if-eqz p4, :piko_story_header_unavailable
        iget-object p5, p2, $reelModelField
        if-eqz p5, :piko_story_header_unavailable
        iget-object p5, p5, $reelTypeField
        move-object p3, p5
        invoke-static/range {p0 .. p4}, $STORY_SEEN_BUTTON_DESCRIPTOR->captureCurrentStoryFromHeader(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
        return-void
        :piko_story_header_unavailable
        invoke-static {}, $STORY_SEEN_BUTTON_DESCRIPTOR->invalidateCurrentStory()V
        return-void
        """.trimIndent(),
    )
}

internal fun deriveConstructorBoundRootViewField(
    holderClass: String,
    methods: Iterable<MutableMethod>,
): FieldReference? {
    val candidates =
        methods.filter { method -> method.name == "<init>" }.flatMap { constructor ->
            val viewParameterIndex =
                constructor.parameterTypes.mapIndexedNotNull { index, type ->
                    index.takeIf { type.toString() == VIEW_CLASS }
                }.singleOrNull() ?: return@flatMap emptyList()
            val instructions = constructor.instructions
            val receiverAliases =
                objectParameterAliasesBeforeEachInstruction(
                    instructions,
                    parameterRegisterStart(constructor),
                )
            val viewAliases =
                objectParameterAliasesBeforeEachInstruction(
                    instructions,
                    declaredParameterRegister(constructor, viewParameterIndex),
                )
            instructions.mapIndexedNotNull { index, instruction ->
                val registers = instruction.registersUsed
                val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
                if (
                    instruction.opcode == Opcode.IPUT_OBJECT &&
                    field.definingClass == holderClass &&
                    field.type == VIEW_CLASS &&
                    registers.size == 2 &&
                    registers[0] in viewAliases[index] &&
                    registers[1] in receiverAliases[index]
                ) field else null
            }
        }.distinctBy { listOf(it.definingClass, it.name, it.type) }
    return candidates.singleOrNull()
}

internal fun deriveHierarchyConstructorBoundRootViewField(
    classHierarchy: Iterable<Pair<String, Iterable<MutableMethod>>>,
): FieldReference? =
    classHierarchy.firstNotNullOfOrNull { (classDescriptor, methods) ->
        deriveConstructorBoundRootViewField(classDescriptor, methods)
    }
