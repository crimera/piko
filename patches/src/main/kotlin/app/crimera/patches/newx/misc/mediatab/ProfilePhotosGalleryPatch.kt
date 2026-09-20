package app.crimera.patches.newx.misc.mediatab

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.inlineactions.newXThumbnailCachePatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.crimera.patches.newx.utils.requireExactlyOne
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
private const val URT_UI_SCOPE = "Lcom/x/urt/ui/"
private const val URT_POST_SCOPE = "Lcom/x/urt/items/post/"
private const val MODELS_SCOPE = "Lcom/x/models/"
private const val TIMELINE_MODEL_SCOPE = "Lcom/x/models/timelines/"
private const val IMMUTABLE_LIST_SCOPE = "Lkotlinx/collections/immutable/"
private const val COMPOSE_LAYOUT_SCOPE = "Landroidx/compose/foundation/layout/"
private const val VIEW_INTEROP_SCOPE = "Landroidx/compose/ui/viewinterop/"
private const val PAGING_SCOPE = "Lcom/x/urt/paging/"
private const val BOTTOM_PAGING_SCOPE = "${PAGING_SCOPE}bottom/"
private const val PAGING_EVENT_HELPER = "createPagingEvent"
private const val PAGINATOR_TYPE_HELPER = "createBottomPaginatorClassName"
private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val VOID_DESCRIPTOR = "V"
private const val FLOAT_DESCRIPTOR = "F"
private const val INT_DESCRIPTOR = "I"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val LAYOUT_DIRECTION_DESCRIPTOR = "Landroidx/compose/ui/unit/m;"
private const val OBJECT_LIST_METHOD = "subList"
private const val PHOTOS_ENUM_NAME = "USER_PROFILE_PHOTOS"
private const val PADDING_SIDE_COUNT = 4
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION3 = "Lkotlin/jvm/functions/Function3;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"
private const val JAVA_LIST = "Ljava/util/List;"
// Navigation controller/destination are never hardcoded: R8 reassigns the short
// navigation names every release (jj/kj on alpha.01 became unrelated classes on
// alpha.04 while the real pair moved to sj/rj). Both are derived from the
// navigate (destination, Z) call below.
private const val GALLERY_EXTENSION = "Lapp/morphe/extension/newx/misc/ProfilePhotosGallery;"

private data class ResolvedTimelineType(
    val descriptor: String,
    val photosField: String,
)

private data class ResolvedComposeContracts(
    val paddingValuesDescriptor: String,
    val paddingTop: Method,
    val paddingBottom: Method,
    val androidView: Method,
)
private data class ResolvedPagingEvent(
    val eventType: String,
    val constructorReference: String,
    val paginatorClassName: String,
)


private fun Method.parameterDescriptors(): List<String> =
    parameterTypes.map(CharSequence::toString)

private fun isDirectDescriptorInScope(type: String, scope: String): Boolean =
    type.startsWith(scope) &&
        !type.removePrefix(scope).removeSuffix(";").contains("/")

private fun FieldReference.toSmaliDescriptor(): String =
    "${definingClass}->${name}:${type}"

private fun MethodReference.toSmaliDescriptor(): String =
    "${definingClass}->${name}(${parameterTypes.joinToString("")})${returnType}"

