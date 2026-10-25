package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.SettingReadRegisterConstraint
import app.crimera.patches.settings.injectRead
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.classDefFlatMap
import app.crimera.patches.common.isAssignableTo
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.scopedMatchAll
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
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod

private const val CONCURRENT_HASH_MAP_DESCRIPTOR = "Ljava/util/concurrent/ConcurrentHashMap;"
private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val ARRAY_LIST_DESCRIPTOR = "Ljava/util/ArrayList;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val INSTANT_DESCRIPTOR = "Lkotlin/time/Instant;"
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
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->restore(${ENUM_DESCRIPTOR}${STRING_DESCRIPTOR}J)[I"
private const val SAVE_POSITION_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->save(${ENUM_DESCRIPTOR}${STRING_DESCRIPTOR}JII)V"
private const val SAVE_ANCHOR_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->saveAnchor(${ENUM_DESCRIPTOR}${STRING_DESCRIPTOR}J${STRING_DESCRIPTOR}JI)V"
private const val SHOULD_SAVE_ANCHOR_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->shouldSaveAnchor(${ENUM_DESCRIPTOR}J)Z"
private const val HAS_PENDING_ANCHOR_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->hasPendingAnchor()Z"
private const val PENDING_ANCHOR_ENTRY_ID_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->pendingAnchorEntryId(${ENUM_DESCRIPTOR}${STRING_DESCRIPTOR}J)$STRING_DESCRIPTOR"
private const val PENDING_ANCHOR_SORT_INDEX_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->pendingAnchorSortIndex(${ENUM_DESCRIPTOR}${STRING_DESCRIPTOR}J)J"
private const val CONSUME_ANCHOR_DESCRIPTOR =
    "$TIMELINE_POSITION_STORE_DESCRIPTOR->consumeAnchor(${ENUM_DESCRIPTOR}${STRING_DESCRIPTOR}JI)[I"
private const val SETTING_READ_DESCRIPTOR =
    "$SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault($STRING_DESCRIPTOR)Z"

/**
 * Branch destination that skips the in-memory restore attempt. The other two smali labels are gone:
 * both pointed at the instruction sitting at their insertion index, which `Target.Original`
 * addresses without an instruction object.
 */
private const val IGNORE_NATIVE_POSITION_LABEL = "piko_newx_restore_position_ignore_native"
private const val ACCOUNT_ID_BRIDGE_NAME = "piko\$newxAccountId"
private const val SAVE_ANCHOR_BRIDGE_NAME = "piko\$newxSaveAnchor"
private const val RESTORE_ANCHOR_BRIDGE_NAME = "piko\$newxRestoreAnchor"
private const val APPLY_ANCHOR_KEY_BRIDGE_NAME = "piko\$newxApplyAnchorKey"
private const val ACCOUNT_ID_BRIDGE_NULL_LABEL = "piko_newx_account_id_null"
private const val APPLY_ANCHOR_KEY_NULL_LABEL = "piko_newx_apply_anchor_key_null"
private val MOVE_RESULT_OPCODES = setOf(Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_OBJECT, Opcode.MOVE_RESULT_WIDE)
private val INVOKE_OPCODES =
    setOf(
        Opcode.INVOKE_VIRTUAL,
        Opcode.INVOKE_VIRTUAL_RANGE,
        Opcode.INVOKE_SUPER,
        Opcode.INVOKE_SUPER_RANGE,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_DIRECT_RANGE,
        Opcode.INVOKE_STATIC,
        Opcode.INVOKE_STATIC_RANGE,
        Opcode.INVOKE_INTERFACE,
        Opcode.INVOKE_INTERFACE_RANGE,
        Opcode.INVOKE_POLYMORPHIC,
        Opcode.INVOKE_POLYMORPHIC_RANGE,
        Opcode.INVOKE_CUSTOM,
        Opcode.INVOKE_CUSTOM_RANGE,
    )
private const val RESTORE_TEMPORARY_REGISTER_COUNT = 4
private const val FALLBACK_RESTORE_TEMPORARY_REGISTER_COUNT = 5

/**
 * Fresh locals added to the save method clone, just below its parameter block. The map put's three
 * arguments are spilled into the first two, a loaded flag takes the third, and the persistent store's
 * six argument words are staged consecutively in the last six for `invoke-static/range`.
 */
private const val SAVE_SCRATCH_LOCALS = 9
private const val SAVE_SKIPPED_LABEL = "piko_newx_restore_position_save_skipped"
private const val SAVE_ANCHOR_SUCCESS_LABEL = "piko_newx_save_anchor_success"
private const val SAVE_ANCHOR_NULL_LABEL = "piko_newx_save_anchor_null"
private const val SAVE_ANCHOR_RETURN_LABEL = "piko_newx_save_anchor_return"
private const val RESTORE_ANCHOR_NULL_LABEL = "piko_newx_restore_anchor_null"
private const val RESTORE_ANCHOR_LOOP_LABEL = "piko_newx_restore_anchor_loop"
private const val RESTORE_ANCHOR_NOT_FOUND_LABEL = "piko_newx_restore_anchor_not_found"
private const val RESTORE_ANCHOR_CONSUME_LABEL = "piko_newx_restore_anchor_consume"
private const val RESTORE_ANCHOR_GONE_LABEL = "piko_newx_restore_anchor_gone"

private val SUPPORTED_TIMELINE_POSITION_VERSIONS = setOf("12.34.0-prod.01", "12.35.0-prod.01")
private val COMPATIBILITY_TIMELINE_POSITION =
    COMPATIBILITY_NEW_X.excluding(
        *COMPATIBILITY_NEW_X.targets
            .filterNot { target -> target.version in SUPPORTED_TIMELINE_POSITION_VERSIONS }
            .map { target -> target.version }
            .toTypedArray(),
    )

private object NewXUserIdentifierSerializerFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "<clinit>",
    strings = listOf("com.x.models.UserIdentifier"),
)

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

private fun Instruction.destinationRegisterOrNull(): Int? {
    if (!opcode.setsRegister()) return null
    return when (this) {
        is ThreeRegisterInstruction -> registerA
        is TwoRegisterInstruction -> registerA
        is OneRegisterInstruction -> registerA
        else -> null
    }
}

private fun Opcode.isInvokeInstruction(): Boolean = this in INVOKE_OPCODES

private fun Instruction.invokeArgumentRegisters(): List<Int> =
    when (this) {
        is FiveRegisterInstruction ->
            listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
        is RegisterRangeInstruction ->
            (startRegister until startRegister + registerCount).toList()
        else -> throw PatchException("NewX invoke instruction has an unsupported register layout: $this")
    }

private data class DelegatingTimelineWrapper(
    val classDescriptor: String,
    val delegateGetter: MethodReference,
    val delegateGetterOpcode: Opcode,
)

private data class PostLoadScrollCallSite(
    val methodOwner: String,
    val methodName: String,
    val methodParameters: List<String>,
    val methodReturnType: String,
    val postLoadInvokeIndex: Int,
    val scrollInvokeIndex: Int,
    val moveResultRegister: Int,
    val scrollStateType: String,
    val scrollStateRegister: Int,
)

