package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val CONCURRENT_HASH_MAP_DESCRIPTOR = "Ljava/util/concurrent/ConcurrentHashMap;"
private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val TIMELINE_POSITION_STORE_DESCRIPTOR =
    "Lapp/morphe/extension/xlite/timeline/TimelineScrollPositionStore;"

private object XLiteScrollPositionHolderFingerprint : Fingerprint(
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
        name = "X-Lite: Restore timeline position",
        description = "Persists the timeline index and offset, then restores them after the app process restarts.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteToggle(
            id = "xlite.timeline.restore_position",
            category = Categories.TIMELINE,
            strings = settingStrings("piko_xlite_restore_timeline_position"),
            order = 150,
            defaultValue = true,
            rebootApp = true,
        )

        execute {
            val holderMatches = XLiteScrollPositionHolderFingerprint.matchAll()
            if (holderMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite scroll-position holder, found ${holderMatches.size}: " +
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
                    "Expected one X-Lite scroll-position holder constructor, found " +
                        "${holderConstructorMatches.size}: ${holderConstructorMatches.joinToString()}",
                )
            }
            val holderConstructorReference = "$holderDescriptor-><init>(II)V"

            val getterMatches = scrollPositionGetterFingerprint(holderDescriptor).matchAll()
            if (getterMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite scroll-position getter, found ${getterMatches.size}: " +
                        getterMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val getterMatch = getterMatches.single()
            var getterMethod = getterMatch.method
            val registerCount = getterMethod.implementation?.registerCount
                ?: throw PatchException("X-Lite scroll-position getter has no implementation")
            if (registerCount < 12) {
                val expandedMethod =
                    getterMethod.cloneMutable(additionalRegisters = 12 - registerCount)
                getterMatch.classDef.methods.remove(getterMethod)
                getterMatch.classDef.methods.add(expandedMethod)
                getterMethod = expandedMethod
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
            if (mapGetCandidates.size != 1) {
                throw PatchException(
                    "Expected one X-Lite timeline-position map read, found " +
                        "${mapGetCandidates.size}: ${mapGetCandidates.joinToString()}",
                )
            }
            val mapGetIndex = mapGetCandidates.single().index
            val mapGetInstruction =
                mapGetCandidates.single().value as? FiveRegisterInstruction
                    ?: throw PatchException("X-Lite timeline-position map read has an unsupported register layout")
            if (mapGetInstruction.registerCount != 2) {
                throw PatchException(
                    "Unexpected X-Lite timeline-position map read register count: " +
                        mapGetInstruction.registerCount,
                )
            }
            val timelineRegister = mapGetInstruction.registerD
            if (timelineRegister !in 0..15) {
                throw PatchException("X-Lite timeline-type register is not encodable in invoke-virtual: v$timelineRegister")
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
                    "Expected one X-Lite timeline-type result feeding the map read, found " +
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
                    ?: throw PatchException("X-Lite timeline-position map field was not found uniquely")
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
                    ?: throw PatchException("X-Lite timeline-position map owner field was not found uniquely")
            if (mapField.definingClass.toString() == componentDescriptor) {
                throw PatchException("X-Lite timeline-position map unexpectedly belongs to the component")
            }
            if (!AccessFlags.PUBLIC.isSet(
                    mutableClassDefBy(mapField.definingClass.toString()).fields.single { field ->
                        field.toString() == mapField.toString()
                    }.accessFlags,
                )
            ) {
                throw PatchException("X-Lite timeline-position map field is not public: $mapField")
            }

            val originalContinuation =
                getterMethod.instructions.getOrNull(timelineResultIndex + 1)
                    ?: throw PatchException("X-Lite scroll-position getter continuation was not found")
            val expandedRegisterCount =
                getterMethod.implementation?.registerCount
                    ?: throw PatchException("X-Lite scroll-position getter has no implementation after expansion")
            if (expandedRegisterCount < 12) {
                throw PatchException(
                    "X-Lite scroll-position getter expansion provided only " +
                        "$expandedRegisterCount registers",
                )
            }
            val freeRegisters = listOf(7, 8, 9, 10)
            val mapOwnerRegister = freeRegisters[0]
            val mapRegister = freeRegisters[1]
            val positionsRegister = freeRegisters[0]
            val indexRegister = freeRegisters[2]
            val offsetRegister = freeRegisters[3]
            getterMethod.addInstructionsWithLabels(
                timelineResultIndex + 1,
                """
                    iget-object v$mapOwnerRegister, p0, $componentField
                    iget-object v$mapRegister, v$mapOwnerRegister, $mapField
                    invoke-virtual {v$mapRegister, v$timelineRegister}, $CONCURRENT_HASH_MAP_DESCRIPTOR->get(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$positionsRegister
                    if-nez v$positionsRegister, :piko_xlite_restore_position_continue
                    invoke-static {v$timelineRegister}, $TIMELINE_POSITION_STORE_DESCRIPTOR->restore($ENUM_DESCRIPTOR)[I
                    move-result-object v$positionsRegister
                    if-eqz v$positionsRegister, :piko_xlite_restore_position_continue
                    const/4 v$indexRegister, 0x0
                    aget v$indexRegister, v$positionsRegister, v$indexRegister
                    const/4 v$offsetRegister, 0x1
                    aget v$offsetRegister, v$positionsRegister, v$offsetRegister
                    new-instance v$positionsRegister, $holderDescriptor
                    invoke-direct {v$positionsRegister, v$indexRegister, v$offsetRegister}, $holderConstructorReference
                    invoke-virtual {v$mapRegister, v$timelineRegister, v$positionsRegister}, $CONCURRENT_HASH_MAP_DESCRIPTOR->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$indexRegister
                """.trimIndent(),
                ExternalLabel("piko_xlite_restore_position_continue", originalContinuation),
            )

            val saveMatches = saveScrollPositionFingerprint(componentDescriptor, holderDescriptor).matchAll()
            if (saveMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite save-scroll-position method, found ${saveMatches.size}: " +
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
                    "Expected one X-Lite timeline-position map write, found " +
                        "${mapPutCandidates.size}: ${mapPutCandidates.joinToString()}",
                )
            }
            val mapPutIndex = mapPutCandidates.single().index
            val mapPutInstruction =
                saveMethod.instructions[mapPutIndex] as? FiveRegisterInstruction
                    ?: throw PatchException("X-Lite timeline-position map write has an unsupported register layout")
            if (mapPutInstruction.registerCount != 3) {
                throw PatchException(
                    "Unexpected X-Lite timeline-position map write register count: " +
                        mapPutInstruction.registerCount,
                )
            }
            val saveTimelineRegister = mapPutInstruction.registerD
            val saveHolderRegister = mapPutInstruction.registerE
            if (saveTimelineRegister !in 0..15 || saveHolderRegister !in 0..15) {
                throw PatchException(
                    "X-Lite timeline-position save registers are not encodable: " +
                        "v$saveTimelineRegister, v$saveHolderRegister",
                )
            }
            saveMethod.addInstruction(
                mapPutIndex,
                "invoke-static {v$saveTimelineRegister, v$saveHolderRegister}, " +
                    "$TIMELINE_POSITION_STORE_DESCRIPTOR->save(${ENUM_DESCRIPTOR}Ljava/lang/Object;)V",
            )
        }
    }