private fun Method.smaliReference(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun isNewXTimelineBody(
    method: Method,
    classType: String,
): Boolean {
    if (!AccessFlags.STATIC.isSet(method.accessFlags)) return false
    if (!classType.startsWith(URT_UI_SCOPE)) return false

    val parameters = method.parameterDescriptors()
    val timelineListDescriptor =
        parameters.singleOrNull { isDirectDescriptorInScope(it, IMMUTABLE_LIST_SCOPE) }
            ?: return false
    if (parameters.count { it == timelineListDescriptor } != 1) return false
    if (parameters.count { isDirectDescriptorInScope(it, TIMELINE_MODEL_SCOPE) } != 1) {
        return false
    }
    if (parameters.count { it == MODIFIER } != 1) return false
    if (parameters.count { it == COMPOSER } != 1) return false
    val composerIndex = parameters.indexOf(COMPOSER)
    if (parameters.drop(composerIndex + 1) != listOf(INT_DESCRIPTOR)) return false
    val instructions = method.implementation?.instructions?.toList() ?: return false
    return instructions.withIndex().any { (index, instruction) ->
        if (instruction.opcode != Opcode.NEW_INSTANCE) return@any false
        val type = (instruction as? ReferenceInstruction)?.reference as? com.android.tools.smali.dexlib2.iface.reference.TypeReference
        val lambdaType = type?.type?.toString() ?: return@any false
        if (!lambdaType.startsWith(URT_UI_SCOPE)) return@any false
        instructions.drop(index + 1).take(32).any { constructorInstruction ->
            if (constructorInstruction.opcode != Opcode.INVOKE_DIRECT &&
                constructorInstruction.opcode != Opcode.INVOKE_DIRECT_RANGE
            ) return@any false
            val constructor =
                (constructorInstruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@any false
            val constructorParameters = constructor.parameterTypes.map(CharSequence::toString)
            constructor.definingClass == lambdaType &&
                constructor.name == "<init>" &&
                constructorParameters.firstOrNull() == FUNCTION3 &&
                constructorParameters.count { it == timelineListDescriptor } == 1 &&
                constructorParameters.none {
                    it == MODIFIER || isDirectDescriptorInScope(it, TIMELINE_MODEL_SCOPE)
                } &&
                constructorParameters.any { it.startsWith(PAGING_SCOPE) } &&
                constructorParameters.any { it == FUNCTION1 }
        }
    }
}

/** The URT timeline body. Owner and method names are release-specific. */
private object NewXTimelineBodyFingerprint : Fingerprint(
    definingClass = URT_UI_SCOPE,
    returnType = VOID_DESCRIPTOR,
    custom = { method, classDef -> isNewXTimelineBody(method, classDef.type.toString()) },
)

private fun registerWidth(type: CharSequence): Int =
    if (type.toString() == "J" || type.toString() == "D") 2 else 1

private fun parameterRegister(method: Method, index: Int): Int {
    val implementation = method.implementation
        ?: throw PatchException("Photos timeline renderer has no implementation: $method")
    val parameterWidth = method.parameterTypes.sumOf(::registerWidth)
    val receiverWidth = if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    var register = implementation.registerCount - parameterWidth - receiverWidth + receiverWidth
    repeat(index) { parameterIndex ->
        register += registerWidth(method.parameterTypes[parameterIndex])
    }
    return register
}

private fun allClassDefs(context: BytecodePatchContext): List<ClassDef> =
    buildList {
        context.classDefForEach { add(it) }
    }

context(context: BytecodePatchContext)
private fun resolveTimelineType(): ResolvedTimelineType {
    val candidates = buildList {
        allClassDefs(context)
            .filter { classDef ->
                isDirectDescriptorInScope(classDef.type.toString(), TIMELINE_MODEL_SCOPE) &&
                    classDef.superclass?.toString() == ENUM_DESCRIPTOR
            }
            .forEach { classDef ->
                classDef.fields
                    .filter { field ->
                        field.name == PHOTOS_ENUM_NAME &&
                            AccessFlags.STATIC.isSet(field.accessFlags) &&
                            field.type.toString() == classDef.type.toString()
                    }
                    .forEach { field -> add(classDef.type.toString() to field.toString()) }
            }
    }
    val (classType, photosField) =
        requireExactlyOne("NewX USER_PROFILE_PHOTOS enum", candidates)
    return ResolvedTimelineType(classType, photosField)
}

context(context: BytecodePatchContext)
private fun resolveTimelineListDescriptor(): String {
    val candidates = allClassDefs(context).filter { classDef ->
        isDirectDescriptorInScope(classDef.type.toString(), IMMUTABLE_LIST_SCOPE) &&
            AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
            classDef.interfaces.any { it.toString() == LIST_DESCRIPTOR } &&
            classDef.methods.count { method ->
                method.name == OBJECT_LIST_METHOD &&
                    method.returnType.toString() == classDef.type.toString() &&
                    method.parameterDescriptors() == listOf(INT_DESCRIPTOR, INT_DESCRIPTOR)
            } == 1
    }
    return requireExactlyOne("NewX immutable timeline list interface", candidates).type.toString()
}

context(context: BytecodePatchContext)
private fun resolveComposeContracts(): ResolvedComposeContracts {
    val classDefs = allClassDefs(context)
    val paddingCandidates = classDefs.filter { classDef ->
        isDirectDescriptorInScope(classDef.type.toString(), COMPOSE_LAYOUT_SCOPE) &&
            AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
            classDef.methods.count { method ->
                method.returnType.toString() == FLOAT_DESCRIPTOR &&
                    method.parameterTypes.isEmpty()
            } == 2 &&
            classDef.methods.count { method ->
                method.returnType.toString() == FLOAT_DESCRIPTOR &&
                    method.parameterDescriptors() == listOf(LAYOUT_DIRECTION_DESCRIPTOR)
            } == 2
    }
    val paddingInterface =
        requireExactlyOne("NewX PaddingValues interface", paddingCandidates)
    val paddingDescriptor = paddingInterface.type.toString()

    val paddingImplementations = classDefs.filter { classDef ->
        classDef.interfaces.any { it.toString() == paddingDescriptor } &&
            classDef.fields.count { field ->
                !AccessFlags.STATIC.isSet(field.accessFlags) &&
                    field.type.toString() == FLOAT_DESCRIPTOR
            } == PADDING_SIDE_COUNT &&
            classDef.methods.any { method ->
                method.name == "<init>" &&
                    method.returnType.toString() == VOID_DESCRIPTOR &&
                    method.parameterDescriptors() ==
                        List(PADDING_SIDE_COUNT) { FLOAT_DESCRIPTOR }
            }
    }
    val paddingImplementation =
        requireExactlyOne("NewX PaddingValues four-side implementation", paddingImplementations)
    val constructor =
        requireExactlyOne(
            "NewX PaddingValues four-side constructor",
            paddingImplementation.methods.filter { method ->
                method.name == "<init>" &&
                    method.returnType.toString() == VOID_DESCRIPTOR &&
                    method.parameterDescriptors() ==
                        List(PADDING_SIDE_COUNT) { FLOAT_DESCRIPTOR }
            },
        )
    val implementation =
        constructor.implementation
            ?: throw PatchException("NewX PaddingValues implementation has no constructor body")
    val receiverRegister = implementation.registerCount - constructor.parameterTypes.size - 1
    val fieldsByParameter = (0 until PADDING_SIDE_COUNT).associateWith { parameterIndex ->
        val parameterRegister = receiverRegister + parameterIndex + 1
        val writes = implementation.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IPUT) return@mapNotNull null
            val registers = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
            if (registers.registerA != parameterRegister ||
                registers.registerB != receiverRegister
            ) {
                return@mapNotNull null
            }
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                ?: return@mapNotNull null
            field.takeIf { it.type.toString() == FLOAT_DESCRIPTOR }
        }.distinctBy(FieldReference::toString)
        requireExactlyOne(
            "NewX PaddingValues constructor side $parameterIndex field",
            writes,
        )
    }

    fun resolveAccessor(field: FieldReference, label: String): Method {
        val implementationAccessor =
            requireExactlyOne(
                "NewX PaddingValues $label implementation accessor",
                paddingImplementation.methods.filter { method ->
                    !AccessFlags.STATIC.isSet(method.accessFlags) &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType.toString() == FLOAT_DESCRIPTOR &&
                        method.implementation?.instructions?.count { instruction ->
                            if (instruction.opcode != Opcode.IGET) return@count false
                            val reference =
                                (instruction as? ReferenceInstruction)?.reference as? FieldReference
                            reference?.toString() == field.toString()
                        } == 1
                },
            )
        return requireExactlyOne(
            "NewX PaddingValues $label interface accessor",
            paddingInterface.methods.filter { method ->
                method.name == implementationAccessor.name &&
                    method.returnType.toString() == FLOAT_DESCRIPTOR &&
                    method.parameterTypes.isEmpty()
            },
        )
    }

    // Compose's four-side constructor is ordered left, top, right, bottom.
    val paddingTop = resolveAccessor(fieldsByParameter.getValue(1), "top")
    val paddingBottom = resolveAccessor(fieldsByParameter.getValue(3), "bottom")

    val androidViewCandidates = classDefs.flatMap { classDef ->
        classDef.methods.filter { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                classDef.type.toString().startsWith(VIEW_INTEROP_SCOPE) &&
                method.returnType.toString() == VOID_DESCRIPTOR &&
                method.parameterDescriptors().let { parameters ->
                    val functionCount = parameters.count { it == FUNCTION1 }
                    val firstFunctionIndex =
                        parameters.withIndex().firstOrNull { it.value == FUNCTION1 }?.index ?: -1
                    val secondFunctionIndex =
                        parameters.withIndex().lastOrNull { it.value == FUNCTION1 }?.index ?: -1
                    val modifierIndex = parameters.indexOf(MODIFIER)
                    val composerIndex = parameters.indexOf(COMPOSER)
                    functionCount == 2 &&
                        firstFunctionIndex < modifierIndex &&
                        modifierIndex < secondFunctionIndex &&
                        secondFunctionIndex < composerIndex &&
                        parameters.drop(composerIndex + 1).size >= 2 &&
                        parameters.drop(composerIndex + 1).all { it == INT_DESCRIPTOR }
                }
        }
    }
    val androidView =
        requireExactlyOne("NewX Compose AndroidView composable", androidViewCandidates)
    return ResolvedComposeContracts(
        paddingValuesDescriptor = paddingDescriptor,
        paddingTop = paddingTop,
        paddingBottom = paddingBottom,
        androidView = androidView,
    )
}