@Suppress("unused")
val restoreTimelinePositionPatch =
    bytecodePatch(
        name = "NewX: Restore timeline position",
        description = "Persists supported timeline positions, then restores them after the app process restarts.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_TIMELINE_POSITION)

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
            val userIdentifierSerializerMatch =
                requireExactlyOne(
                    "NewX UserIdentifier serializer class",
                    NewXUserIdentifierSerializerFingerprint.scopedMatchAll(),
                )
            val userIdentifierSerializerDescriptor = userIdentifierSerializerMatch.originalClassDef.type
            val userIdentifierDeserializer =
                requireExactlyOne(
                    "NewX UserIdentifier deserializer",
                    mutableClassDefBy(userIdentifierSerializerDescriptor).methods.filter { method ->
                        method.name == "deserialize" &&
                            method.parameterTypes.size == 1 &&
                            method.returnType == OBJECT_DESCRIPTOR
                    },
                )
            val userIdentifierDescriptor =
                requireExactlyOne(
                    "NewX UserIdentifier deserializer allocation type",
                    userIdentifierDeserializer.instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
                        instruction.getReference<TypeReference>()?.type
                    },
                )
            val userIdentifierIdField =
                requireExactlyOne(
                    "NewX UserIdentifier account-id field",
                    mutableClassDefBy(userIdentifierDescriptor).fields.filter { field ->
                        !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == "J"
                    },
                )
            if (!AccessFlags.PUBLIC.isSet(userIdentifierIdField.accessFlags)) {
                throw PatchException("NewX UserIdentifier account-id field is not public: $userIdentifierIdField")
            }

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
                        it.definingClass == holderDescriptor &&
                            it.type == "I"
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
            val fallbackRegisterStart = scratchBase + RESTORE_TEMPORARY_REGISTER_COUNT
            val fallbackRegisters =
                fallbackRegisterStart..
                    fallbackRegisterStart + FALLBACK_RESTORE_TEMPORARY_REGISTER_COUNT - 1
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
                    reference.definingClass == CONCURRENT_HASH_MAP_DESCRIPTOR &&
                        reference.name == "get" &&
                        reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
                        reference.returnType == "Ljava/lang/Object;"
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
                    val returnType = methodReference.returnType
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
                        field.takeIf { it.type == CONCURRENT_HASH_MAP_DESCRIPTOR }
                    },
                )
            val componentDescriptor = getterMatch.originalMethod.definingClass
            val componentAccountField =
                requireExactlyOne(
                    "NewX timeline component UserIdentifier field",
                    mutableClassDefBy(componentDescriptor).fields.filter { field ->
                        !AccessFlags.STATIC.isSet(field.accessFlags) &&
                            field.type == userIdentifierDescriptor
                    },
                )
            if (!AccessFlags.PUBLIC.isSet(componentAccountField.accessFlags)) {
                throw PatchException("NewX timeline component UserIdentifier field is not public: $componentAccountField")
            }
            val componentClass = mutableClassDefBy(componentDescriptor)
            if (componentClass.methods.any { method -> method.name == ACCOUNT_ID_BRIDGE_NAME }) {
                throw PatchException("NewX timeline component already has $ACCOUNT_ID_BRIDGE_NAME: $componentDescriptor")
            }
            val accountIdBridge =
                MutableMethod(
                    ImmutableMethod(
                        componentDescriptor,
                        ACCOUNT_ID_BRIDGE_NAME,
                        emptyList(),
                        "J",
                        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value or AccessFlags.SYNTHETIC.value,
                        emptySet(),
                        emptySet(),
                        MutableMethodImplementation(3),
                    ),
                )
            componentClass.methods.add(accountIdBridge)
            // Keep the wide field read in v0/v1; callers can have parameter registers above iget-wide's limit.
            accountIdBridge.insertHook(0, relocateBranchTargets = false) {
                iget(0, accountIdBridge.p0Register, componentAccountField)
                ifEqz(0, Target.Local(ACCOUNT_ID_BRIDGE_NULL_LABEL))
                iget(0, 0, userIdentifierIdField)
                returnWide(0)
                label(ACCOUNT_ID_BRIDGE_NULL_LABEL)
                constLong(0, 0L)
                returnWide(0)
            }
            val componentField =
                requireExactlyOne(
                    "NewX timeline-position map owner field",
                    getterMethod.instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                        val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                        field.takeIf {
                            it.definingClass == componentDescriptor &&
                                it.type == mapField.definingClass
                        }
                    },
                )
            if (mapField.definingClass == componentDescriptor) {
                throw PatchException("NewX timeline-position map unexpectedly belongs to the component")
            }
            if (!AccessFlags.PUBLIC.isSet(
                    requireExactlyOne(
                        "NewX timeline-position map field definition",
                        mutableClassDefBy(mapField.definingClass).fields.filter { field ->
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
                        val returnType = method.returnType
                        method.parameterTypes.isEmpty() &&
                            returnType.startsWith("Lcom/x/models/timelines/") &&
                            returnType != timelineGetterReference.returnType &&
                            runCatching {
                                mutableClassDefBy(returnType).fields.count { field ->
                                    field.type == STRING_DESCRIPTOR
                                } == 1
                            }.getOrDefault(false)
                    },
                )
            val timelineIdentityDescriptor = timelineIdentityGetter.returnType
            val timelineIdentityGetterReference =
                "${timelineGetterReference.definingClass}->${timelineIdentityGetter.name}()" +
                    timelineIdentityDescriptor
            val timelineIdentityField =
                requireExactlyOne(
                    "NewX timeline identity field",
                    mutableClassDefBy(timelineIdentityDescriptor).fields.filter { field ->
                        field.type == STRING_DESCRIPTOR
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
                            it.definingClass == componentDescriptor &&
                                it.type == timelineGetterReference.definingClass
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
            val accountIdRegister = scratchBase + 2
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
                invokeVirtual(
                    methodReference("$componentDescriptor->$ACCOUNT_ID_BRIDGE_NAME()J"),
                    getterMethod.p0Register,
                )
                moveResult(accountIdRegister, "J")
                invokeStatic(
                    methodReference(RESTORE_POSITION_DESCRIPTOR),
                    timelineRegister,
                    mapRegister,
                    accountIdRegister,
                    accountIdRegister + 1,
                )
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
            val fallbackPositionsRegister = fallbackRegisters.first + 2
            val fallbackAccountIdRegister = fallbackRegisters.first + 3
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
                invokeVirtual(
                    methodReference("$componentDescriptor->$ACCOUNT_ID_BRIDGE_NAME()J"),
                    getterMethod.p0Register,
                )
                moveResult(fallbackAccountIdRegister, "J")
                invokeStatic(
                    methodReference(RESTORE_POSITION_DESCRIPTOR),
                    fallbackTimelineRegister,
                    fallbackRepositoryRegister,
                    fallbackAccountIdRegister,
                    fallbackAccountIdRegister + 1,
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
                    additionalRegisters = SAVE_SCRATCH_LOCALS,
                ).also { expandedMethod ->
                    saveMatch.classDef.methods.remove(originalSaveMethod)
                    saveMatch.classDef.methods.add(expandedMethod)
                }
            // The clone's fresh locals sit directly below the parameter block: registerCount minus the
            // parameter registers gives the first local that the clone added.
            val saveLocalBase =
                (saveMethod.implementation?.registerCount
                    ?: throw PatchException("NewX save-scroll-position clone has no implementation")) -
                    originalSaveMethod.numberOfParameterRegisters - SAVE_SCRATCH_LOCALS
            val mapPutCandidates =
                saveMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.INVOKE_VIRTUAL) return@filter false
                    val reference = indexedInstruction.value.getReference<MethodReference>() ?: return@filter false
                    reference.definingClass == CONCURRENT_HASH_MAP_DESCRIPTOR &&
                        reference.name == "put" &&
                        reference.parameterTypes.map(CharSequence::toString) ==
                            listOf("Ljava/lang/Object;", "Ljava/lang/Object;") &&
                        reference.returnType == "Ljava/lang/Object;"
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
            // The save hook stages its six persistent-store argument words in the clone's fresh locals.

            // A timeline page that is created next to the visible one and removed before its posts
            // load still reports its list position, which Compose has reset to 0,0 on an empty list.
            // Saving that clobbers the real position in both the persistent store and X's in-memory
            // map, so the hook only saves while the timeline state is Success. The save method reads
            // that state itself: `iget stateFlow; check-cast; getValue; move-result; check-cast State`.
            val stateRead =
                requireExactlyOne(
                    "NewX save-scroll-position timeline state read",
                    saveMethod.instructions.indices.mapNotNull { index ->
                        val flowRead = saveMethod.instructions[index]
                        if (flowRead.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
                        val flowField = flowRead.getReference<FieldReference>() ?: return@mapNotNull null
                        if (flowField.definingClass != componentDescriptor) return@mapNotNull null
                        val flowCast = saveMethod.instructions.getOrNull(index + 1)
                        val getValue = saveMethod.instructions.getOrNull(index + 2)
                        val result = saveMethod.instructions.getOrNull(index + 3)
                        val stateCast = saveMethod.instructions.getOrNull(index + 4)
                        if (flowCast?.opcode != Opcode.CHECK_CAST ||
                            getValue?.opcode != Opcode.INVOKE_VIRTUAL ||
                            result?.opcode != Opcode.MOVE_RESULT_OBJECT ||
                            stateCast?.opcode != Opcode.CHECK_CAST
                        ) {
                            return@mapNotNull null
                        }
                        val getValueReference = getValue.getReference<MethodReference>() ?: return@mapNotNull null
                        val flowType = flowCast.getReference<TypeReference>()?.type ?: return@mapNotNull null
                        if (getValueReference.definingClass != flowType ||
                            getValueReference.name != "getValue" ||
                            getValueReference.parameterTypes.isNotEmpty() ||
                            getValueReference.returnType != OBJECT_DESCRIPTOR
                        ) {
                            return@mapNotNull null
                        }
                        val stateType = stateCast.getReference<TypeReference>()?.type ?: return@mapNotNull null
                        if (!stateType.startsWith("Lcom/x/urt/")) return@mapNotNull null
                        Triple(flowField, getValueReference, stateType)
                    }.distinctBy { (field, getter, state) -> "$field|$getter|$state" },
                )
            val (stateFlowField, stateGetValueReference, stateInterface) = stateRead
            val timelineTypeDescriptor = timelineGetterReference.returnType
            // Success is the one state checked in this method that carries the timeline type.
            val successStateType =
                requireExactlyOne(
                    "NewX timeline Success state",
                    saveMethod.instructions.mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.INSTANCE_OF) return@mapNotNull null
                        instruction.getReference<TypeReference>()?.type
                    }.distinct().filter { stateType ->
                        // Platform interfaces (for example Collection) are not APK-defined state classes.
                        val stateClass = classDefByOrNull(stateType) ?: return@filter false
                        stateClass.interfaces.contains(stateInterface) &&
                            stateClass.fields.any { field ->
                                !AccessFlags.STATIC.isSet(field.accessFlags) &&
                                    field.type == timelineTypeDescriptor
                            }
                    },
                )

            val successItemsField =
                requireExactlyOne(
                    "NewX timeline Success items field",
                    mutableClassDefBy(successStateType).fields.filter { field ->
                        !AccessFlags.STATIC.isSet(field.accessFlags) &&
                            isAssignableTo(field.type, LIST_DESCRIPTOR)
                    },
                )
            if (!AccessFlags.PUBLIC.isSet(successItemsField.accessFlags)) {
                throw PatchException("NewX timeline Success items field is not public: $successItemsField")
            }

            val resumePositionMatch =
                requireExactlyOne(
                    "NewX TimelineResumePosition class",
                    Fingerprint(
                        definingClass = "Lcom/x/urt/",
                        name = "toString",
                        parameters = emptyList(),
                        returnType = STRING_DESCRIPTOR,
                        strings = listOf("TimelineResumePosition(entryId="),
                    ).scopedMatchAll(),
                )
            val resumePositionDescriptor = resumePositionMatch.originalClassDef.type
            val resumePositionClass = mutableClassDefBy(resumePositionDescriptor)
            val resumeEntryIdField =
                requireExactlyOne(
                    "NewX TimelineResumePosition entry-id field",
                    resumePositionClass.fields.filter { field -> field.type == STRING_DESCRIPTOR },
                )
            val resumeSortIndexField =
                requireExactlyOne(
                    "NewX TimelineResumePosition sort-index field",
                    resumePositionClass.fields.filter { field -> field.type == "J" },
                )
            listOf(resumeEntryIdField, resumeSortIndexField).forEach { field ->
                if (!AccessFlags.PUBLIC.isSet(field.accessFlags)) {
                    throw PatchException("NewX TimelineResumePosition field is not public: $field")
                }
            }
            val resumeEntryIdFieldReference =
                fieldReference("$resumePositionDescriptor->${resumeEntryIdField.name}:$STRING_DESCRIPTOR")
            val resumeSortIndexFieldReference =
                fieldReference("$resumePositionDescriptor->${resumeSortIndexField.name}:J")

            val factoryMethodCandidates =
                saveMethod.instructions.withIndex().filter { indexedInstruction ->
                    if (indexedInstruction.value.opcode != Opcode.INVOKE_STATIC &&
                        indexedInstruction.value.opcode != Opcode.INVOKE_STATIC_RANGE
                    ) {
                        return@filter false
                    }
                    val reference = indexedInstruction.value.getReference<MethodReference>()
                        ?: return@filter false
                    reference.parameterTypes.map(CharSequence::toString) ==
                        listOf(
                            successItemsField.type,
                            ARRAY_LIST_DESCRIPTOR,
                            holderDescriptor,
                            INSTANT_DESCRIPTOR,
                        ) && reference.returnType == resumePositionDescriptor
                }
            val factoryCall = requireAtMostOne("NewX timeline resume-position factory call", factoryMethodCandidates)
            val (factoryReference, clockNowReference) = if (factoryCall != null) {
                val reference = factoryCall.value.getReference<MethodReference>()
                    ?: throw PatchException("NewX timeline resume-position factory has no method reference")
                val arguments = factoryCall.value.invokeArgumentRegisters()
                if (arguments.size != 4) {
                    throw PatchException("NewX timeline resume-position factory has unexpected argument registers: $arguments")
                }
                val result = saveMethod.instructions.getOrNull(factoryCall.index - 1)
                if (result?.opcode != Opcode.MOVE_RESULT_OBJECT ||
                    (result as? OneRegisterInstruction)?.registerA != arguments[3]
                ) throw PatchException("NewX timeline resume-position factory's Instant argument is not the preceding invoke result")
                val clockInstruction = saveMethod.instructions.getOrNull(factoryCall.index - 2)
                val clock = clockInstruction?.getReference<MethodReference>()
                    ?: throw PatchException("NewX timeline clock now() call was not found before the factory")
                if (clockInstruction.opcode != Opcode.INVOKE_INTERFACE || clock.name != "now" ||
                    clock.parameterTypes.isNotEmpty() || clock.returnType != INSTANT_DESCRIPTOR
                ) throw PatchException("Unexpected NewX timeline clock now() call: $clock")
                reference to clock
            } else {
                val extracted = resolveInlineResumePositionFactory(
                    saveMethod,
                    listOf(successItemsField.type, ARRAY_LIST_DESCRIPTOR, holderDescriptor, INSTANT_DESCRIPTOR),
                    resumePositionDescriptor,
                    successItemsField,
                    saveHolderRegister,
                )
                if (componentClass.methods.any { it.name == extracted.method.name }) {
                    throw PatchException("NewX timeline component already has ${extracted.method.name}")
                }
                componentClass.methods.add(extracted.method)
                extracted.method to extracted.clock
            }
            val clockFieldDefinition =
                requireExactlyOne(
                    "NewX timeline clock field definition",
                    componentClass.fields.filter { field ->
                        !AccessFlags.STATIC.isSet(field.accessFlags) &&
                            field.type == clockNowReference.definingClass
                    },
                )

            val postLoadCandidates =
                componentClass.methods.filter { method ->
                    !AccessFlags.STATIC.isSet(method.accessFlags) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType == holderDescriptor &&
                        method.implementation != null &&
                        resolveResumePositionValidation(method, resumePositionDescriptor, ::classDefByOrNull) != null
                }
            val postLoadMethod = requireExactlyOne("NewX timeline post-load holder method", postLoadCandidates)

            val componentInterfaceDescriptor =
                requireExactlyOne(
                    "NewX timeline component interface",
                    componentClass.interfaces.map { it }.filter { interfaceDescriptor ->
                        val interfaceDefinition = mutableClassDefBy(interfaceDescriptor)
                        AccessFlags.INTERFACE.isSet(interfaceDefinition.accessFlags) &&
                            interfaceDefinition.methods.any { method ->
                                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                                    method.name == getterMethod.name &&
                                    method.parameterTypes.isEmpty() &&
                                    method.returnType == holderDescriptor
                            } &&
                            interfaceDefinition.methods.any { method ->
                                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                                    method.name == postLoadMethod.name &&
                                    method.parameterTypes.isEmpty() &&
                                    method.returnType == holderDescriptor
                            }
                    },
                )
            val componentInterface = mutableClassDefBy(componentInterfaceDescriptor)
            val interfaceGetterMethod =
                requireExactlyOne(
                    "NewX timeline component interface getter",
                    componentInterface.methods.filter { method ->
                        !AccessFlags.STATIC.isSet(method.accessFlags) &&
                            method.name == getterMethod.name &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType == holderDescriptor
                    },
                )
            val interfacePostLoadMethod =
                requireExactlyOne(
                    "NewX timeline component interface post-load method",
                    componentInterface.methods.filter { method ->
                        !AccessFlags.STATIC.isSet(method.accessFlags) &&
                            method.name == postLoadMethod.name &&
                            method.parameterTypes.isEmpty() &&
                        method.returnType == holderDescriptor
                    },
                )

            val postLoadScrollCallSites =
                classDefFlatMap { classDef ->
                    val candidates = mutableListOf<PostLoadScrollCallSite>()
                    classDef.methods.forEach methodLoop@{ method ->
                        val implementation = method.implementation ?: return@methodLoop
                        val instructions = implementation.instructions.toList()
                        instructions.forEachIndexed instructionLoop@{ postLoadInvokeIndex, instruction ->
                            if (instruction.opcode != Opcode.INVOKE_INTERFACE &&
                                instruction.opcode != Opcode.INVOKE_INTERFACE_RANGE
                            ) {
                                return@instructionLoop
                            }
                            val postLoadReference = instruction.getReference<MethodReference>()
                                ?: return@instructionLoop
                            if (postLoadReference.definingClass != componentInterfaceDescriptor ||
                                postLoadReference.name != interfacePostLoadMethod.name ||
                                postLoadReference.parameterTypes.isNotEmpty() ||
                                postLoadReference.returnType != holderDescriptor
                            ) {
                                return@instructionLoop
                            }

                            val moveResultIndex = postLoadInvokeIndex + 1
                            val moveResult = instructions.getOrNull(moveResultIndex) as? OneRegisterInstruction
                                ?: return@instructionLoop
                            if (instructions[moveResultIndex].opcode != Opcode.MOVE_RESULT_OBJECT) {
                                return@instructionLoop
                            }
                            // Instruction order is the contract: the first invoke after u() must be i(II)V.
                            var firstInvokeIndex = -1
                            for (index in moveResultIndex + 1 until instructions.size) {
                                if (instructions[index].opcode.isInvokeInstruction()) {
                                    firstInvokeIndex = index
                                    break
                                }
                            }
                            if (firstInvokeIndex < 0) return@instructionLoop
                            val scrollInvoke = instructions[firstInvokeIndex]
                            if (scrollInvoke.opcode != Opcode.INVOKE_VIRTUAL &&
                                scrollInvoke.opcode != Opcode.INVOKE_VIRTUAL_RANGE
                            ) {
                                return@instructionLoop
                            }
                            val scrollReference = scrollInvoke.getReference<MethodReference>()
                                ?: return@instructionLoop
                            if (scrollReference.name != "i" ||
                                scrollReference.parameterTypes.map(CharSequence::toString) != listOf("I", "I") ||
                                scrollReference.returnType != "V"
                            ) {
                                return@instructionLoop
                            }
                            val scrollArguments = runCatching { scrollInvoke.invokeArgumentRegisters() }
                                .getOrNull() ?: return@instructionLoop
                            if (scrollArguments.size != 3) return@instructionLoop

                            val holderRegister = moveResult.registerA
                            val holderLoads =
                                (moveResultIndex + 1 until firstInvokeIndex).mapNotNull { index ->
                                    val holderLoad = instructions[index]
                                    if (holderLoad.opcode != Opcode.IGET) return@mapNotNull null
                                    val field = holderLoad.getReference<FieldReference>()
                                        ?: return@mapNotNull null
                                    val registers = holderLoad as? TwoRegisterInstruction
                                        ?: return@mapNotNull null
                                    if (field.definingClass != holderDescriptor ||
                                        field.type != "I" ||
                                        registers.registerB != holderRegister
                                    ) {
                                        return@mapNotNull null
                                    }
                                    registers.registerA
                                }
                            if (holderLoads.size != 2 ||
                                holderLoads.toSet() != scrollArguments.drop(1).toSet() ||
                                scrollArguments.drop(1).toSet().size != 2
                            ) {
                                return@instructionLoop
                            }

                            candidates.add(
                                PostLoadScrollCallSite(
                                    methodOwner = classDef.type,
                                    methodName = method.name,
                                    methodParameters = method.parameterTypes.map(CharSequence::toString),
                                    methodReturnType = method.returnType,
                                    postLoadInvokeIndex = postLoadInvokeIndex,
                                    scrollInvokeIndex = firstInvokeIndex,
                                    moveResultRegister = holderRegister,
                                    scrollStateType = scrollReference.definingClass,
                                    scrollStateRegister = scrollArguments.first(),
                                ),
                            )
                        }
                    }
                    candidates
                }
            val postLoadScrollCallSite =
                requireExactlyOne(
                    "NewX timeline post-load LazyListState scroll call site",
                    postLoadScrollCallSites,
                )
            if (postLoadScrollCallSite.scrollStateRegister == postLoadScrollCallSite.moveResultRegister) {
                throw PatchException(
                    "NewX post-load LazyListState receiver aliases the u() holder result register: " +
                        postLoadScrollCallSite,
                )
            }
            val postLoadCallSiteMethod =
                requireExactlyOne(
                    "NewX timeline post-load call-site method",
                    mutableClassDefBy(postLoadScrollCallSite.methodOwner).methods.filter { method ->
                        method.name == postLoadScrollCallSite.methodName &&
                            method.parameterTypes.map(CharSequence::toString) ==
                                postLoadScrollCallSite.methodParameters &&
                            method.returnType == postLoadScrollCallSite.methodReturnType
                    },
                )
            val postLoadCallSiteImplementation = postLoadCallSiteMethod.implementation
                ?: throw PatchException("NewX timeline post-load call site has no implementation")
            val postLoadCallSiteInstructions = postLoadCallSiteImplementation.instructions
            val overwrittenScrollStateRegisters =
                (postLoadScrollCallSite.postLoadInvokeIndex + 1 until postLoadScrollCallSite.scrollInvokeIndex)
                    .filter { index ->
                        postLoadCallSiteInstructions[index].destinationRegisterOrNull() ==
                            postLoadScrollCallSite.scrollStateRegister
                    }
            if (overwrittenScrollStateRegisters.isNotEmpty()) {
                throw PatchException(
                    "NewX post-load LazyListState receiver v${postLoadScrollCallSite.scrollStateRegister} " +
                        "is overwritten between u() and i(II)V at instructions " +
                        overwrittenScrollStateRegisters.joinToString(),
                )
            }

            val rowKeyMatch =
                requireExactlyOne(
                    "NewX RegularItem row-key class",
                    Fingerprint(
                        definingClass = "Lcom/x/urt/",
                        name = "toString",
                        parameters = emptyList(),
                        returnType = STRING_DESCRIPTOR,
                        strings = listOf("RegularItem(entryId="),
                    ).scopedMatchAll(),
                )
            val rowKeyDescriptor = rowKeyMatch.originalClassDef.type
            val rowKeyConstructor =
                requireExactlyOne(
                    "NewX RegularItem row-key constructor",
                    mutableClassDefBy(rowKeyDescriptor).methods.filter { method ->
                        method.name == "<init>" &&
                            method.parameterTypes.map(CharSequence::toString) == listOf(STRING_DESCRIPTOR, "J") &&
                            method.returnType == "V"
                    },
                )
            val rowKeyConstructorReference =
                methodReference("$rowKeyDescriptor->${rowKeyConstructor.name}(${STRING_DESCRIPTOR}J)V")

            val scrollStateClass = mutableClassDefBy(postLoadScrollCallSite.scrollStateType)
            val scrollPositionCandidates =
                scrollStateClass.fields.mapNotNull { scrollPositionField ->
                    if (AccessFlags.STATIC.isSet(scrollPositionField.accessFlags) ||
                        !scrollPositionField.type.startsWith("L")
                    ) {
                        return@mapNotNull null
                    }
                    val positionClass =
                        runCatching { mutableClassDefBy(scrollPositionField.type) }.getOrNull()
                            ?: return@mapNotNull null
                    val keyFields =
                        positionClass.fields.filter { field ->
                            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                                field.type == OBJECT_DESCRIPTOR
                        }
                    if (keyFields.size != 1) return@mapNotNull null
                    scrollPositionField to
                        requireExactlyOne(
                            "NewX Compose remembered-key field in ${scrollPositionField.type}",
                            keyFields,
                        )
                }
            val (scrollPositionField, scrollPositionKeyField) =
                requireExactlyOne(
                    "NewX LazyListState scroll-position and remembered-key fields",
                    scrollPositionCandidates,
                )
            if (!AccessFlags.PUBLIC.isSet(scrollPositionField.accessFlags)) {
                throw PatchException("NewX LazyListState scroll-position field is not public: $scrollPositionField")
            }
            if (!AccessFlags.PUBLIC.isSet(scrollPositionKeyField.accessFlags)) {
                throw PatchException("NewX Compose scroll-position key field is not public: $scrollPositionKeyField")
            }
            if (AccessFlags.FINAL.isSet(scrollPositionKeyField.accessFlags)) {
                throw PatchException("NewX Compose scroll-position key field is final: $scrollPositionKeyField")
            }
            val scrollPositionFieldReference =
                fieldReference(
                    "${postLoadScrollCallSite.scrollStateType}->" +
                        "${scrollPositionField.name}:${scrollPositionField.type}",
                )
            val scrollPositionKeyFieldReference =
                fieldReference(
                    "${scrollPositionField.type}->${scrollPositionKeyField.name}:${OBJECT_DESCRIPTOR}",
                )

            if (AccessFlags.ABSTRACT.isSet(interfacePostLoadMethod.accessFlags) ||
                interfacePostLoadMethod.implementation == null
            ) {
                throw PatchException(
                    "NewX timeline component interface post-load method is not a default method: " +
                        "$componentInterfaceDescriptor->$interfacePostLoadMethod",
                )
            }

            val delegatingTimelineWrappers =
                classDefFlatMap { classDef ->
                    val classDescriptor = classDef.type
                    if (classDescriptor == componentDescriptor ||
                        classDef.interfaces.none { it.toString() == componentInterfaceDescriptor }
                    ) {
                        return@classDefFlatMap emptyList()
                    }
                    if (classDef.methods.any { method ->
                            method.name == postLoadMethod.name &&
                                method.parameterTypes.isEmpty() &&
                                method.returnType == holderDescriptor
                        }
                    ) {
                        return@classDefFlatMap emptyList()
                    }

                    val wrapperGetterMethods =
                        classDef.methods.filter { method ->
                            !AccessFlags.STATIC.isSet(method.accessFlags) &&
                                method.name == getterMethod.name &&
                                method.parameterTypes.isEmpty() &&
                                method.returnType == holderDescriptor
                        }
                    if (wrapperGetterMethods.isEmpty()) return@classDefFlatMap emptyList()

                    val wrapperGetter =
                        requireExactlyOne(
                            "NewX timeline wrapper getter in $classDescriptor",
                            wrapperGetterMethods,
                        )
                    val implementation = wrapperGetter.implementation ?: return@classDefFlatMap emptyList()
                    if (AccessFlags.ABSTRACT.isSet(wrapperGetter.accessFlags)) return@classDefFlatMap emptyList()
                    val instructions = implementation.instructions.toList()
                    if (instructions.size != 5) return@classDefFlatMap emptyList()

                    val p0Register = implementation.registerCount - 1
                    val delegateInvoke = instructions[0]
                    if (delegateInvoke.opcode != Opcode.INVOKE_VIRTUAL &&
                        delegateInvoke.opcode != Opcode.INVOKE_DIRECT
                    ) {
                        return@classDefFlatMap emptyList()
                    }
                    val delegateInvokeRegisters = delegateInvoke as? FiveRegisterInstruction
                        ?: return@classDefFlatMap emptyList()
                    if (delegateInvokeRegisters.registerCount != 1 ||
                        delegateInvokeRegisters.registerC != p0Register
                    ) {
                        return@classDefFlatMap emptyList()
                    }
                    val delegateGetter = delegateInvoke.getReference<MethodReference>()
                        ?: return@classDefFlatMap emptyList()
                    if (delegateGetter.parameterTypes.isNotEmpty() ||
                        delegateGetter.returnType != componentInterfaceDescriptor
                    ) {
                        return@classDefFlatMap emptyList()
                    }

                    val delegateResult = instructions[1] as? OneRegisterInstruction
                        ?: return@classDefFlatMap emptyList()
                    if (instructions[1].opcode != Opcode.MOVE_RESULT_OBJECT) {
                        return@classDefFlatMap emptyList()
                    }
                    val interfaceInvoke = instructions[2]
                    if (interfaceInvoke.opcode != Opcode.INVOKE_INTERFACE) return@classDefFlatMap emptyList()
                    val interfaceInvokeRegisters = interfaceInvoke as? FiveRegisterInstruction
                        ?: return@classDefFlatMap emptyList()
                    if (interfaceInvokeRegisters.registerCount != 1 ||
                        interfaceInvokeRegisters.registerC != delegateResult.registerA
                    ) {
                        return@classDefFlatMap emptyList()
                    }
                    val interfaceGetter = interfaceInvoke.getReference<MethodReference>()
                        ?: return@classDefFlatMap emptyList()
                    if (interfaceGetter.definingClass != componentInterfaceDescriptor ||
                        interfaceGetter.name != interfaceGetterMethod.name ||
                        interfaceGetter.parameterTypes.isNotEmpty() ||
                        interfaceGetter.returnType != holderDescriptor
                    ) {
                        return@classDefFlatMap emptyList()
                    }

                    val getterResult = instructions[3] as? OneRegisterInstruction
                        ?: return@classDefFlatMap emptyList()
                    val returnInstruction = instructions[4] as? OneRegisterInstruction
                        ?: return@classDefFlatMap emptyList()
                    if (instructions[3].opcode != Opcode.MOVE_RESULT_OBJECT ||
                        instructions[4].opcode != Opcode.RETURN_OBJECT ||
                        returnInstruction.registerA != getterResult.registerA
                    ) {
                        return@classDefFlatMap emptyList()
                    }

                    listOf(DelegatingTimelineWrapper(classDescriptor, delegateGetter, delegateInvoke.opcode))
                }
            if (delegatingTimelineWrappers.isEmpty()) {
                throw PatchException(
                    "NewX delegating timeline wrappers missing the post-load override were not found; " +
                        "matching wrappers: none",
                )
            }
            val componentPostLoadReference =
                methodReference("$componentInterfaceDescriptor->${postLoadMethod.name}()$holderDescriptor")

            if (componentClass.methods.any { method -> method.name == APPLY_ANCHOR_KEY_BRIDGE_NAME }) {
                throw PatchException(
                    "NewX timeline component already has $APPLY_ANCHOR_KEY_BRIDGE_NAME: $componentDescriptor",
                )
            }
            val applyAnchorKeyBridge =
                MutableMethod(
                    ImmutableMethod(
                        componentDescriptor,
                        APPLY_ANCHOR_KEY_BRIDGE_NAME,
                        listOf(ImmutableMethodParameter(postLoadScrollCallSite.scrollStateType, emptySet(), null)),
                        "V",
                        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNTHETIC.value,
                        emptySet(),
                        emptySet(),
                        MutableMethodImplementation(6),
                    ),
                )
            componentClass.methods.add(applyAnchorKeyBridge)
            applyAnchorKeyBridge.insertHook(0, relocateBranchTargets = false) {
                invokeStatic(
                    methodReference(
                        "$TIMELINE_POSITION_STORE_DESCRIPTOR->takeStagedAnchorEntryId()$STRING_DESCRIPTOR",
                    ),
                )
                moveResult(0, STRING_DESCRIPTOR)
                ifEqz(0, Target.Local(APPLY_ANCHOR_KEY_NULL_LABEL))
                invokeStatic(
                    methodReference("$TIMELINE_POSITION_STORE_DESCRIPTOR->stagedAnchorSortIndex()J"),
                )
                moveResult(1, "J")
                newInstance(3, rowKeyDescriptor)
                invokeDirect(rowKeyConstructorReference, 3, 0, 1, 2)
                iget(4, applyAnchorKeyBridge.p0Register, scrollPositionFieldReference)
                iput(3, 4, scrollPositionKeyFieldReference)
                label(APPLY_ANCHOR_KEY_NULL_LABEL)
                returnVoid()
            }

            // i(II) is requestScrollToItem: it forces a remeasure but forgets Compose's first-item key. Writing the
            // staged key right after it puts the key in place for that forced measure, which relocates to its row.
            // Writing it without a scroll request left the list at the raw index until something else remeasured.
            // The u() hook stages the key in this call stack; i(II) only runs when u() returned a position.
            postLoadCallSiteMethod.insertHook(
                index = postLoadScrollCallSite.scrollInvokeIndex + 1,
                relocateBranchTargets = false,
            ) {
                invokeStatic(
                    methodReference(
                        "$componentDescriptor->$APPLY_ANCHOR_KEY_BRIDGE_NAME(" +
                            "${postLoadScrollCallSite.scrollStateType})V",
                    ),
                    postLoadScrollCallSite.scrollStateRegister,
                )
            }

            // The interface default returns null, so wrappers without an override hide the component's post-load restore.
            delegatingTimelineWrappers.forEach { wrapper ->
                val wrapperClass = mutableClassDefBy(wrapper.classDescriptor)
                if (wrapperClass.methods.any { method ->
                        method.name == postLoadMethod.name &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType == holderDescriptor
                    }
                ) {
                    throw PatchException(
                        "NewX timeline wrapper already declares the post-load method: ${wrapper.classDescriptor}",
                    )
                }
                val wrapperPostLoadBridge =
                    MutableMethod(
                        ImmutableMethod(
                            wrapper.classDescriptor,
                            postLoadMethod.name,
                            emptyList(),
                            holderDescriptor,
                            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                            emptySet(),
                            emptySet(),
                            MutableMethodImplementation(2),
                        ),
                    )
                wrapperClass.methods.add(wrapperPostLoadBridge)
                wrapperPostLoadBridge.insertHook(0, relocateBranchTargets = false) {
                    when (wrapper.delegateGetterOpcode) {
                        Opcode.INVOKE_VIRTUAL ->
                            invokeVirtual(wrapper.delegateGetter, wrapperPostLoadBridge.p0Register)
                        Opcode.INVOKE_DIRECT ->
                            invokeDirect(wrapper.delegateGetter, wrapperPostLoadBridge.p0Register)
                        else ->
                            throw PatchException(
                                "Unsupported NewX timeline wrapper delegate invocation: " +
                                    "${wrapper.delegateGetterOpcode} in ${wrapper.classDescriptor}",
                            )
                    }
                    moveResult(0, componentInterfaceDescriptor)
                    invokeInterface(componentPostLoadReference, 0)
                    moveResult(0, holderDescriptor)
                    returnObject(0)
                }
            }

            val validation = resolveResumePositionValidation(postLoadMethod, resumePositionDescriptor, ::classDefByOrNull)
                ?: throw PatchException("NewX timeline post-load method lost its resume validator: $postLoadMethod")
            val validatorItemIndexFieldReference = validation.itemIndexField
            val validatorItemIndexField =
                requireExactlyOne(
                    "NewX timeline resume-position item-index field definition",
                    resumePositionClass.fields.filter { field ->
                        field.toString() == validatorItemIndexFieldReference.toString()
                    },
                )
            if (!AccessFlags.PUBLIC.isSet(validatorItemIndexField.accessFlags)) {
                throw PatchException("NewX TimelineResumePosition item-index field is not public: $validatorItemIndexField")
            }
            val matcherReference = validation.matcher
            val itemDescriptor = matcherReference.parameterTypes.first().toString()
            if (classDefByOrNull(itemDescriptor)?.accessFlags?.let(AccessFlags.INTERFACE::isSet) != true) {
                throw PatchException("NewX TimelineItem matcher no longer takes an interface: $matcherReference")
            }
            requireExactlyOne(
                "NewX TimelineItem matcher method definition",
                mutableClassDefBy(matcherReference.definingClass).methods.filter { method ->
                    AccessFlags.STATIC.isSet(method.accessFlags) &&
                        method.name == matcherReference.name &&
                        method.parameterTypes.map(CharSequence::toString) ==
                            matcherReference.parameterTypes.map(CharSequence::toString) &&
                        method.returnType == "Z"
                },
            )

            val eventHolderLoad =
                requireExactlyOne(
                    "NewX save event holder load",
                    saveMethod.instructions.withIndex().filter { indexedInstruction ->
                        if (indexedInstruction.index >= mapPutIndex ||
                            indexedInstruction.value.opcode != Opcode.IGET_OBJECT
                        ) {
                            return@filter false
                        }
                        val field = indexedInstruction.value.getReference<FieldReference>() ?: return@filter false
                        val registers = indexedInstruction.value as? TwoRegisterInstruction ?: return@filter false
                        field.type == holderDescriptor &&
                            registers.registerA == saveHolderRegister
                    },
                )
            val eventHolderField =
                eventHolderLoad.value.getReference<FieldReference>()
                    ?: throw PatchException("NewX save event holder load has no field reference")
            val eventType = eventHolderField.definingClass
            val eventRegister = (eventHolderLoad.value as TwoRegisterInstruction).registerB
            val eventHolderFieldDefinition =
                requireExactlyOne(
                    "NewX save event holder field definition",
                    mutableClassDefBy(eventType).fields.filter { field ->
                        field.toString() == eventHolderField.toString()
                    },
                )
            if (!AccessFlags.PUBLIC.isSet(eventHolderFieldDefinition.accessFlags)) {
                throw PatchException("NewX save event holder field is not public: $eventHolderField")
            }
            val eventRowsField =
                requireExactlyOne(
                    "NewX save event visible-rows field",
                    mutableClassDefBy(eventType).fields.filter { field ->
                        !AccessFlags.STATIC.isSet(field.accessFlags) &&
                            field.type == ARRAY_LIST_DESCRIPTOR
                    },
                )
            if (!AccessFlags.PUBLIC.isSet(eventRowsField.accessFlags)) {
                throw PatchException("NewX save event visible-rows field is not public: $eventRowsField")
            }
            val eventRegisterWrite =
                (eventHolderLoad.index + 1 until mapPutIndex).firstOrNull { index ->
                    saveMethod.instructions[index].destinationRegisterOrNull() == eventRegister
                }
            if (eventRegisterWrite != null) {
                throw PatchException(
                    "NewX save event register v$eventRegister is overwritten before map put at instruction $eventRegisterWrite",
                )
            }

            // Skipping the write must not skip the rest of the save method: 12.34 keeps working on the
            // timeline state after the map put. The skip branch targets the instruction after the put,
            // which is only correct when nothing consumes the put's result.
            val resultOpcode = saveMethod.instructions.getOrNull(mapPutIndex + 1)?.opcode
            if (resultOpcode != null && resultOpcode in MOVE_RESULT_OPCODES) {
                throw PatchException("NewX timeline-position map write has result handling; the skip target would drop it")
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
                    field.type == "Z" &&
                        field.definingClass.startsWith("Landroidx/compose/foundation/layout/") &&
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
            // The put's map and key are live until the put, so they are spilled into fresh locals and reused as
            // 4-bit scratch. The holder is only read, so it stays in place. Every path restores the map and key
            // before the put, and the not-loaded path then skips only the put.
            val spilledMap = saveLocalBase
            val spilledKey = saveLocalBase + 1
            val loadedFlag = saveLocalBase + 2
            val timelineArgument = saveLocalBase + 3
            val identityArgument = saveLocalBase + 4
            val accountIdArgument = saveLocalBase + 5
            val indexArgument = saveLocalBase + 7
            val offsetArgument = saveLocalBase + 8
            if (componentClass.methods.any { method -> method.name == SAVE_ANCHOR_BRIDGE_NAME }) {
                throw PatchException("NewX timeline component already has $SAVE_ANCHOR_BRIDGE_NAME: $componentDescriptor")
            }
            val saveAnchorBridge =
                MutableMethod(
                    ImmutableMethod(
                        componentDescriptor,
                        SAVE_ANCHOR_BRIDGE_NAME,
                        listOf(ImmutableMethodParameter(eventType, emptySet(), null)),
                        "V",
                        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value or AccessFlags.SYNTHETIC.value,
                        emptySet(),
                        emptySet(),
                        MutableMethodImplementation(16),
                    ),
                )
            componentClass.methods.add(saveAnchorBridge)
            saveAnchorBridge.insertHook(0, relocateBranchTargets = false) {
                iget(4, saveAnchorBridge.p0Register, stateFlowField)
                checkCast(4, stateGetValueReference.definingClass)
                invokeVirtual(stateGetValueReference, 4)
                moveResult(4, OBJECT_DESCRIPTOR)
                instanceOf(5, 4, successStateType)
                ifEqz(5, Target.Local(SAVE_ANCHOR_RETURN_LABEL))
                checkCast(4, successStateType)
                iget(
                    4,
                    4,
                    fieldReference(
                        "$successStateType->${successItemsField.name}:${successItemsField.type}",
                    ),
                )
                move(12, saveAnchorBridge.p0Register, componentDescriptor)
                iget(12, 12, repositoryField)
                invokeInterface(timelineGetterReference, 12)
                moveResult(0, timelineTypeDescriptor)
                invokeInterface(methodReference(timelineIdentityGetterReference), 12)
                moveResult(1, timelineIdentityDescriptor)
                iget(1, 1, timelineIdentityField)
                invokeVirtual(
                    methodReference("$componentDescriptor->$ACCOUNT_ID_BRIDGE_NAME()J"),
                    saveAnchorBridge.p0Register,
                )
                moveResult(2, "J")
                // Keep the native map save and extension account tracking intact, but do not
                // build an anchor for a disabled/unsupported persistence scope.
                invokeStatic(methodReference(SHOULD_SAVE_ANCHOR_DESCRIPTOR), 0, 2, 3)
                moveResult(5, "Z")
                ifEqz(5, Target.Local(SAVE_ANCHOR_RETURN_LABEL))
                iget(
                    8,
                    saveAnchorBridge.p0Register + 1,
                    fieldReference("$eventType->${eventRowsField.name}:$ARRAY_LIST_DESCRIPTOR"),
                )
                iget(9, saveAnchorBridge.p0Register + 1, eventHolderField)
                iget(10, saveAnchorBridge.p0Register, clockFieldDefinition)
                invokeInterface(clockNowReference, 10)
                moveResult(10, INSTANT_DESCRIPTOR)
                invokeStatic(factoryReference, 4, 8, 9, 10)
                moveResult(11, resumePositionDescriptor)
                ifEqz(11, Target.Local(SAVE_ANCHOR_NULL_LABEL))
                iget(4, 11, resumeEntryIdFieldReference)
                iget(5, 11, resumeSortIndexFieldReference)
                iget(7, 11, validatorItemIndexFieldReference)
                goto(Target.Local(SAVE_ANCHOR_SUCCESS_LABEL))
                label(SAVE_ANCHOR_NULL_LABEL)
                constInt(4, 0)
                constLong(5, 0L)
                constInt(7, 0)
                label(SAVE_ANCHOR_SUCCESS_LABEL)
                invokeStatic(methodReference(SAVE_ANCHOR_DESCRIPTOR), 0, 1, 2, 3, 4, 5, 6, 7)
                label(SAVE_ANCHOR_RETURN_LABEL)
                returnVoid()
            }
            saveMethod.insertHook(
                index = mapPutIndex,
                // Plain insertion used to leave an incoming branch on the map write, which keeps skipping
                // the hook exactly as before.
                relocateBranchTargets = false,
            ) {
                move(spilledMap, saveMapRegister, OBJECT_DESCRIPTOR)
                move(spilledKey, saveTimelineRegister, OBJECT_DESCRIPTOR)
                move(saveMapRegister, saveMethod.p0Register, OBJECT_DESCRIPTOR)
                iget(saveMapRegister, saveMapRegister, stateFlowField)
                checkCast(saveMapRegister, stateGetValueReference.definingClass)
                invokeVirtual(stateGetValueReference, saveMapRegister)
                moveResult(saveMapRegister, OBJECT_DESCRIPTOR)
                instanceOf(saveTimelineRegister, saveMapRegister, successStateType)
                move(loadedFlag, saveTimelineRegister, "Z")
                // Not loaded: jump past the persistent store, to the restore below.
                ifEqz(loadedFlag, Target.Local(SAVE_SKIPPED_LABEL))
                move(timelineArgument, spilledKey, OBJECT_DESCRIPTOR)
                move(saveMapRegister, saveMethod.p0Register, OBJECT_DESCRIPTOR)
                iget(saveMapRegister, saveMapRegister, repositoryField)
                invokeInterface(methodReference(timelineIdentityGetterReference), saveMapRegister)
                moveResult(saveMapRegister, OBJECT_DESCRIPTOR)
                iget(saveMapRegister, saveMapRegister, timelineIdentityField)
                move(identityArgument, saveMapRegister, OBJECT_DESCRIPTOR)
                invokeVirtual(
                    methodReference("$componentDescriptor->$ACCOUNT_ID_BRIDGE_NAME()J"),
                    saveMethod.p0Register,
                )
                moveResult(accountIdArgument, "J")
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
                iget(saveTimelineRegister, saveHolderRegister, positionIndexField)
                move(indexArgument, saveTimelineRegister, "I")
                iget(saveTimelineRegister, saveHolderRegister, positionOffsetField)
                move(offsetArgument, saveTimelineRegister, "I")
                // The six staged argument words are consecutive, so the call is emitted in range form.
                invokeStatic(
                    methodReference(SAVE_POSITION_DESCRIPTOR),
                    timelineArgument,
                    identityArgument,
                    accountIdArgument,
                    accountIdArgument + 1,
                    indexArgument,
                    offsetArgument,
                )
                move(timelineArgument, saveMethod.p0Register, componentDescriptor)
                move(identityArgument, eventRegister, eventType)
                invokeVirtual(
                    methodReference("$componentDescriptor->$SAVE_ANCHOR_BRIDGE_NAME($eventType)V"),
                    timelineArgument,
                    identityArgument,
                )
                label(SAVE_SKIPPED_LABEL)
                move(saveMapRegister, spilledMap, OBJECT_DESCRIPTOR)
                move(saveTimelineRegister, spilledKey, OBJECT_DESCRIPTOR)
                // Not loaded: skip only the map put (the original instruction). Loaded: fall into the put.
                ifEqz(loadedFlag, Target.AfterOriginal(1))
            }

            if (componentClass.methods.any { method -> method.name == RESTORE_ANCHOR_BRIDGE_NAME }) {
                throw PatchException(
                    "NewX timeline component already has $RESTORE_ANCHOR_BRIDGE_NAME: $componentDescriptor",
                )
            }
            val restoreAnchorBridge =
                MutableMethod(
                    ImmutableMethod(
                        componentDescriptor,
                        RESTORE_ANCHOR_BRIDGE_NAME,
                        emptyList(),
                        holderDescriptor,
                        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value or AccessFlags.SYNTHETIC.value,
                        emptySet(),
                        emptySet(),
                        MutableMethodImplementation(16),
                    ),
                )
            componentClass.methods.add(restoreAnchorBridge)
            restoreAnchorBridge.insertHook(0, relocateBranchTargets = false) {
                // This hook can be retried by a Compose SideEffect. A consumed/absent custom
                // anchor must fall through to the original native restore without key work.
                invokeStatic(methodReference(HAS_PENDING_ANCHOR_DESCRIPTOR))
                moveResult(4, "Z")
                ifEqz(4, Target.Local(RESTORE_ANCHOR_NULL_LABEL))
                iget(5, restoreAnchorBridge.p0Register, repositoryField)
                invokeInterface(timelineGetterReference, 5)
                moveResult(0, timelineTypeDescriptor)
                invokeInterface(methodReference(timelineIdentityGetterReference), 5)
                moveResult(1, timelineIdentityDescriptor)
                iget(1, 1, timelineIdentityField)
                invokeVirtual(
                    methodReference("$componentDescriptor->$ACCOUNT_ID_BRIDGE_NAME()J"),
                    restoreAnchorBridge.p0Register,
                )
                moveResult(2, "J")
                invokeStatic(
                    methodReference(PENDING_ANCHOR_ENTRY_ID_DESCRIPTOR),
                    0,
                    1,
                    2,
                    3,
                )
                moveResult(4, STRING_DESCRIPTOR)
                ifEqz(4, Target.Local(RESTORE_ANCHOR_NULL_LABEL))
                iget(5, restoreAnchorBridge.p0Register, stateFlowField)
                checkCast(5, stateGetValueReference.definingClass)
                invokeVirtual(stateGetValueReference, 5)
                moveResult(5, OBJECT_DESCRIPTOR)
                instanceOf(12, 5, successStateType)
                ifEqz(12, Target.Local(RESTORE_ANCHOR_NULL_LABEL))
                checkCast(5, successStateType)
                iget(
                    6,
                    5,
                    fieldReference(
                        "$successStateType->${successItemsField.name}:${successItemsField.type}",
                    ),
                )
                invokeStatic(
                    methodReference(PENDING_ANCHOR_SORT_INDEX_DESCRIPTOR),
                    0,
                    1,
                    2,
                    3,
                )
                moveResult(7, "J")
                invokeInterface(methodReference("$LIST_DESCRIPTOR->size()I"), 6)
                moveResult(10, "I")
                constInt(9, 0)
                label(RESTORE_ANCHOR_LOOP_LABEL)
                ifGe(9, 10, Target.Local(RESTORE_ANCHOR_NOT_FOUND_LABEL))
                invokeInterface(methodReference("$LIST_DESCRIPTOR->get(I)$OBJECT_DESCRIPTOR"), 6, 9)
                moveResult(11, OBJECT_DESCRIPTOR)
                checkCast(11, itemDescriptor)
                invokeStatic(matcherReference, 11, 4, 7, 8)
                moveResult(12, "Z")
                ifNez(12, Target.Local(RESTORE_ANCHOR_CONSUME_LABEL))
                intAddLiteral8(9, 9, 1)
                goto(Target.Local(RESTORE_ANCHOR_LOOP_LABEL))
                label(RESTORE_ANCHOR_NOT_FOUND_LABEL)
                constInt(9, -1)
                goto(Target.Local(RESTORE_ANCHOR_GONE_LABEL))
                label(RESTORE_ANCHOR_CONSUME_LABEL)
                label(RESTORE_ANCHOR_GONE_LABEL)
                invokeStatic(
                    methodReference(CONSUME_ANCHOR_DESCRIPTOR),
                    0,
                    1,
                    2,
                    3,
                    9,
                )
                moveResult(13, "[I")
                ifEqz(13, Target.Local(RESTORE_ANCHOR_NULL_LABEL))
                constInt(14, 0)
                aget(9, 13, 14)
                constInt(14, 1)
                aget(10, 13, 14)
                newInstance(11, holderDescriptor)
                invokeDirect(methodReference(holderConstructorReference), 11, 9, 10)
                returnObject(11)
                label(RESTORE_ANCHOR_NULL_LABEL)
                constInt(4, 0)
                returnObject(4)
            }

            var postLoadHookMethod = postLoadMethod
            var postLoadResultRegister = 0
            if (postLoadHookMethod.p0Register == 0) {
                val originalRegisterCount =
                    postLoadHookMethod.implementation?.registerCount
                        ?: throw PatchException("NewX timeline post-load method has no implementation")
                val expandedPostLoadMethod =
                    postLoadHookMethod.cloneMutable(
                        additionalRegisters = postLoadHookMethod.numberOfParameterRegisters + 1,
                    )
                componentClass.methods.remove(postLoadHookMethod)
                componentClass.methods.add(expandedPostLoadMethod)
                postLoadHookMethod = expandedPostLoadMethod
                postLoadResultRegister = originalRegisterCount
            }
            if (postLoadResultRegister >= postLoadHookMethod.p0Register || postLoadResultRegister > 15) {
                throw PatchException(
                    "NewX timeline post-load hook has no encodable low result register in " +
                        "$postLoadHookMethod: result=v$postLoadResultRegister, p0=v${postLoadHookMethod.p0Register}",
                )
            }
            // X invokes this from a Compose SideEffect while Success. A null result leaves its
            // remembered applied flag false, so the next composition retries without consuming the anchor.
            postLoadHookMethod.insertHook(index = 0, relocateBranchTargets = false) {
                invokeVirtual(
                    methodReference(
                        "$componentDescriptor->$RESTORE_ANCHOR_BRIDGE_NAME()${holderDescriptor}",
                    ),
                    postLoadHookMethod.p0Register,
                )
                moveResult(postLoadResultRegister, holderDescriptor)
                ifEqz(postLoadResultRegister, Target.Original)
                returnObject(postLoadResultRegister)
            }
        }
    }
