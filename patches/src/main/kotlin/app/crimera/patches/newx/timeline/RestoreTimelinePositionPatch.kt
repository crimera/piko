package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val CONCURRENT_HASH_MAP_DESCRIPTOR = "Ljava/util/concurrent/ConcurrentHashMap;"
private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val TIMELINE_POSITION_STORE_DESCRIPTOR =
    "Lapp/morphe/extension/newx/timeline/TimelineScrollPositionStore;"
private const val MAP_GET_DESCRIPTOR = "$CONCURRENT_HASH_MAP_DESCRIPTOR->get($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"
private const val MAP_REMOVE_DESCRIPTOR =
    "$CONCURRENT_HASH_MAP_DESCRIPTOR->remove($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"
private const val MAP_PUT_DESCRIPTOR =
    "$CONCURRENT_HASH_MAP_DESCRIPTOR->put($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"
private const val HAS_IN_MEMORY_POSITION_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->useInMemoryPosition($ENUM_DESCRIPTOR)Z"
private const val RESTORE_POSITION_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->restore(${ENUM_DESCRIPTOR}${STRING_DESCRIPTOR})[I"
private const val SAVE_POSITION_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->save(${ENUM_DESCRIPTOR}${STRING_DESCRIPTOR}II)V"
private const val SETTING_READ_DESCRIPTOR =
    "Lapp/morphe/extension/newx/settings/SettingsRegistry;->getBooleanOrDefault($STRING_DESCRIPTOR)Z"

/**
 * Branch destination that skips the in-memory restore attempt. The other two smali labels are gone:
 * both pointed at the instruction sitting at their insertion index, which `Target.Original`
 * addresses without an instruction object.
 */
private const val IGNORE_NATIVE_POSITION_LABEL = "piko_newx_restore_position_ignore_native"
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
        description = "Persists supported timeline positions, then restores them after the app process restarts.",
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
        newXToggle(
            id = "newx.profile.restore_position",
            category = Categories.TIMELINE,
            strings = settingStrings("piko_newx_restore_profile_position"),
            order = 151,
            defaultValue = false,
            rebootApp = true,
        )

        execute {
            val holderMatch =
                requireExactlyOne(
                    "NewX scroll-position holder",
                    NewXScrollPositionHolderFingerprint.scopedMatchAll(),
                )
            val holderDescriptor = holderMatch.originalClassDef.type
            requireExactlyOne(
                "NewX scroll-position holder constructor",
                mutableClassDefBy(holderDescriptor).methods.filter { method ->
                    method.name == "<init>" &&
                        method.parameterTypes.map(CharSequence::toString) == listOf("I", "I") &&
                        method.returnType == "V"
                },
            )
            val holderConstructorReference = "$holderDescriptor-><init>(II)V"
            val holderPositionFields =
                holderMatch.method.instructions.mapNotNull { instruction ->
                    if (instruction.opcode != Opcode.IGET) return@mapNotNull null
                    val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                    field.takeIf {
                        it.definingClass.toString() == holderDescriptor &&
                            it.type.toString() == "I"
                    }
                }.distinctBy(FieldReference::toString)
            if (holderPositionFields.size != 2) {
                throw PatchException(
                    "Expected two ordered NewX scroll-position holder fields, found " +
                        "${holderPositionFields.size}: ${holderPositionFields.joinToString()}",
                )
            }
            holderPositionFields.forEach { fieldReference ->
                val definition =
                    requireExactlyOne(
                        "NewX scroll-position holder field definition $fieldReference",
                        mutableClassDefBy(holderDescriptor).fields.filter { field ->
                            field.toString() == fieldReference.toString()
                        },
                    )
                if (!AccessFlags.PUBLIC.isSet(definition.accessFlags)) {
                    throw PatchException(
                        "NewX scroll-position holder field is not public: $fieldReference",
                    )
                }
            }

            val getterMatch =
                requireExactlyOne(
                    "NewX scroll-position getter",
                    scrollPositionGetterFingerprint(holderDescriptor).scopedMatchAll(),
                )
            var getterMethod = getterMatch.method
            val originalRegisterCount =
                getterMethod.implementation?.registerCount
                    ?: throw PatchException("NewX scroll-position getter has no implementation")
            val parameterRegisterCount = getterMethod.numberOfParameterRegisters
            if (AccessFlags.STATIC.isSet(getterMethod.accessFlags)) {
                throw PatchException("NewX scroll-position getter unexpectedly has no instance receiver")
            }
            val expandedMethod =
                getterMethod.cloneMutable(
                    additionalRegisters =
                        parameterRegisterCount +
                            RESTORE_TEMPORARY_REGISTER_COUNT +
                            FALLBACK_RESTORE_TEMPORARY_REGISTER_COUNT,
                )
            getterMatch.classDef.methods.remove(getterMethod)
            getterMatch.classDef.methods.add(expandedMethod)
            getterMethod = expandedMethod
            val expandedRegisterCount =
                getterMethod.implementation?.registerCount
                    ?: throw PatchException("NewX cloned scroll-position getter has no implementation")
            val scratchBase = originalRegisterCount
            val restoreRegisters = scratchBase..scratchBase + 1
            val fallbackRegisters = scratchBase + 2..scratchBase + 4
            val scratchLast = fallbackRegisters.last
            val shiftedParameterRegionStart = expandedRegisterCount - parameterRegisterCount
            if (scratchBase < 0 || scratchLast > 15 || scratchLast >= shiftedParameterRegionStart) {
                throw PatchException(
                    "NewX scroll-position getter scratch register range is invalid for " +
                        "${getterMatch.originalMethod}: originalRegisters=$originalRegisterCount, " +
                        "expandedRegisters=$expandedRegisterCount, " +
                        "parameterRegisters=$parameterRegisterCount, " +
                        "attemptedRange=v$scratchBase..v$scratchLast, " +
                        "shiftedParameterRegionStart=v$shiftedParameterRegionStart",
                )
            }

            val mapGetCandidates =
                getterMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.INVOKE_VIRTUAL) return@filter false
                    val reference = indexedInstruction.value.getReference<MethodReference>() ?: return@filter false
                    reference.definingClass.toString() == CONCURRENT_HASH_MAP_DESCRIPTOR &&
                        reference.name == "get" &&
                        reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
                        reference.returnType.toString() == "Ljava/lang/Object;"
                }
            val mapGetCandidate = requireExactlyOne("NewX timeline-position map read", mapGetCandidates)
            val mapGetIndex = mapGetCandidate.index
            val mapGetInstruction =
                mapGetCandidate.value as? FiveRegisterInstruction
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
            val timelineResultCandidate =
                requireExactlyOne(
                    "NewX timeline-type result feeding the map read",
                    timelineResultCandidates,
                )
            val timelineResultIndex = timelineResultCandidate.first

            val mapField =
                requireExactlyOne(
                    "NewX timeline-position map field",
                    getterMethod.instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                        val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                        field.takeIf { it.type.toString() == CONCURRENT_HASH_MAP_DESCRIPTOR }
                    },
                )
            val componentDescriptor = getterMatch.originalMethod.definingClass
            val componentField =
                requireExactlyOne(
                    "NewX timeline-position map owner field",
                    getterMethod.instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                        val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                        field.takeIf {
                            it.definingClass.toString() == componentDescriptor &&
                                it.type.toString() == mapField.definingClass.toString()
                        }
                    },
                )
            if (mapField.definingClass.toString() == componentDescriptor) {
                throw PatchException("NewX timeline-position map unexpectedly belongs to the component")
            }
            if (!AccessFlags.PUBLIC.isSet(
                    requireExactlyOne(
                        "NewX timeline-position map field definition",
                        mutableClassDefBy(mapField.definingClass.toString()).fields.filter { field ->
                            field.toString() == mapField.toString()
                        },
                    ).accessFlags,
                )
            ) {
                throw PatchException("NewX timeline-position map field is not public: $mapField")
            }

            // `Target.Original` needs no anchor instruction, but the continuation still has to exist.
            if (timelineResultIndex + 1 >= getterMethod.instructions.size) {
                throw PatchException("NewX scroll-position getter continuation was not found")
            }
            val timelineGetterInstruction =
                getterMethod.instructions.getOrNull(timelineResultIndex - 1)
                    ?: throw PatchException("NewX timeline-type getter call was not found")
            val timelineGetterReference =
                timelineGetterInstruction.getReference<MethodReference>()
                    ?: throw PatchException("NewX timeline-type getter call has no method reference")
            val repositoryClass = mutableClassDefBy(timelineGetterReference.definingClass)
            val timelineIdentityGetter =
                requireExactlyOne(
                    "NewX timeline identity getter",
                    repositoryClass.methods.filter { method ->
                        val returnType = method.returnType.toString()
                        method.parameterTypes.isEmpty() &&
                            returnType.startsWith("Lcom/x/models/timelines/") &&
                            returnType != timelineGetterReference.returnType.toString() &&
                            runCatching {
                                mutableClassDefBy(returnType).fields.count { field ->
                                    field.type.toString() == STRING_DESCRIPTOR
                                } == 1
                            }.getOrDefault(false)
                    },
                )
            val timelineIdentityDescriptor = timelineIdentityGetter.returnType.toString()
            val timelineIdentityGetterReference =
                "${timelineGetterReference.definingClass}->${timelineIdentityGetter.name}()" +
                    timelineIdentityDescriptor
            val timelineIdentityField =
                requireExactlyOne(
                    "NewX timeline identity field",
                    mutableClassDefBy(timelineIdentityDescriptor).fields.filter { field ->
                        field.type.toString() == STRING_DESCRIPTOR
                    },
                )
            if (!AccessFlags.PUBLIC.isSet(timelineIdentityField.accessFlags)) {
                throw PatchException("NewX timeline identity field is not public: $timelineIdentityField")
            }
            val repositoryField =
                requireExactlyOne(
                    "NewX timeline repository field",
                    getterMethod.instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                        val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                        field.takeIf {
                            it.definingClass.toString() == componentDescriptor &&
                                it.type.toString() == timelineGetterReference.definingClass.toString()
                        }
                    },
                )
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
            val mapOwnerRegister = restoreRegisters.first
            val positionsRegister = mapOwnerRegister
            val mapRegister = restoreRegisters.last
            val indexRegister = mapRegister
            val offsetRegister = timelineRegister
            // The old `continue` label sat on the instruction after the enum result, so the block's
            // jumps have to land on that original instruction: `Target.Original` with the
            // insertion point's labels left in place, exactly like `addInstructionsWithLabels`.
            getterMethod.insertHook(
                index = timelineResultIndex + 1,
                relocateBranchTargets = false,
            ) {
                move(mapOwnerRegister, getterMethod.p0Register, OBJECT_DESCRIPTOR)
                iget(mapOwnerRegister, mapOwnerRegister, componentField)
                iget(mapRegister, mapOwnerRegister, mapField)
                invokeVirtual(methodReference(MAP_GET_DESCRIPTOR), mapRegister, timelineRegister)
                moveResult(positionsRegister, OBJECT_DESCRIPTOR)
                invokeStatic(methodReference(HAS_IN_MEMORY_POSITION_DESCRIPTOR), timelineRegister)
                moveResult(mapRegister, "Z")
                ifEqz(mapRegister, Target.Local(IGNORE_NATIVE_POSITION_LABEL))
                ifNez(positionsRegister, Target.Original)
                label(IGNORE_NATIVE_POSITION_LABEL)
                constInt(positionsRegister, 0)
                move(mapOwnerRegister, getterMethod.p0Register, OBJECT_DESCRIPTOR)
                iget(mapOwnerRegister, mapOwnerRegister, componentField)
                iget(mapRegister, mapOwnerRegister, mapField)
                invokeVirtual(methodReference(MAP_REMOVE_DESCRIPTOR), mapRegister, timelineRegister)
                moveResult(mapRegister, OBJECT_DESCRIPTOR)
                invokeInterface(methodReference(timelineIdentityGetterReference), timelineGetterReceiverRegister)
                moveResult(mapRegister, OBJECT_DESCRIPTOR)
                iget(mapRegister, mapRegister, timelineIdentityField)
                invokeStatic(methodReference(RESTORE_POSITION_DESCRIPTOR), timelineRegister, mapRegister)
                moveResult(positionsRegister, "[I")
                ifEqz(positionsRegister, Target.Original)
                constInt(indexRegister, 0)
                aget(indexRegister, positionsRegister, indexRegister)
                constInt(offsetRegister, 1)
                aget(offsetRegister, positionsRegister, offsetRegister)
                newInstance(positionsRegister, holderDescriptor)
                invokeDirect(
                    methodReference(holderConstructorReference),
                    positionsRegister,
                    indexRegister,
                    offsetRegister,
                )
                invokeInterface(timelineGetterReference, timelineGetterReceiverRegister)
                moveResult(timelineRegister, OBJECT_DESCRIPTOR)
                move(mapRegister, getterMethod.p0Register, OBJECT_DESCRIPTOR)
                iget(mapRegister, mapRegister, componentField)
                iget(mapRegister, mapRegister, mapField)
                invokeVirtual(methodReference(MAP_PUT_DESCRIPTOR), mapRegister, timelineRegister, positionsRegister)
                moveResult(mapRegister, OBJECT_DESCRIPTOR)
            }

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
            val fallbackHolderCandidate =
                requireExactlyOne(
                    "NewX zero-position fallback holder allocation",
                    fallbackHolderCandidates,
                )
            val fallbackHolderRegister =
                (fallbackHolderCandidate.value as? OneRegisterInstruction)?.registerA
                    ?: throw PatchException("NewX zero-position fallback holder allocation has no register layout")
            val fallbackRead =
                restoreTimelinePosition.injectRead(
                    method = getterMethod,
                    index = fallbackHolderCandidate.index,
                    excludedRegisters =
                        restoreRegisters.toList() +
                            fallbackRegisters.toList() +
                            fallbackHolderRegister,
                    registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                )
            val settingReadInstructionCount = fallbackRead.nextIndex - fallbackHolderCandidate.index
            getterMethod.removeInstructions(fallbackHolderCandidate.index, settingReadInstructionCount)
            val fallbackRepositoryRegister = fallbackRegisters.first
            val fallbackTimelineRegister = fallbackRegisters.first + 1
            val fallbackPositionsRegister = fallbackRegisters.last
            // Raw smali replacement: the typed API has no in-place replace primitive, and this slot is
            // the one the surrounding getter branches into for a missing position. `replaceInstruction`
            // writes into the existing location, so the labels already on it keep pointing here (and run
            // the read below); a typed insertion would leave them on the holder allocation instead.
            getterMethod.replaceInstruction(
                fallbackHolderCandidate.index,
                "const-string v${fallbackRead.register}, \"newx.timeline.restore_position\"",
            )
            // The holder allocation is the instruction both guard jumps below land on, and
            // `Target.Original` addresses the instruction already sitting at the hook index, so the
            // allocation is inserted first - into the same slot the smali `addInstruction` used.
            getterMethod.insertHook(
                index = fallbackHolderCandidate.index + 1,
                relocateBranchTargets = false,
            ) {
                newInstance(fallbackHolderRegister, holderDescriptor)
            }
            // The old `fallback` label sat on the freshly allocated holder, which is the instruction at the
            // insertion index, so `Target.Original` makes both guard jumps land there again.
            getterMethod.insertHook(
                index = fallbackHolderCandidate.index + 1,
                relocateBranchTargets = false,
            ) {
                invokeStatic(methodReference(SETTING_READ_DESCRIPTOR), fallbackRead.register)
                moveResult(fallbackRead.register, "Z")
                ifEqz(fallbackRead.register, Target.Original)
                move(fallbackRepositoryRegister, getterMethod.p0Register, OBJECT_DESCRIPTOR)
                iget(fallbackRepositoryRegister, fallbackRepositoryRegister, repositoryField)
                invokeInterface(timelineGetterReference, fallbackRepositoryRegister)
                moveResult(fallbackTimelineRegister, OBJECT_DESCRIPTOR)
                invokeInterface(methodReference(timelineIdentityGetterReference), fallbackRepositoryRegister)
                moveResult(fallbackRepositoryRegister, OBJECT_DESCRIPTOR)
                iget(fallbackRepositoryRegister, fallbackRepositoryRegister, timelineIdentityField)
                invokeStatic(
                    methodReference(RESTORE_POSITION_DESCRIPTOR),
                    fallbackTimelineRegister,
                    fallbackRepositoryRegister,
                )
                moveResult(fallbackPositionsRegister, "[I")
                ifEqz(fallbackPositionsRegister, Target.Original)
                constInt(fallbackRead.register, 0)
                aget(fallbackRead.register, fallbackPositionsRegister, fallbackRead.register)
                constInt(fallbackTimelineRegister, 1)
                aget(fallbackTimelineRegister, fallbackPositionsRegister, fallbackTimelineRegister)
                newInstance(fallbackRepositoryRegister, holderDescriptor)
                invokeDirect(
                    methodReference(holderConstructorReference),
                    fallbackRepositoryRegister,
                    fallbackRead.register,
                    fallbackTimelineRegister,
                )
                returnObject(fallbackRepositoryRegister)
            }

            val saveMatch =
                requireExactlyOne(
                    "NewX save-scroll-position method",
                    saveScrollPositionFingerprint(componentDescriptor, holderDescriptor).scopedMatchAll(),
                )
            val originalSaveMethod = saveMatch.method
            if (originalSaveMethod.implementation == null) {
                throw PatchException("NewX save-scroll-position method has no implementation")
            }
            val saveMethod =
                originalSaveMethod.cloneMutable(
                    additionalRegisters = originalSaveMethod.numberOfParameterRegisters + 3,
                ).also { expandedMethod ->
                    saveMatch.classDef.methods.remove(originalSaveMethod)
                    saveMatch.classDef.methods.add(expandedMethod)
                }
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
            val mapPutCandidate = requireExactlyOne("NewX timeline-position map write", mapPutCandidates)
            val mapPutIndex = mapPutCandidate.index
            val mapPutInstruction =
                mapPutCandidate.value as? FiveRegisterInstruction
                    ?: throw PatchException("NewX timeline-position map write has an unsupported register layout")
            if (mapPutInstruction.registerCount != 3) {
                throw PatchException(
                    "Unexpected NewX timeline-position map write register count: " +
                        mapPutInstruction.registerCount,
                )
            }
            val saveTimelineRegister = mapPutInstruction.registerD
            val saveHolderRegister = mapPutInstruction.registerE
            val saveMapRegister = mapPutInstruction.registerC
            if (saveTimelineRegister !in 0..15 ||
                saveHolderRegister !in 0..15 ||
                saveMapRegister !in 0..15
            ) {
                throw PatchException(
                    "NewX timeline-position save registers are not encodable: " +
                        "v$saveTimelineRegister, v$saveHolderRegister, v$saveMapRegister",
                )
            }

            requireExactlyOne(
                "NewX save-scroll-position repository read",
                saveMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.IGET_OBJECT) return@filter false
                    val field = indexedInstruction.value.getReference<FieldReference>() ?: return@filter false
                    if (field.toString() != repositoryField.toString()) return@filter false
                    val fieldInstruction =
                        indexedInstruction.value as? TwoRegisterInstruction
                            ?: return@filter false
                    val getterInstruction = saveMethod.instructions.getOrNull(indexedInstruction.index + 1)
                    val getterReference = getterInstruction?.getReference<MethodReference>()
                        ?: return@filter false
                    if (getterReference.toString() != timelineGetterReference.toString()) return@filter false
                    val getterInvoke = getterInstruction as? FiveRegisterInstruction
                        ?: return@filter false
                    if (getterInvoke.registerCount != 1 ||
                        getterInvoke.registerC != fieldInstruction.registerA
                    ) {
                        return@filter false
                    }
                    val resultInstruction = saveMethod.instructions.getOrNull(indexedInstruction.index + 2)
                        as? OneRegisterInstruction
                        ?: return@filter false
                    if (resultInstruction.registerA != saveTimelineRegister) return@filter false
                    indexedInstruction.index + 2 < mapPutIndex &&
                        saveMethod.instructions.withIndex().none { laterInstruction ->
                            laterInstruction.index > indexedInstruction.index + 2 &&
                                laterInstruction.index < mapPutIndex &&
                                laterInstruction.value.getReference<MethodReference>()?.toString() ==
                                    timelineGetterReference.toString() &&
                                (saveMethod.instructions.getOrNull(laterInstruction.index + 1)
                                    as? OneRegisterInstruction)?.registerA == saveTimelineRegister
                        }
                },
            )
            // The scratch pool now hands out the three staging registers inside the hook below.

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
            val layoutPolicyGateIndex =
                requireExactlyOne(
                    "NewX Ranked Following save-policy gate",
                    layoutPolicyGateCandidates,
                ).index
            // Raw smali replacement: the typed API only emits through hooks, so the standalone `nop`
            // that neutralizes the gate branch has no typed primitive - `insertHook` here would
            // leave the branch in place and put the nop in front of it instead.
            saveMethod.replaceInstruction(
                layoutPolicyGateIndex + 1,
                "nop",
            )
            saveMethod.insertHook(
                index = mapPutIndex,
                // The three registers the original call reads are live here and must stay untouched.
                excludedRegisters = listOf(saveTimelineRegister, saveHolderRegister, saveMapRegister),
                // Plain insertion used to leave an incoming branch on the map write, which keeps skipping
                // the hook exactly as before.
                relocateBranchTargets = false,
            ) {
                val saveIdentityRegister = scratchRegister()
                val saveIndexRegister = scratchRegister()
                val saveOffsetRegister = scratchRegister()
                move(saveIdentityRegister, saveMethod.p0Register, OBJECT_DESCRIPTOR)
                iget(saveIdentityRegister, saveIdentityRegister, repositoryField)
                invokeInterface(methodReference(timelineIdentityGetterReference), saveIdentityRegister)
                moveResult(saveIdentityRegister, OBJECT_DESCRIPTOR)
                iget(saveIdentityRegister, saveIdentityRegister, timelineIdentityField)
                // The resolver already proved the holder has exactly these two public int fields;
                // destructure instead of indexing so the cardinality stays explicit here too.
                val (positionIndexField, positionOffsetField) =
                    holderPositionFields.also { fields ->
                        if (fields.size != 2) {
                            throw PatchException(
                                "Expected two NewX scroll-position holder fields, found ${fields.size}",
                            )
                        }
                    }
                iget(saveIndexRegister, saveHolderRegister, positionIndexField)
                iget(saveOffsetRegister, saveHolderRegister, positionOffsetField)
                invokeStatic(
                    methodReference(SAVE_POSITION_DESCRIPTOR),
                    saveTimelineRegister,
                    saveIdentityRegister,
                    saveIndexRegister,
                    saveOffsetRegister,
                )
            }
        }
    }