context(context: BytecodePatchContext)
private fun resolvePagingEvent(): ResolvedPagingEvent {
    val classDefs = allClassDefs(context)
    val classByType = classDefs.associateBy { it.type.toString() }
    val dispatchCandidates = classDefs.flatMap { classDef ->
        if (!classDef.type.toString().startsWith(BOTTOM_PAGING_SCOPE)) {
            emptyList()
        } else {
            classDef.methods.filter { method ->
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.returnType.toString() == VOID_DESCRIPTOR &&
                    method.parameterDescriptors().size == 1 &&
                    method.parameterDescriptors().single().startsWith(PAGING_SCOPE)
            }
        }
    }
    val dispatch =
        requireExactlyOne("NewX bottom paginator event dispatch method", dispatchCandidates)
    val eventInterface = dispatch.parameterDescriptors().single()
    val eventInterfaceClass =
        classByType[eventInterface]
            ?: throw PatchException("NewX bottom paginator event interface is missing: $eventInterface")
    if (!AccessFlags.INTERFACE.isSet(eventInterfaceClass.accessFlags)) {
        throw PatchException("NewX bottom paginator event target is not an interface: $eventInterface")
    }
    val dispatchInstructions =
        dispatch.implementation?.instructions?.toList()
            ?: throw PatchException("NewX bottom paginator dispatch has no implementation: $dispatch")

    fun isRequestEventConstructor(method: Method): Boolean {
        val parameters = method.parameterDescriptors()
        return method.name == "<init>" &&
            method.returnType.toString() == VOID_DESCRIPTOR &&
            parameters.size == 3 &&
            parameters[0].startsWith("L") &&
            parameters.drop(1).all { it == INT_DESCRIPTOR }
    }

    val requestEventType =
        requireExactlyOne(
            "NewX bottom pagination request event class",
            dispatchInstructions.mapNotNull { instruction ->
                if (instruction.opcode != Opcode.INSTANCE_OF) return@mapNotNull null
                val type =
                    instruction.getReference<TypeReference>()?.type?.toString()
                        ?: return@mapNotNull null
                val classDef = classByType[type] ?: return@mapNotNull null
                type.takeIf {
                    isDirectDescriptorInScope(type, PAGING_SCOPE) &&
                        classDef.interfaces.any { it.toString() == eventInterface } &&
                        classDef.methods.any(::isRequestEventConstructor)
                }
            }.distinct(),
        )
    val requestEventClass =
        classByType[requestEventType]
            ?: throw PatchException("NewX bottom pagination request event class is missing: $requestEventType")
    val requestConstructor =
        requireExactlyOne(
            "NewX bottom pagination request event constructor",
            requestEventClass.methods.filter(::isRequestEventConstructor),
        )
    return ResolvedPagingEvent(
        eventType = requestEventType,
        constructorReference = requestConstructor.smaliReference(),
        paginatorClassName = dispatch.definingClass
            .toString()
            .removePrefix("L")
            .removeSuffix(";")
            .replace('/', '.'),
    )
}

