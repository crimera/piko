package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val CONCURRENT_HASH_MAP_DESCRIPTOR = "Ljava/util/concurrent/ConcurrentHashMap;"
private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val TIMELINE_POSITION_STORE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/TimelineScrollPositionStore;"
private const val RESTORE_TEMPORARY_REGISTER_COUNT = 2
private const val FALLBACK_RESTORE_TEMPORARY_REGISTER_COUNT = 3

private object NewXScrollPositionHolderFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/",
    name = "toString",
    parameters = emptyList(),
    returnType = STRING_DESCRIPTOR,
    strings =
        listOf(
            "ScrollPositionHolder(firstVisibleItemIndex=",
            ", firstVisibleItemScrollOffset=",
        ),
)

private fun scrollPositionGetterFingerprint(holderDescriptor: String) =
    Fingerprint(
        definingClass = "Lcom/x/urt/",
        parameters = emptyList(),
        returnType = holderDescriptor,
        filters =
            listOf(
                fieldAccess(
                    opcode = Opcode.IGET_OBJECT,
                    type = CONCURRENT_HASH_MAP_DESCRIPTOR,
                ),
                methodCall(
                    opcode = Opcode.INVOKE_VIRTUAL,
                    definingClass = CONCURRENT_HASH_MAP_DESCRIPTOR,
                    name = "get",
                    parameters = listOf("Ljava/lang/Object;"),
                    returnType = "Ljava/lang/Object;",
                ),
                opcode(Opcode.CHECK_CAST),
                string("Restoring scrolling position for "),
                opcode(Opcode.RETURN_OBJECT),
            ),
    )

private fun saveScrollPositionFingerprint(
    componentDescriptor: String,
    holderDescriptor: String,
) =
    Fingerprint(
        definingClass = componentDescriptor,
        parameters = listOf("L"),
        returnType = "V",
        filters =
            listOf(
                fieldAccess(
                    opcode = Opcode.IGET_OBJECT,
                    type = holderDescriptor,
                ),
                string("Saving scrolling positions for "),
                methodCall(
                    opcode = Opcode.INVOKE_VIRTUAL,
                    definingClass = CONCURRENT_HASH_MAP_DESCRIPTOR,
                    name = "put",
                    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
                    returnType = "Ljava/lang/Object;",
                ),
            ),
    )