context(context: BytecodePatchContext)
private fun patchPagingEventBridge(event: ResolvedPagingEvent) {
    val extensionClass = context.mutableClassDefBy(GALLERY_EXTENSION)
    val placeholder =
        requireExactlyOne(
            "NewX gallery paging-event bridge",
            extensionClass.methods.filter { method ->
                method.name == PAGING_EVENT_HELPER &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType.toString() == OBJECT &&
                    AccessFlags.STATIC.isSet(method.accessFlags)
            },
        )
    val implementation =
        placeholder.implementation
            ?: throw PatchException("NewX gallery paging-event bridge has no implementation: $placeholder")
    val requiredRegisterCount = 4
    val helper =
        if (implementation.registerCount >= requiredRegisterCount) {
            placeholder
        } else {
            placeholder.cloneMutable(
                additionalRegisters = requiredRegisterCount - implementation.registerCount,
            ).also { expanded ->
                extensionClass.methods.remove(placeholder)
                extensionClass.methods.add(expanded)
            }
        }
    val helperImplementation =
        helper.implementation
            ?: throw PatchException("NewX gallery paging-event bridge has no implementation: $helper")
    while (helperImplementation.instructions.isNotEmpty()) {
        helperImplementation.removeInstruction(helperImplementation.instructions.lastIndex)
    }
    helper.addInstructions(
        0,
        """
            new-instance v0, ${event.eventType}
            const/4 v1, 0x0
            const/4 v2, 0x0
            const/4 v3, 0x0
            invoke-direct {v0, v1, v2, v3}, ${event.constructorReference}
            return-object v0
        """.trimIndent(),
    )
    patchBottomPaginatorClassBridge(event.paginatorClassName)
}

context(context: BytecodePatchContext)
private fun patchBottomPaginatorClassBridge(className: String) {
    val extensionClass = context.mutableClassDefBy(GALLERY_EXTENSION)
    val placeholder =
        requireExactlyOne(
            "NewX bottom paginator class bridge",
            extensionClass.methods.filter { method ->
                method.name == PAGINATOR_TYPE_HELPER &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType.toString() == STRING &&
                    AccessFlags.STATIC.isSet(method.accessFlags)
            },
        )
    val implementation =
        placeholder.implementation
            ?: throw PatchException("NewX bottom paginator class bridge has no implementation: $placeholder")
    val helper =
        if (implementation.registerCount >= 1) {
            placeholder
        } else {
            placeholder.cloneMutable(additionalRegisters = 1).also { expanded ->
                extensionClass.methods.remove(placeholder)
                extensionClass.methods.add(expanded)
            }
        }
    val helperImplementation =
        helper.implementation
            ?: throw PatchException("NewX bottom paginator class bridge has no implementation: $helper")
    while (helperImplementation.instructions.isNotEmpty()) {
        helperImplementation.removeInstruction(helperImplementation.instructions.lastIndex)
    }
    helper.addInstructions(
        0,
        """
            const-string v0, "$className"
            return-object v0
        """.trimIndent(),
    )
}

private fun isMoveObject(instruction: Instruction): Boolean =
    instruction.opcode == Opcode.MOVE_OBJECT ||
        instruction.opcode == Opcode.MOVE_OBJECT_FROM16 ||
        instruction.opcode == Opcode.MOVE_OBJECT_16

private fun writesRegister(instruction: Instruction, register: Int): Boolean {
    val destination =
        when {
            isMoveObject(instruction) -> (instruction as? TwoRegisterInstruction)?.registerA
            instruction.opcode == Opcode.IGET_OBJECT -> (instruction as? TwoRegisterInstruction)?.registerA
            instruction.opcode.name.startsWith("MOVE_RESULT") ||
                instruction.opcode == Opcode.NEW_INSTANCE ||
                instruction.opcode.name.startsWith("CONST") -> (instruction as? OneRegisterInstruction)?.registerA
            else -> null
        }
    return destination == register
}


private fun traceParameterIndex(
    method: Method,
    beforeIndex: Int,
    initialRegister: Int,
): Int? {
    var register = initialRegister
    var cursor = beforeIndex
    val parameterRegisters =
        method.parameterTypes.indices.associateBy { parameterRegister(method, it) }
    while (cursor > 0) {
        parameterRegisters[register]?.let { return it }
        var followed = false
        for (index in cursor - 1 downTo 0) {
            val instruction = method.implementation?.instructions?.elementAt(index) ?: return null
            val registers = instruction as? TwoRegisterInstruction
            if (isMoveObject(instruction) && registers?.registerA == register) {
                register = registers.registerB
                cursor = index
                followed = true
                break
            }
            if (writesRegister(instruction, register)) return null
        }
        if (!followed) return null
    }
    return parameterRegisters[register]
}

private fun argumentRegister(
    instruction: Instruction,
    reference: MethodReference,
    parameterIndex: Int,
): Int? {
    val offset = 1 + reference.parameterTypes.take(parameterIndex).sumOf(::registerWidth)
    return instruction.registersUsed.getOrNull(offset)
}

context(context: BytecodePatchContext)
private fun resolveTimelineItemCallbackIndex(method: Method): Int {
    val instructions = method.implementation?.instructions?.toList()
        ?: throw PatchException("Photos timeline renderer has no implementation: $method")
    val timelineListDescriptor = resolveTimelineListDescriptor()
    fun isTimelineStateConstructor(reference: MethodReference): Boolean {
        val parameters = reference.parameterTypes.map(CharSequence::toString)
        return reference.name == "<init>" &&
            parameters.firstOrNull() == FUNCTION3 &&
            parameters.count { it == timelineListDescriptor } == 1 &&
            parameters.none {
                it == MODIFIER || isDirectDescriptorInScope(it, TIMELINE_MODEL_SCOPE)
            } &&
            parameters.any { it.startsWith(PAGING_SCOPE) } &&
            parameters.any { it == FUNCTION1 }
    }

    val timelineStateType = requireExactlyOne(
        label = "NewX timeline state class",
        candidates = instructions.withIndex().mapNotNull { (index, instruction) ->
            if (instruction.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
            val type = (instruction as? ReferenceInstruction)?.reference?.toString()
                ?: return@mapNotNull null
            if (!type.startsWith(URT_UI_SCOPE)) return@mapNotNull null
            val hasStateConstructor = instructions.drop(index + 1).take(32).any { constructorInstruction ->
                if (constructorInstruction.opcode != Opcode.INVOKE_DIRECT &&
                    constructorInstruction.opcode != Opcode.INVOKE_DIRECT_RANGE
                ) {
                    return@any false
                }
                val reference = constructorInstruction.getReference<MethodReference>()
                    ?: return@any false
                reference.definingClass.toString() == type &&
                    isTimelineStateConstructor(reference)
            }
            type.takeIf { hasStateConstructor }
        }.distinct(),
    )
    val timelineState = context.mutableClassDefBy(timelineStateType)
    val stateConstructorCall = requireExactlyOne(
        label = "NewX timeline state constructor call",
        candidates = instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.INVOKE_DIRECT &&
                instruction.opcode != Opcode.INVOKE_DIRECT_RANGE
            ) {
                return@mapNotNull null
            }
            val reference = instruction.getReference<MethodReference>()
                ?: return@mapNotNull null
            reference.takeIf {
                it.name == "<init>" &&
                    it.definingClass.toString() == timelineStateType
            }?.let { instruction to it }
        },
    )
    val stateConstructor =
        requireExactlyOne(
            "NewX timeline state constructor",
            timelineState.methods.filter {
                it.name == stateConstructorCall.second.name &&
                    it.parameterTypes.map(CharSequence::toString) ==
                        stateConstructorCall.second.parameterTypes.map(CharSequence::toString)
            },
        )
    val functionParameterIndexes =
        stateConstructor.parameterTypes.indices.filter {
            stateConstructor.parameterTypes[it].toString() == FUNCTION1
        }
    if (functionParameterIndexes.size != 2) {
        throw PatchException(
            "NewX timeline state constructor must expose exactly two Function1 callbacks: " +
                "${stateConstructor.parameterTypes}",
        )
    }
    // The state lambda's final Function1 is the item-click callback; the first renders each item.
    val constructorParameterIndex = functionParameterIndexes.last()
    val callbackRegister = argumentRegister(
        stateConstructorCall.first,
        stateConstructorCall.second,
        constructorParameterIndex,
    ) ?: throw PatchException("NewX timeline state constructor has no callback argument register")
    return traceParameterIndex(method, instructions.indexOf(stateConstructorCall.first), callbackRegister)
        ?: throw PatchException("NewX timeline item click callback is not a renderer parameter")
}

private data class NativeMethodCall(
    val descriptor: String,
    val opcode: String,
)

private data class NativePhotoViewerTarget(
    val method: MutableMethod,
    val postParameterIndex: Int,
    val postHandleGetter: NativeMethodCall,
    val handleIdGetter: NativeMethodCall,
    val idStringGetter: NativeMethodCall,
    val mediaGetter: NativeMethodCall,
    val routeConstructor: NativeMethodCall,
    val routeClass: String,
    val routeMediaType: String,
    val navigationField: FieldReference,
    val navigationCall: NativeMethodCall,
)

context(context: BytecodePatchContext)
private fun invocationOpcode(owner: String): String {
    val classDef = runCatching { context.mutableClassDefBy(owner) }.getOrNull()
    return if (classDef != null && AccessFlags.INTERFACE.isSet(classDef.accessFlags)) {
        "invoke-interface"
    } else {
        "invoke-virtual"
    }
}

private fun methodDescriptor(
    owner: String,
    name: String,
    parameters: List<String>,
    returnType: String,
): String = "$owner->$name(${parameters.joinToString("")})$returnType"

context(context: BytecodePatchContext)
private fun Method.toNativeCall(): NativeMethodCall =
    NativeMethodCall(
        descriptor = methodDescriptor(
            owner = definingClass.toString(),
            name = name.toString(),
            parameters = parameterTypes.map(CharSequence::toString),
            returnType = returnType.toString(),
        ),
        opcode = invocationOpcode(definingClass.toString()),
    )

context(context: BytecodePatchContext)
private fun MethodReference.toNativeCall(): NativeMethodCall =
    NativeMethodCall(
        descriptor = methodDescriptor(
            owner = definingClass.toString(),
            name = name.toString(),
            parameters = parameterTypes.map(CharSequence::toString),
            returnType = returnType.toString(),
        ),
        opcode = invocationOpcode(definingClass.toString()),
    )

context(context: BytecodePatchContext)
private fun classImplements(classType: String, target: String, seen: Set<String> = emptySet()): Boolean {
    if (classType == target) return true
    if (classType in seen) return false
    val classDef = runCatching { context.mutableClassDefBy(classType) }.getOrNull() ?: return false
    return classDef.interfaces.any { classImplements(it.toString(), target, seen + classType) }
}