@Suppress("unused")
val restoreTimelinePositionPatch =
    bytecodePatch(
        name = "NewX: Restore timeline position",
        description = "Persists the timeline index and offset, then restores them after the app process restarts.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val restoreTimelinePosition =
            newXToggle(
                id = "newx.timeline.restore_position",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_restore_timeline_position"),
                order = 150,
                defaultValue = true,
                rebootApp = true,
            )

        execute {
            val holderMatches = NewXScrollPositionHolderFingerprint.scopedMatchAll()
            if (holderMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX scroll-position holder, found ${holderMatches.size}: " +
                        holderMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val holderDescriptor = holderMatches.single().originalClassDef.type
            val holderConstructorMatches =
                mutableClassDefBy(holderDescriptor).methods.filter { method ->
                    method.name == "<init>" &&
                        method.parameterTypes.map(CharSequence::toString) == listOf("I", "I") &&
                        method.returnType == "V"
                }
            if (holderConstructorMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX scroll-position holder constructor, found " +
                        "${holderConstructorMatches.size}: ${holderConstructorMatches.joinToString()}",
                )
            }
            val holderConstructorReference = "$holderDescriptor-><init>(II)V"

            val getterMatches = scrollPositionGetterFingerprint(holderDescriptor).scopedMatchAll()
            if (getterMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX scroll-position getter, found ${getterMatches.size}: " +
                        getterMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val getterMatch = getterMatches.single()
            var getterMethod = getterMatch.method
            if (getterMethod.implementation == null) {
                throw PatchException("NewX scroll-position getter has no implementation")
            }
            val expandedMethod =
                getterMethod.cloneMutable(
                    additionalRegisters =
                        RESTORE_TEMPORARY_REGISTER_COUNT +
                            FALLBACK_RESTORE_TEMPORARY_REGISTER_COUNT +
                            getterMethod.numberOfParameterRegisters,
                )
            getterMatch.classDef.methods.remove(getterMethod)
            getterMatch.classDef.methods.add(expandedMethod)
            getterMethod = expandedMethod

            val mapGetCandidates =
                getterMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.INVOKE_VIRTUAL) return@filter false
                    val reference = indexedInstruction.value.getReference<MethodReference>() ?: return@filter false
                    reference.definingClass.toString() == CONCURRENT_HASH_MAP_DESCRIPTOR &&
                        reference.name == "get" &&
                        reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
                        reference.returnType.toString() == "Ljava/lang/Object;"
                }
            if (mapGetCandidates.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline-position map read, found " +
                        "${mapGetCandidates.size}: ${mapGetCandidates.joinToString()}",
                )
            }
            val mapGetIndex = mapGetCandidates.single().index
            val mapGetInstruction =
                mapGetCandidates.single().value as? FiveRegisterInstruction
                    ?: throw PatchException("NewX timeline-position map read has an unsupported register layout")
            if (mapGetInstruction.registerCount != 2) {
                throw PatchException(
                    "Unexpected NewX timeline-position map read register count: " +
                        mapGetInstruction.registerCount,
                )
            }
            val timelineRegister = mapGetInstruction.registerD
            if (timelineRegister !in 0..15) {
                throw PatchException("NewX timeline-type register is not encodable in invoke-virtual: v$timelineRegister")
            }
            val timelineResultCandidates =
                getterMethod.instructions.withIndex().mapNotNull { indexedInstruction ->
                    if (indexedInstruction.index >= mapGetIndex ||
                        indexedInstruction.value.opcode != Opcode.MOVE_RESULT_OBJECT
                    ) {
                        return@mapNotNull null
                    }
                    val resultRegister =
                        (indexedInstruction.value as? OneRegisterInstruction)?.registerA
                            ?: return@mapNotNull null
                    if (resultRegister != timelineRegister) return@mapNotNull null
                    val methodReference =
                        getterMethod.instructions
                            .getOrNull(indexedInstruction.index - 1)
                            ?.getReference<MethodReference>()
                            ?: return@mapNotNull null
                    val returnType = methodReference.returnType.toString()
                    if (!returnType.startsWith("L")) return@mapNotNull null
                    val isEnum =
                        runCatching { mutableClassDefBy(returnType).superclass == ENUM_DESCRIPTOR }
                            .getOrDefault(false)
                    if (!isEnum) return@mapNotNull null
                    indexedInstruction.index to methodReference
                }
            if (timelineResultCandidates.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline-type result feeding the map read, found " +
                        "${timelineResultCandidates.size}: ${timelineResultCandidates.joinToString()}",
                )
            }
            val timelineResultIndex = timelineResultCandidates.single().first

            val mapField =
                getterMethod.instructions.mapNotNull { instruction ->
                    if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                    val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                    field.takeIf { it.type.toString() == CONCURRENT_HASH_MAP_DESCRIPTOR }
                }.singleOrNull()
                    ?: throw PatchException("NewX timeline-position map field was not found uniquely")
            val componentDescriptor = getterMatch.originalMethod.definingClass
            val componentField =
                getterMethod.instructions.mapNotNull { instruction ->
                    if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                    val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                    field.takeIf {
                        it.definingClass.toString() == componentDescriptor &&
                            it.type.toString() == mapField.definingClass.toString()
                    }
                }.singleOrNull()
                    ?: throw PatchException("NewX timeline-position map owner field was not found uniquely")
            if (mapField.definingClass.toString() == componentDescriptor) {
                throw PatchException("NewX timeline-position map unexpectedly belongs to the component")
            }
            if (!AccessFlags.PUBLIC.isSet(
                    mutableClassDefBy(mapField.definingClass.toString()).fields.single { field ->
                        field.toString() == mapField.toString()
                    }.accessFlags,
                )
            ) {
                throw PatchException("NewX timeline-position map field is not public: $mapField")
            }

            val originalContinuation =
                getterMethod.instructions.getOrNull(timelineResultIndex + 1)
                    ?: throw PatchException("NewX scroll-position getter continuation was not found")
            val timelineGetterInstruction =
                getterMethod.instructions.getOrNull(timelineResultIndex - 1)
                    ?: throw PatchException("NewX timeline-type getter call was not found")
            val timelineGetterReference =
                timelineGetterInstruction.getReference<MethodReference>()
                    ?: throw PatchException("NewX timeline-type getter call has no method reference")
            val repositoryField =
                getterMethod.instructions.mapNotNull { instruction ->
                    if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                    val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                    field.takeIf {
                        it.definingClass.toString() == componentDescriptor &&
                            it.type.toString() == timelineGetterReference.definingClass.toString()
                    }
                }.singleOrNull()
                    ?: throw PatchException("NewX timeline repository field was not found uniquely")
            val timelineGetterInvoke =
                timelineGetterInstruction as? FiveRegisterInstruction
                    ?: throw PatchException("NewX timeline-type getter call has an unsupported register layout")
            if (timelineGetterInvoke.registerCount != 1) {
                throw PatchException(
                    "Unexpected NewX timeline-type getter call register count: " +
                        timelineGetterInvoke.registerCount,
                )
            }
            val timelineGetterReceiverRegister = timelineGetterInvoke.registerC
            val restoreRegisters =
                try {
                    getterMethod
                        .getFreeRegisterProvider(
                            timelineResultIndex + 1,
                            RESTORE_TEMPORARY_REGISTER_COUNT,
                            timelineRegister,
                            timelineGetterReceiverRegister,
                        ).let { provider ->
                            List(RESTORE_TEMPORARY_REGISTER_COUNT) {
                                provider.getFreeRegister4Bit()
                            }
                        }
                } catch (exception: RuntimeException) {
                    throw PatchException(
                        "Could not allocate NewX timeline-position restore registers",
                        exception,
                    )
                }
            val mapOwnerRegister = restoreRegisters[0]
            val positionsRegister = mapOwnerRegister
            val mapRegister = restoreRegisters[1]
            val indexRegister = mapRegister
            val offsetRegister = timelineRegister
            getterMethod.addInstructionsWithLabels(
                timelineResultIndex + 1,
                """
                    iget-object v$mapOwnerRegister, p0, $componentField
                    iget-object v$mapRegister, v$mapOwnerRegister, $mapField
                    invoke-virtual {v$mapRegister, v$timelineRegister}, $CONCURRENT_HASH_MAP_DESCRIPTOR->get(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$positionsRegister
                    if-nez v$positionsRegister, :piko_newx_restore_position_continue
                    invoke-static {v$timelineRegister}, $TIMELINE_POSITION_STORE_DESCRIPTOR->restore($ENUM_DESCRIPTOR)[I
                    move-result-object v$positionsRegister
                    if-eqz v$positionsRegister, :piko_newx_restore_position_continue
                    const/4 v$indexRegister, 0x0
                    aget v$indexRegister, v$positionsRegister, v$indexRegister
                    const/4 v$offsetRegister, 0x1
                    aget v$offsetRegister, v$positionsRegister, v$offsetRegister
                    new-instance v$positionsRegister, $holderDescriptor
                    invoke-direct {v$positionsRegister, v$indexRegister, v$offsetRegister}, $holderConstructorReference
                    invoke-interface {v$timelineGetterReceiverRegister}, $timelineGetterReference
                    move-result-object v$timelineRegister
                    iget-object v$mapRegister, p0, $componentField
                    iget-object v$mapRegister, v$mapRegister, $mapField
                    invoke-virtual {v$mapRegister, v$timelineRegister, v$positionsRegister}, $CONCURRENT_HASH_MAP_DESCRIPTOR->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$mapRegister
                """.trimIndent(),
                ExternalLabel("piko_newx_restore_position_continue", originalContinuation),
            )

            val fallbackHolderCandidates =
                getterMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.NEW_INSTANCE) return@filter false
                    if (indexedInstruction.value.getReference<TypeReference>()?.type != holderDescriptor) {
                        return@filter false
                    }
                    val holderRegister =
                        (indexedInstruction.value as? OneRegisterInstruction)?.registerA
                        ?: return@filter false
                    val constructor = getterMethod.instructions.getOrNull(indexedInstruction.index + 1)
                    val returnedHolder = getterMethod.instructions.getOrNull(indexedInstruction.index + 2)
                    constructor?.opcode == Opcode.INVOKE_DIRECT &&
                        constructor.getReference<MethodReference>()?.toString() == holderConstructorReference &&
                        returnedHolder?.opcode == Opcode.RETURN_OBJECT &&
                        (returnedHolder as? OneRegisterInstruction)?.registerA == holderRegister
                }
            if (fallbackHolderCandidates.size != 1) {
                throw PatchException(
                    "Expected one NewX zero-position fallback holder allocation, found " +
                        "${fallbackHolderCandidates.size}: ${fallbackHolderCandidates.joinToString()}",
                )
            }
            val fallbackHolderCandidate = fallbackHolderCandidates.single()
            val fallbackHolderRegister =
                (fallbackHolderCandidate.value as? OneRegisterInstruction)?.registerA
                    ?: throw PatchException("NewX zero-position fallback holder allocation has no register layout")
            val fallbackRead =
                restoreTimelinePosition.injectRead(
                    method = getterMethod,
                    index = fallbackHolderCandidate.index,
                    excludedRegisters = listOf(fallbackHolderRegister),
                    registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                )
            val settingReadInstructionCount = fallbackRead.nextIndex - fallbackHolderCandidate.index
            getterMethod.removeInstructions(fallbackHolderCandidate.index, settingReadInstructionCount)
            val fallbackRegisters =
                try {
                    getterMethod
                        .getFreeRegisterProvider(
                            fallbackHolderCandidate.index + 1,
                            FALLBACK_RESTORE_TEMPORARY_REGISTER_COUNT,
                            fallbackRead.register,
                            fallbackHolderRegister,
                        ).let { provider ->
                            List(FALLBACK_RESTORE_TEMPORARY_REGISTER_COUNT) {
                                provider.getFreeRegister4Bit()
                            }
                        }
                } catch (exception: RuntimeException) {
                    throw PatchException(
                        "Could not allocate NewX fallback timeline-position restore registers",
                        exception,
                    )
                }
            val fallbackRepositoryRegister = fallbackRegisters[0]
            val fallbackTimelineRegister = fallbackRegisters[1]
            val fallbackPositionsRegister = fallbackRegisters[2]
            getterMethod.replaceInstruction(
                fallbackHolderCandidate.index,
                "const-string v${fallbackRead.register}, \"newx.timeline.restore_position\"",
            )
            getterMethod.addInstruction(
                fallbackHolderCandidate.index + 1,
                "new-instance v$fallbackHolderRegister, $holderDescriptor",
            )
            val nativeFallbackInstruction = getterMethod.instructions[fallbackHolderCandidate.index + 1]
            getterMethod.addInstructionsWithLabels(
                fallbackHolderCandidate.index + 1,
                """
                    invoke-static {v${fallbackRead.register}}, Lapp/morphe/extension/newx/settings/SettingsRegistry;->getBooleanOrDefault(Ljava/lang/String;)Z
                    move-result v${fallbackRead.register}
                    if-eqz v${fallbackRead.register}, :piko_newx_restore_position_fallback
                    iget-object v$fallbackRepositoryRegister, p0, $repositoryField
                    invoke-interface {v$fallbackRepositoryRegister}, $timelineGetterReference
                    move-result-object v$fallbackTimelineRegister
                    invoke-static {v$fallbackTimelineRegister}, $TIMELINE_POSITION_STORE_DESCRIPTOR->restore($ENUM_DESCRIPTOR)[I
                    move-result-object v$fallbackPositionsRegister
                    if-eqz v$fallbackPositionsRegister, :piko_newx_restore_position_fallback
                    const/4 v${fallbackRead.register}, 0x0
                    aget v${fallbackRead.register}, v$fallbackPositionsRegister, v${fallbackRead.register}
                    const/4 v$fallbackTimelineRegister, 0x1
                    aget v$fallbackTimelineRegister, v$fallbackPositionsRegister, v$fallbackTimelineRegister
                    new-instance v$fallbackRepositoryRegister, $holderDescriptor
                    invoke-direct {v$fallbackRepositoryRegister, v${fallbackRead.register}, v$fallbackTimelineRegister}, $holderConstructorReference
                    return-object v$fallbackRepositoryRegister
                """.trimIndent(),
                ExternalLabel("piko_newx_restore_position_fallback", nativeFallbackInstruction),
            )

            val saveMatches =
                saveScrollPositionFingerprint(componentDescriptor, holderDescriptor).scopedMatchAll()
            if (saveMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX save-scroll-position method, found ${saveMatches.size}: " +
                        saveMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val saveMethod = saveMatches.single().method
            val mapPutCandidates =
                saveMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.INVOKE_VIRTUAL) return@filter false
                    val reference = indexedInstruction.value.getReference<MethodReference>() ?: return@filter false
                    reference.definingClass.toString() == CONCURRENT_HASH_MAP_DESCRIPTOR &&
                        reference.name == "put" &&
                        reference.parameterTypes.map(CharSequence::toString) ==
                            listOf("Ljava/lang/Object;", "Ljava/lang/Object;") &&
                        reference.returnType.toString() == "Ljava/lang/Object;"
                }
            if (mapPutCandidates.size != 1) {
                throw PatchException(
                    "Expected one NewX timeline-position map write, found " +
                        "${mapPutCandidates.size}: ${mapPutCandidates.joinToString()}",
                )
            }
            val mapPutIndex = mapPutCandidates.single().index
            val mapPutInstruction =
                saveMethod.instructions[mapPutIndex] as? FiveRegisterInstruction
                    ?: throw PatchException("NewX timeline-position map write has an unsupported register layout")
            if (mapPutInstruction.registerCount != 3) {
                throw PatchException(
                    "Unexpected NewX timeline-position map write register count: " +
                        mapPutInstruction.registerCount,
                )
            }
            val saveTimelineRegister = mapPutInstruction.registerD
            val saveHolderRegister = mapPutInstruction.registerE
            if (saveTimelineRegister !in 0..15 || saveHolderRegister !in 0..15) {
                throw PatchException(
                    "NewX timeline-position save registers are not encodable: " +
                        "v$saveTimelineRegister, v$saveHolderRegister",
                )
            }

            // Ranked Following uses the same shared save method as Latest Following, but
            // its Compose scroll policy has `a == false`. The original method branches
            // around the map write in that case, so the persistent store hook below never
            // receives a position. The policy class is R8-renamed between releases; keep
            // the semantic layout-package/boolean/branch shape as the release anchor.
            val layoutPolicyGateCandidates =
                saveMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.IGET_BOOLEAN) return@filter false
                    val field = indexedInstruction.value.getReference<FieldReference>() ?: return@filter false
                    field.type.toString() == "Z" &&
                        field.definingClass.toString().startsWith("Landroidx/compose/foundation/layout/") &&
                        saveMethod.instructions.getOrNull(indexedInstruction.index + 1)?.opcode == Opcode.IF_EQZ
                }
            if (layoutPolicyGateCandidates.size != 1) {
                throw PatchException(
                    "Expected one NewX Ranked Following save-policy gate, found " +
                        "${layoutPolicyGateCandidates.size}: ${layoutPolicyGateCandidates.joinToString()}",
                )
            }
            saveMethod.replaceInstruction(
                layoutPolicyGateCandidates.single().index + 1,
                "nop",
            )
            saveMethod.addInstruction(
                mapPutIndex,
                "invoke-static {v$saveTimelineRegister, v$saveHolderRegister}, " +
                    "$TIMELINE_POSITION_STORE_DESCRIPTOR->save(${ENUM_DESCRIPTOR}Ljava/lang/Object;)V",
            )
        }
    }