context(context: BytecodePatchContext)
private fun resolveNativePhotoViewerTarget(): NativePhotoViewerTarget {
    val timelineListDescriptor = resolveTimelineListDescriptor()
    val dispatchCandidates = buildList {
        context.classDefForEach { classDef ->
            if (!classDef.type.startsWith(URT_POST_SCOPE)) return@classDefForEach
            val mutableClass = context.mutableClassDefBy(classDef.type)
            mutableClass.methods
                .filter {
                    it.name == "invoke" &&
                        it.returnType.toString() == OBJECT &&
                        it.parameterTypes.map(CharSequence::toString) == listOf(OBJECT) &&
                        it.implementation != null
                }
                .forEach { method ->
                    method.instructions.forEach { instruction ->
                        val reference = instruction.getReference<MethodReference>() ?: return@forEach
                        if (
                            reference.returnType.toString() == "V" &&
                            reference.parameterTypes.size == 7 &&
                            reference.definingClass.toString().startsWith(URT_POST_SCOPE) &&
                            reference.parameterTypes[1].toString().startsWith(MODELS_SCOPE)
                        ) {
                            add(method to reference)
                        }
                    }
                }
        }
    }
    val dispatch = requireExactlyOne(
        label = "NewX post action dispatcher",
        candidates = dispatchCandidates.distinctBy { (method, reference) ->
            "${method.definingClass}->${method.name}:${reference.toSmaliDescriptor()}"
        },
    )
    val hostClass = context.mutableClassDefBy(dispatch.second.definingClass.toString())
    val hostMethods = hostClass.methods.filter {
        it.name == dispatch.second.name &&
            it.parameterTypes.map(CharSequence::toString) ==
                dispatch.second.parameterTypes.map(CharSequence::toString) &&
            it.returnType.toString() == dispatch.second.returnType.toString()
    }
    val hostMethod = requireExactlyOne("NewX post media event handler", hostMethods)
    val postType = dispatch.second.parameterTypes[1].toString()
    val postParameterIndex = hostMethod.parameterTypes.indexOfFirst { it.toString() == postType }
    if (postParameterIndex < 0) {
        throw PatchException("NewX post media event handler lost its post model parameter: $hostMethod")
    }

    val routeCandidates = buildList {
        hostMethod.instructions
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
                (instruction as? ReferenceInstruction)?.reference?.toString()
            }
            .distinct()
            .forEach { routeClass ->
                val route = runCatching { context.mutableClassDefBy(routeClass) }.getOrNull()
                    ?: return@forEach
                // No destination check here: the destination interface is R8-renamed
                // every release and is derived from the navigate call below instead.
                // The single abstract-media constructor is the complete route shape:
                // post-wrapping routes (concrete post param) and multi-arg routes
                // never match it.
                route.methods
                    .filter { it.name == "<init>" && it.parameterTypes.size == 1 }
                    .filter { constructor ->
                        val mediaType = constructor.parameterTypes.single().toString()
                        val mediaClass = runCatching { context.mutableClassDefBy(mediaType) }.getOrNull()
                        mediaClass != null &&
                            AccessFlags.ABSTRACT.isSet(mediaClass.accessFlags) &&
                            !AccessFlags.INTERFACE.isSet(mediaClass.accessFlags)
                    }
                    .forEach { constructor ->
                        add(routeClass to constructor)
                    }
            }
    }
    val route = requireExactlyOne(
        label = "NewX native photo destination",
        candidates = routeCandidates,
    )
    val routeClass = route.first
    val routeConstructor = route.second.toNativeCall()
    val routeMediaType = route.second.parameterTypes.single().toString()

    val postClass = context.mutableClassDefBy(postType)
    fun hasIdAccessor(type: String): Boolean {
        val valueClass = runCatching { context.mutableClassDefBy(type) }.getOrNull()
            ?: return false
        val methods = valueClass.methods + valueClass.interfaces.flatMap { interfaceType ->
            runCatching { context.mutableClassDefBy(interfaceType.toString()) }
                .getOrNull()
                ?.methods
                .orEmpty()
        }
        return methods.any {
            it.name == "getId" &&
                it.parameterTypes.isEmpty() &&
                it.returnType.toString().startsWith("L")
        }
    }
    val postAccessors =
        (postClass.methods + postClass.interfaces.flatMap { interfaceType ->
            runCatching { context.mutableClassDefBy(interfaceType.toString()) }
                .getOrNull()
                ?.methods
                .orEmpty()
        }).distinctBy { it.toNativeCall().descriptor }
    val hostAccessorDescriptors = hostMethod.instructions
        .mapNotNull { it.getReference<MethodReference>() }
        .map {
            methodDescriptor(
                owner = it.definingClass.toString(),
                name = it.name.toString(),
                parameters = it.parameterTypes.map(CharSequence::toString),
                returnType = it.returnType.toString(),
            )
        }
        .toSet()
    val usedPostAccessors = postAccessors.filter {
        it.toNativeCall().descriptor in hostAccessorDescriptors
    }
    val mediaGetters = usedPostAccessors.filter {
        it.parameterTypes.isEmpty() && it.returnType.toString() == timelineListDescriptor
    }
    val handleGetters = usedPostAccessors.filter {
        it.parameterTypes.isEmpty() &&
            it.returnType.toString().startsWith("L") &&
            it.returnType.toString() != timelineListDescriptor &&
            hasIdAccessor(it.returnType.toString())
    }
    val postContract = requireExactlyOne(
        label = "NewX post media model contract for $postType list=$timelineListDescriptor " +
            "media=${mediaGetters.joinToString { it.toString() }} " +
            "handles=${handleGetters.joinToString { it.toString() }}",
        candidates = listOfNotNull(
            if (mediaGetters.size == 1 && handleGetters.size == 1) {
                Triple(postClass, mediaGetters.single(), handleGetters.single())
            } else {
                null
            },
        ),
    )
    val mediaGetter = postContract.second.toNativeCall()
    val postHandleGetter = postContract.third.toNativeCall()

    val handleClass = context.mutableClassDefBy(postContract.third.returnType.toString())
    val handleIdGetters =
        handleClass.methods.filter {
            it.name == "getId" &&
                it.parameterTypes.isEmpty() &&
                it.returnType.toString().startsWith("L")
        } + handleClass.interfaces.flatMap { interfaceType ->
            runCatching { context.mutableClassDefBy(interfaceType.toString()) }
                .getOrNull()
                ?.methods
                ?.filter {
                    it.name == "getId" &&
                        it.parameterTypes.isEmpty() &&
                        it.returnType.toString().startsWith("L")
                }
                .orEmpty()
        }
    val handleIdGetter = requireExactlyOne(
        label = "NewX post id handle accessor",
        candidates = handleIdGetters.distinctBy { it.toNativeCall().descriptor },
    ).toNativeCall()
    val idType = handleIdGetters
        .distinctBy { it.toNativeCall().descriptor }
        .single { it.toNativeCall().descriptor == handleIdGetter.descriptor }
        .returnType
        .toString()
    val idClass = context.mutableClassDefBy(idType)
    val idStringGetter = requireExactlyOne(
        label = "NewX post id string accessor",
        candidates = idClass.methods.filter {
            it.name != "toString" &&
                it.parameterTypes.isEmpty() &&
                it.returnType.toString() == STRING
        },
    ).toNativeCall()

    // Controller, destination, and navigate call are all derived from the call graph:
    // the controller field's class must declare a (destination, Z) -> V method whose
    // destination is a supertype of the resolved route (kj/k(jj,Z) on old targets,
    // sj/k(rj,Z) on alpha.04). Never hardcode either descriptor; R8 reassigns them.
    val navigationCandidates =
        hostClass.fields.flatMap { field ->
            val navigationClass =
                runCatching { context.mutableClassDefBy(field.type.toString()) }.getOrNull()
                    ?: return@flatMap emptyList()
            navigationClass.methods.mapNotNull { method ->
                val parameters = method.parameterTypes.map(CharSequence::toString)
                if (parameters.size != 2 || parameters[1] != "Z") return@mapNotNull null
                if (method.returnType.toString() != "V") return@mapNotNull null
                if (!parameters[0].startsWith("L")) return@mapNotNull null
                if (!classImplements(routeClass, parameters[0])) return@mapNotNull null
                Triple(field, method, parameters[0])
            }
        }
    val navigation =
        requireExactlyOne(
            label = "NewX native photo navigation (controller field, call, destination)",
            candidates = navigationCandidates,
            describe = { (field, method, destination) ->
                "${field.type}->${field.name} ${method.name}($destination,Z)V"
            },
        )
    val navigationField = navigation.first
    val navigationCall = navigation.second.toNativeCall()

    return NativePhotoViewerTarget(
        method = hostMethod,
        postParameterIndex = postParameterIndex,
        postHandleGetter = postHandleGetter,
        handleIdGetter = handleIdGetter,
        idStringGetter = idStringGetter,
        mediaGetter = mediaGetter,
        routeConstructor = routeConstructor,
        routeClass = routeClass,
        routeMediaType = routeMediaType,
        navigationField = navigationField,
        navigationCall = navigationCall,
    )
}

private fun receiverRegister(method: Method): Int {
    val implementation = method.implementation
        ?: throw PatchException("NewX native photo handler has no implementation: $method")
    val parameterWidth = method.parameterTypes.sumOf(::registerWidth)
    return implementation.registerCount - parameterWidth - 1
}

private fun nativePhotoViewerInstructions(target: NativePhotoViewerTarget): String {
    val postRegister = parameterRegister(target.method, target.postParameterIndex)
    val receiverRegister = receiverRegister(target.method)
    val navigationField = target.navigationField.toSmaliDescriptor()
    return """
        move-object/from16 v0, v$postRegister
        ${target.postHandleGetter.opcode} {v0}, ${target.postHandleGetter.descriptor}
        move-result-object v0
        ${target.handleIdGetter.opcode} {v0}, ${target.handleIdGetter.descriptor}
        move-result-object v0
        ${target.idStringGetter.opcode} {v0}, ${target.idStringGetter.descriptor}
        move-result-object v0
        invoke-static {v0}, $GALLERY_EXTENSION->peekPendingPhotoIndex(Ljava/lang/String;)I
        move-result v1
        if-ltz v1, :piko_newx_native_photo_original
        move-object/from16 v0, v$postRegister
        ${target.mediaGetter.opcode} {v0}, ${target.mediaGetter.descriptor}
        move-result-object v2
        invoke-interface {v2}, $JAVA_LIST->size()I
        move-result v3
        if-ltz v1, :piko_newx_native_photo_clear
        if-ge v1, v3, :piko_newx_native_photo_clear
        invoke-interface {v2, v1}, $JAVA_LIST->get(I)$OBJECT
        move-result-object v4
        check-cast v4, ${target.routeMediaType}
        new-instance v5, ${target.routeClass}
        invoke-direct {v5, v4}, ${target.routeConstructor.descriptor}
        invoke-static {}, $GALLERY_EXTENSION->clearPendingPhoto()V
        move-object/from16 v7, v$receiverRegister
        iget-object v6, v7, $navigationField
        const/4 v7, 0
        ${target.navigationCall.opcode} {v6, v5, v7}, ${target.navigationCall.descriptor}
        return-void
        :piko_newx_native_photo_clear
        invoke-static {}, $GALLERY_EXTENSION->clearPendingPhoto()V
        :piko_newx_native_photo_original
    """.trimIndent()
}

context(context: BytecodePatchContext)
private fun patchNativePhotoViewer() {
    val target = resolveNativePhotoViewerTarget()
    val owner = context.mutableClassDefBy(target.method.definingClass)
    val expanded = target.method.cloneMutable(additionalRegisters = 8)
    owner.methods.remove(target.method)
    owner.methods.add(expanded)
    expanded.addInstructions(
        0,
        nativePhotoViewerInstructions(target.copy(method = expanded)),
    )
}

private fun galleryInstructions(
    timeline: ResolvedTimelineType,
    compose: ResolvedComposeContracts,
    listRegister: Int,
    timelineTypeRegister: Int,
    callbackRegister: Int,
    itemClickCallbackRegister: Int,
    paddingValuesRegister: Int,
    modifierRegister: Int,
    composerRegister: Int,
    scratchRegister: Int,
): String {
    val androidViewParameters = compose.androidView.parameterTypes
    val androidViewLastRegister = androidViewParameters.lastIndex
    val androidViewFlags =
        androidViewParameters.drop(4).indices.joinToString("\n") { index ->
            "const/4 v${4 + index}, 0"
        }
    return """
        sget-object v0, ${timeline.photosField}
        move-object/from16 v1, v$timelineTypeRegister
        if-ne v1, v0, :piko_newx_photos_original
        invoke-static {}, $GALLERY_EXTENSION->isEnabled()Z
        move-result v0
        if-eqz v0, :piko_newx_photos_original
        move-object/from16 v0, v$listRegister
        move-object/from16 v1, v$callbackRegister
        move-object/from16 v2, v$itemClickCallbackRegister
        move-object/from16 v6, v$paddingValuesRegister
        invoke-interface {v6}, ${compose.paddingTop.smaliReference()}
        move-result v3
        invoke-interface {v6}, ${compose.paddingBottom.smaliReference()}
        move-result v4
        invoke-static/range {v0 .. v4}, $GALLERY_EXTENSION->createFactory(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;FF)$FUNCTION1
        move-result-object v0
        move-object v$scratchRegister, v0
        move-object/from16 v0, v$listRegister
        move-object/from16 v1, v$callbackRegister
        move-object/from16 v2, v$itemClickCallbackRegister
        move-object/from16 v6, v$paddingValuesRegister
        invoke-interface {v6}, ${compose.paddingTop.smaliReference()}
        move-result v3
        invoke-interface {v6}, ${compose.paddingBottom.smaliReference()}
        move-result v4
        invoke-static/range {v0 .. v4}, $GALLERY_EXTENSION->createUpdater(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;FF)$FUNCTION1
        move-result-object v2
        move-object v0, v$scratchRegister
        move-object/from16 v1, v$modifierRegister
        move-object/from16 v3, v$composerRegister
        $androidViewFlags
        invoke-static/range {v0 .. v$androidViewLastRegister}, ${compose.androidView.smaliReference()}
        return-void
        :piko_newx_photos_original
    """.trimIndent()
}

@Suppress("unused")
val newXProfilePhotosGalleryPatch =
    bytecodePatch(
        name = "NewX: Gallery profile Photos tab",
        description = "Replaces the profile Photos timeline with a three-column media gallery.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch, newXDefaultMediaTabPatch, newXThumbnailCachePatch)

        val galleryToggle =
            newXToggle(
                id = "newx.post_actions_media.gallery_profile_photos",
                category = Categories.POST_ACTIONS_MEDIA,
                strings = settingStrings("piko_newx_gallery_profile_photos"),
                order = 105,
                defaultValue = true,
            )

        newXSettings {
            category(Categories.ADVANCED) {
                group(Groups.DEBUG_TOOLS) {
                    customScreen(
                        id = "newx.advanced.debug_tools.gallery_cache_stats",
                        strings = settingStrings("piko_newx_gallery_cache_stats"),
                        order = 175,
                        fragmentClassDescriptor =
                            "Lapp/morphe/extension/newx/misc/GalleryCacheStatsFragment;",
                    )
                }
            }
        }

        execute {
            val timelineType = resolveTimelineType()
            val timelineListDescriptor = resolveTimelineListDescriptor()
            val compose = resolveComposeContracts()
            val pagingEvent = resolvePagingEvent()

            val match = requireExactlyOne(
                label = "NewX profile Photos timeline body",
                candidates = NewXTimelineBodyFingerprint.scopedMatchAll(),
            )
            val originalMethod = match.method
            val parameters = originalMethod.parameterDescriptors()
            val listIndex = parameters.indexOf(timelineListDescriptor)
            val timelineTypeIndex = parameters.indexOf(timelineType.descriptor)
            val callbackIndex = parameters.indexOf(FUNCTION1)
            val itemClickCallbackIndex = resolveTimelineItemCallbackIndex(originalMethod)
            val paddingValuesIndex = parameters.indexOf(compose.paddingValuesDescriptor)
            val modifierIndex = parameters.indexOf(MODIFIER)
            val composerIndex = parameters.indexOf(COMPOSER)
            if (
                listIndex < 0 ||
                timelineTypeIndex < 0 ||
                callbackIndex < 0 ||
                itemClickCallbackIndex < 0 ||
                paddingValuesIndex < 0 ||
                modifierIndex < 0 ||
                composerIndex < 0
            ) {
                throw PatchException(
                    "Photos timeline body lost a required parameter: $parameters",
                )
            }

            val scratchRegister =
                maxOf(8, compose.androidView.parameterTypes.size + 1)
            val method = originalMethod.cloneMutable(
                additionalRegisters = scratchRegister + 1,
            ).also { expanded ->
                match.classDef.methods.remove(originalMethod)
                match.classDef.methods.add(expanded)
            }
            val listRegister = parameterRegister(method, listIndex)
            val timelineTypeRegister = parameterRegister(method, timelineTypeIndex)
            val callbackRegister = parameterRegister(method, callbackIndex)
            val itemClickCallbackRegister = parameterRegister(method, itemClickCallbackIndex)
            val paddingValuesRegister = parameterRegister(method, paddingValuesIndex)
            val modifierRegister = parameterRegister(method, modifierIndex)
            val composerRegister = parameterRegister(method, composerIndex)
            method.addInstructions(
                0,
                galleryInstructions(
                    timeline = timelineType,
                    compose = compose,
                    listRegister = listRegister,
                    timelineTypeRegister = timelineTypeRegister,
                    callbackRegister = callbackRegister,
                    itemClickCallbackRegister = itemClickCallbackRegister,
                    paddingValuesRegister = paddingValuesRegister,
                    modifierRegister = modifierRegister,
                    composerRegister = composerRegister,
                    scratchRegister = scratchRegister,
                ),
            )
            patchPagingEventBridge(pagingEvent)
            patchNativePhotoViewer()
        }
    }
