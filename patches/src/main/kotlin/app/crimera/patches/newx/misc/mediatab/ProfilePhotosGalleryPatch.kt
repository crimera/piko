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
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21s
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
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
private const val PAGING_STATE_HELPER = "readPagingState"
private const val PAGINATOR_TYPE_HELPER = "createBottomPaginatorClassName"
private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val VOID_DESCRIPTOR = "V"
private const val FLOAT_DESCRIPTOR = "F"
private const val INT_DESCRIPTOR = "I"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val COMPOSE_UNIT_SCOPE = "Landroidx/compose/ui/unit/"
private const val LAYOUT_DIRECTION_LTR = "Ltr"
private const val LAYOUT_DIRECTION_RTL = "Rtl"
private const val OBJECT_LIST_METHOD = "subList"
private const val PHOTOS_ENUM_NAME = "USER_PROFILE_PHOTOS"
private const val PADDING_SIDE_COUNT = 4
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION3 = "Lkotlin/jvm/functions/Function3;"
private const val FUNCTION2 = "Lkotlin/jvm/functions/Function2;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"
private const val JAVA_LIST = "Ljava/util/List;"
private const val OBJECT_ARRAY = "[Ljava/lang/Object;"
private const val BOOLEAN = "Ljava/lang/Boolean;"
private const val INTEGER = "Ljava/lang/Integer;"
private const val COMPOSE_LAZY_SCOPE = "Landroidx/compose/foundation/lazy/"
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
    val eventKindField: String,
    val paginatorType: String,
    val paginatorClassName: String,
    val state: ResolvedPagingState,
)

private data class ResolvedPagingState(
    val stateType: String,
    val stateFlowGetter: NativeMethodCall,
    val stateValueGetter: NativeMethodCall,
    val needsMore: NativeMethodCall,
    val terminated: NativeMethodCall,
    val threshold: NativeMethodCall,
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
    // LayoutDirection is the Compose ui.unit enum exposing Ltr/Rtl. Its obfuscated
    // descriptor is not stable, so resolve it from its (preserved) enum members.
    val layoutDirection = requireExactlyOne(
        "NewX Compose LayoutDirection enum",
        classDefs.filter { classDef ->
            isDirectDescriptorInScope(classDef.type.toString(), COMPOSE_UNIT_SCOPE) &&
                classDef.superclass?.toString() == ENUM_DESCRIPTOR &&
                classDef.fields
                    .filter { it.type.toString() == classDef.type.toString() }
                    .map { it.name.toString() }
                    .toSet() == setOf(LAYOUT_DIRECTION_LTR, LAYOUT_DIRECTION_RTL)
        },
    ).type.toString()
    val paddingCandidates = classDefs.filter { classDef ->
        isDirectDescriptorInScope(classDef.type.toString(), COMPOSE_LAYOUT_SCOPE) &&
            AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
            classDef.methods.count { method ->
                method.returnType.toString() == FLOAT_DESCRIPTOR &&
                    method.parameterTypes.isEmpty()
            } == 2 &&
            classDef.methods.count { method ->
                method.returnType.toString() == FLOAT_DESCRIPTOR &&
                    method.parameterDescriptors() == listOf(layoutDirection)
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

    val eventKindType = requestConstructor.parameterDescriptors().first()
    val eventKindClass =
        classByType[eventKindType]
            ?: throw PatchException("NewX bottom pagination event kind is missing: $eventKindType")
    val eventKindFields = eventKindClass.fields.filter { field ->
        field.name == "LAZY_LIST_SCROLL" &&
            AccessFlags.STATIC.isSet(field.accessFlags) &&
            field.type.toString() == eventKindType
    }
    val eventKindField =
        requireExactlyOne("NewX bottom pagination lazy-scroll event kind", eventKindFields)
            .toSmaliDescriptor()

    // The state contract is identified by shape, not by the obfuscated method
    // names: the abstract surface is exactly one no-arg int and two no-arg
    // booleans, with the preserved isTerminated() default alongside it.
    // threshold/needsMore are re-derived by return type from the trigger below.
    fun isStateInterface(classDef: ClassDef): Boolean {
        if (!AccessFlags.INTERFACE.isSet(classDef.accessFlags)) return false
        val noArgMethods = classDef.methods.filter { it.parameterTypes.isEmpty() }
        if (noArgMethods.none { it.name == "isTerminated" && it.returnType.toString() == "Z" }) {
            return false
        }
        val abstractNoArg = noArgMethods.filter { AccessFlags.ABSTRACT.isSet(it.accessFlags) }
        return abstractNoArg.count { it.returnType.toString() == INT_DESCRIPTOR } == 1 &&
            abstractNoArg.count { it.returnType.toString() == "Z" } == 2
    }

    val stateClassCandidates = dispatchInstructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.CHECK_CAST) return@mapNotNull null
        val type = instruction.getReference<TypeReference>()?.type?.toString()
            ?: return@mapNotNull null
        val stateClass = classByType[type] ?: return@mapNotNull null
        val stateInterfaces = stateClass.interfaces.mapNotNull { interfaceType ->
            classByType[interfaceType.toString()]
        }.filter(::isStateInterface)
        type.takeIf { stateInterfaces.size == 1 }
    }.distinct()
    val stateType =
        requireExactlyOne("NewX bottom paginator state class", stateClassCandidates)
    val stateClass =
        classByType[stateType]
            ?: throw PatchException("NewX bottom paginator state class is missing: $stateType")
    val stateInterface = requireExactlyOne(
        "NewX bottom paginator state interface",
        stateClass.interfaces.mapNotNull { interfaceType ->
            classByType[interfaceType.toString()]
        }.filter(::isStateInterface),
    )

    val paginatorClass =
        classByType[dispatch.definingClass.toString()]
            ?: throw PatchException(
                "NewX bottom paginator class is missing: ${dispatch.definingClass}",
            )
    val stateFlowGetter =
        requireExactlyOne(
            "NewX bottom paginator state-flow getter",
            paginatorClass.methods.filter { method ->
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes.isEmpty() &&
                    isStateFlowInterface(method.returnType.toString(), classByType)
            },
        )
    val stateValueGetter =
        resolveStateValueGetter(stateFlowGetter.returnType.toString(), classByType)
    val stateFlowGetterCall = stateFlowGetter.toNativeCall()

    val nativeTriggerCandidates = classDefs.flatMap { classDef ->
        classDef.methods.filter { method ->
            if (!AccessFlags.STATIC.isSet(method.accessFlags)) return@filter false
            if (method.returnType.toString() != VOID_DESCRIPTOR) return@filter false
            // The scroll state (LazyListState) is obfuscated too; match it by the
            // stable lazy scope instead of its release-specific short name. The
            // lazy/grid state lives deeper and is excluded by the direct-scope check.
            val parameters = method.parameterDescriptors()
            parameters.size == 5 &&
                parameters[0] == stateInterface.type.toString() &&
                isDirectDescriptorInScope(parameters[1], COMPOSE_LAZY_SCOPE) &&
                parameters[2] == FUNCTION2 &&
                parameters[3] == COMPOSER &&
                parameters[4] == INT_DESCRIPTOR
        }
    }
    val nativeTrigger =
        requireExactlyOne("NewX native bottom paginator scroll trigger", nativeTriggerCandidates)
    val triggerStateMethods =
        nativeTrigger.implementation?.instructions
            ?.mapNotNull { instruction -> instruction.getReference<MethodReference>() }
            ?.filter { reference ->
                reference.definingClass.toString() == stateInterface.type.toString() &&
                    reference.parameterTypes.isEmpty()
            }
            ?.distinctBy { reference -> reference.toString() }
            .orEmpty()
    val thresholdMethod = requireExactlyOne(
        "NewX native bottom paginator threshold state method",
        triggerStateMethods.filter { reference ->
            reference.returnType.toString() == INT_DESCRIPTOR
        },
    ).toNativeCall()
    val needsMoreMethod = requireExactlyOne(
        "NewX native bottom paginator needs-more state method",
        triggerStateMethods.filter { reference ->
            reference.returnType.toString() == "Z" &&
                reference.name.toString() != "isTerminated"
        },
    ).toNativeCall()
    val terminatedMethod = requireExactlyOne(
        "NewX native bottom paginator terminated state method",
        stateInterface.methods.filter { method ->
            method.name == "isTerminated" &&
                method.parameterTypes.isEmpty() &&
                method.returnType.toString() == "Z"
        },
    ).toNativeCall()

    return ResolvedPagingEvent(
        eventType = requestEventType,
        constructorReference = requestConstructor.smaliReference(),
        eventKindField = eventKindField,
        paginatorType = dispatch.definingClass.toString(),
        paginatorClassName = dispatch.definingClass
            .toString()
            .removePrefix("L")
            .removeSuffix(";")
            .replace('/', '.'),
        state = ResolvedPagingState(
            stateType = stateType,
            stateFlowGetter = stateFlowGetterCall,
            stateValueGetter = stateValueGetter,
            needsMore = needsMoreMethod,
            terminated = terminatedMethod,
            threshold = thresholdMethod,
        ),
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
            sget-object v0, ${event.eventKindField}
            new-instance v1, ${event.eventType}
            const/4 v2, 0x0
            const/4 v3, 0x0
            invoke-direct {v1, v0, v2, v3}, ${event.constructorReference}
            return-object v1
        """.trimIndent(),
    )
    patchPagingStateBridge(event)
    patchBottomPaginatorClassBridge(event.paginatorClassName)
}

context(context: BytecodePatchContext)
private fun patchPagingStateBridge(event: ResolvedPagingEvent) {
    val extensionClass = context.mutableClassDefBy(GALLERY_EXTENSION)
    val placeholder =
        requireExactlyOne(
            "NewX gallery paging-state bridge",
            extensionClass.methods.filter { method ->
                method.name == PAGING_STATE_HELPER &&
                    method.parameterTypes.map(CharSequence::toString) == listOf(OBJECT) &&
                    method.returnType.toString() == OBJECT_ARRAY &&
                    AccessFlags.STATIC.isSet(method.accessFlags)
            },
        )
    val implementation =
        placeholder.implementation
            ?: throw PatchException("NewX gallery paging-state bridge has no implementation: $placeholder")
    val requiredRegisterCount = 7
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
            ?: throw PatchException("NewX gallery paging-state bridge has no implementation: $helper")
    while (helperImplementation.instructions.isNotEmpty()) {
        helperImplementation.removeInstruction(helperImplementation.instructions.lastIndex)
    }
    val state = event.state
    helper.addInstructions(
        0,
        """
            move-object/from16 v0, p0
            check-cast v0, ${event.paginatorType}
            ${state.stateFlowGetter.opcode} {v0}, ${state.stateFlowGetter.descriptor}
            move-result-object v1
            ${state.stateValueGetter.opcode} {v1}, ${state.stateValueGetter.descriptor}
            move-result-object v0
            check-cast v0, ${state.stateType}
            ${state.needsMore.opcode} {v0}, ${state.needsMore.descriptor}
            move-result v1
            ${state.terminated.opcode} {v0}, ${state.terminated.descriptor}
            move-result v2
            ${state.threshold.opcode} {v0}, ${state.threshold.descriptor}
            move-result v3
            const/4 v5, 0x3
            new-array v4, v5, $OBJECT_ARRAY
            invoke-static {v1}, $BOOLEAN->valueOf(Z)$BOOLEAN
            move-result-object v6
            const/4 v5, 0x0
            aput-object v6, v4, v5
            invoke-static {v2}, $BOOLEAN->valueOf(Z)$BOOLEAN
            move-result-object v6
            const/4 v5, 0x1
            aput-object v6, v4, v5
            invoke-static {v3}, $INTEGER->valueOf(I)$INTEGER
            move-result-object v6
            const/4 v5, 0x2
            aput-object v6, v4, v5
            return-object v4
        """.trimIndent(),
    )
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

// Reads the destination of a const/4, const/16, or const/high16 instruction.
// Uses the format interface directly: the shared OneRegisterInstruction cast does
// not resolve against dexlib2 instruction objects in this runtime (every such
// cast yields null), while the format interface resolves correctly.
private fun constDestination(instruction: Instruction): Int? =
    when (instruction.opcode) {
        Opcode.CONST_4 -> (instruction as? Instruction11n)?.registerA
        Opcode.CONST_16 -> (instruction as? Instruction21s)?.registerA
        else -> null
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
    val mediaGetter: NativeMethodCall,
    val postType: String,
    val ebClass: String,
    val ebConstructor: NativeMethodCall,
    val w0Type: String,
    val routeConstructor: NativeMethodCall,
    val routeClass: String,
    val viewerFlags: Int,
    val navigationCall: NativeMethodCall,
)

private data class ItemClickViewerTarget(
    val ownerType: String,
    val methodName: String,
    val parameterTypes: List<String>,
    val itemEventType: String,
    val itemEventField: String,
    val navigationField: String,
)

private data class ItemMediaDelegate(
    val itemType: String,
    val holderField: String?,
    val w0Field: String,
    val postFields: List<String>,
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

private fun isStateFlowInterface(
    type: String,
    classByType: Map<String, ClassDef>,
): Boolean {
    val classDef = classByType[type] ?: return false
    return AccessFlags.INTERFACE.isSet(classDef.accessFlags) &&
        classDef.methods.any { method ->
            method.name == "getValue" &&
                method.parameterTypes.isEmpty() &&
                method.returnType.toString() == OBJECT
        }
}

context(context: BytecodePatchContext)
private fun resolveStateValueGetter(
    flowType: String,
    classByType: Map<String, ClassDef>,
): NativeMethodCall {
    val visited = mutableSetOf<String>()
    val pending = mutableListOf(flowType)
    while (pending.isNotEmpty()) {
        val currentType = pending.removeAt(0)
        if (!visited.add(currentType)) continue
        val classDef = classByType[currentType] ?: continue
        val getters =
            classDef.methods.filter { method ->
                method.name == "getValue" &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType.toString() == OBJECT
            }
        if (getters.isNotEmpty()) {
            return requireExactlyOne(
                "NewX bottom paginator state-flow getValue on $currentType",
                getters,
            ).toNativeCall()
        }
        pending += classDef.interfaces.map(CharSequence::toString)
    }
    throw PatchException(
        "NewX bottom paginator state-flow getValue is missing for $flowType",
    )
}

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

    // Post-context route (eb): built in the media branch from the post model, two
    // scribe strings, and a w0. Matched by construction in the host method plus a
    // 5-arg constructor led by the post model.
    val ebConstructors = buildList {
        hostMethod.instructions
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
                (instruction as? ReferenceInstruction)?.reference?.toString()
            }
            .distinct()
            .forEach { ebClass ->
                val eb = runCatching { context.mutableClassDefBy(ebClass) }.getOrNull()
                    ?: return@forEach
                eb.methods
                    .filter { method ->
                        method.name == "<init>" &&
                            !AccessFlags.SYNTHETIC.isSet(method.accessFlags) &&
                            method.parameterTypes.size == 5 &&
                            method.parameterTypes.first().toString() == postType
                    }
                    .forEach { constructor ->
                        add(ebClass to constructor)
                    }
            }
    }
    val ebRoute = requireExactlyOne(
        label = "NewX viewer post-context route",
        candidates = ebConstructors,
    )
    val ebClass = ebRoute.first
    val ebConstructor = ebRoute.second.toNativeCall()
    val w0Type = ebRoute.second.parameterTypes.map(CharSequence::toString)[3]

    // Full viewer destination: (media list, selected index, post context, three
    // strings, flags). The single-media constructor on the same class opens the
    // bare viewer without post chrome (profile headers, cards); the media branch
    // always uses this full one. No destination check here: the destination
    // interface is R8-renamed every release and is derived from the navigate
    // call below instead.
    val viewerConstructors = buildList {
        hostMethod.instructions
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.NEW_INSTANCE) return@mapNotNull null
                (instruction as? ReferenceInstruction)?.reference?.toString()
            }
            .distinct()
            .forEach { routeClass ->
                val route = runCatching { context.mutableClassDefBy(routeClass) }.getOrNull()
                    ?: return@forEach
                route.methods
                    .filter { method ->
                        if (method.name != "<init>") return@filter false
                        val parameters = method.parameterTypes.map(CharSequence::toString)
                        parameters.size == 7 &&
                            parameters[0] == JAVA_LIST &&
                            parameters[1] == INT_DESCRIPTOR &&
                            parameters[2] == ebClass &&
                            parameters[3] == STRING &&
                            parameters[4] == STRING &&
                            parameters[5] == STRING &&
                            parameters[6] == INT_DESCRIPTOR
                    }
                    .forEach { constructor ->
                        add(routeClass to constructor)
                    }
            }
    }
    val viewerRoute = requireExactlyOne(
        label = "NewX full viewer destination",
        candidates = viewerConstructors,
    )
    val routeClass = viewerRoute.first
    val routeConstructor = viewerRoute.second.toNativeCall()
    val viewerFlags = resolveViewerFlags(hostMethod, routeConstructor.descriptor)

    val postClass = context.mutableClassDefBy(postType)
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
    val mediaGetter = requireExactlyOne(
        label = "NewX post media list accessor for $postType list=$timelineListDescriptor " +
            "media=${mediaGetters.joinToString { it.toString() }}",
        candidates = mediaGetters.distinctBy { it.toNativeCall().descriptor },
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
    val navigationCall = navigation.second.toNativeCall()

    return NativePhotoViewerTarget(
        mediaGetter = mediaGetter,
        postType = postType,
        ebClass = ebClass,
        ebConstructor = ebConstructor,
        w0Type = w0Type,
        routeConstructor = routeConstructor,
        routeClass = routeClass,
        viewerFlags = viewerFlags,
        navigationCall = navigationCall,
    )
}

// Viewer flags: the int constant feeding the destination constructor's last
// argument at its media-branch call site. The mask selects which constructor
// params fall back to defaults, so it is read from the call site, never
// hardcoded: R8 keeps values but source changes could move them.
private fun resolveViewerFlags(hostMethod: Method, constructorDescriptor: String): Int {
    val instructions = hostMethod.implementation?.instructions?.toList()
        ?: throw PatchException("NewX post media event handler has no implementation: $hostMethod")
    val flags = instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.INVOKE_DIRECT_RANGE) return@mapIndexedNotNull null
        val range = instruction as? Instruction3rc ?: return@mapIndexedNotNull null
        if (range.registerCount != 8) return@mapIndexedNotNull null
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (reference.name.toString() != "<init>") return@mapIndexedNotNull null
        val descriptor = methodDescriptor(
            owner = reference.definingClass.toString(),
            name = reference.name.toString(),
            parameters = reference.parameterTypes.map(CharSequence::toString),
            returnType = reference.returnType.toString(),
        )
        if (descriptor != constructorDescriptor) return@mapIndexedNotNull null
        nearbyFlagsConstant(instructions, index, range.startRegister + 7)
    }.distinct()
    return requireExactlyOne(
        "NewX viewer destination flags",
        flags,
    )
}

// Reads a flags int constant feeding a call-site argument register. Looks back
// over instructions provably free of register writes (consts to other registers,
// invokes, branches, returns, field stores); any other shape aborts the site so a
// stale value can never be picked up. Uses the const format interfaces directly
// because the shared OneRegisterInstruction cast does not resolve against
// dexlib2 instruction objects in this runtime.
private fun nearbyFlagsConstant(
    instructions: List<Instruction>,
    callIndex: Int,
    flagsRegister: Int,
): Int? {
    val start = maxOf(0, callIndex - 8)
    for (cursor in callIndex - 1 downTo start) {
        val prior = instructions.elementAt(cursor)
        if (constDestination(prior) == flagsRegister) {
            return (prior as? NarrowLiteralInstruction)?.narrowLiteral
        }
        val name = prior.opcode.name
        val sideEffectFree = name.startsWith("CONST") ||
            name.startsWith("INVOKE") ||
            name.startsWith("IF_") ||
            name.startsWith("GOTO") ||
            name.startsWith("RETURN") ||
            name == "NOP" ||
            name.startsWith("MONITOR") ||
            name.startsWith("IPUT") ||
            name.startsWith("SPUT") ||
            name == "THROW"
        if (!sideEffectFree) return null
    }
    return null
}

private fun receiverRegister(method: Method): Int {
    val implementation = method.implementation
        ?: throw PatchException("NewX native photo handler has no implementation: $method")
    val parameterWidth = method.parameterTypes.sumOf(::registerWidth)
    return implementation.registerCount - parameterWidth - 1
}

private fun itemClickViewerInstructions(
    event: NativePhotoViewerTarget,
    target: ItemClickViewerTarget,
    delegates: List<ItemMediaDelegate>,
    eventRegister: Int,
    receiverRegister: Int,
): String {
    val mediaOwner = event.mediaGetter.descriptor.substringBefore("->")
    // Per item type: narrow to the model, resolve its media holder, stash it, then
    // hunt the post model for the viewer post context (holder first, then the item
    // fields that can hold it). Every miss falls through with the pending entry
    // intact, so the Java side degrades to the deep-link fallback. Register plan:
    // v0 work, v1 flags, v2 item, v4 holder, v5 w0; the tail reuses v0..v7 for the
    // consecutive range invokes below.
    val delegateCascade = delegates.mapIndexed { index, delegate ->
        val holderRead = delegate.holderField?.let { "iget-object v0, v0, $it" }.orEmpty()
        val postHunt = delegate.postFields.mapIndexed { fieldIndex, postField ->
            """
            iget-object v0, v2, $postField
            instance-of v1, v0, ${event.postType}
            if-eqz v1, :piko_newx_gallery_qh_${index}_$fieldIndex
            iget-object v5, v2, ${delegate.w0Field}
            goto :piko_newx_gallery_have_q1
            :piko_newx_gallery_qh_${index}_$fieldIndex
            """.trimIndent()
        }.joinToString("\n")
        """
            instance-of v1, v0, ${delegate.itemType}
            if-eqz v1, :piko_newx_gallery_next_$index
            check-cast v0, ${delegate.itemType}
            check-cast v2, ${delegate.itemType}
            $holderRead
            check-cast v0, $mediaOwner
            move-object v4, v0
            instance-of v1, v0, ${event.postType}
            if-eqz v1, :piko_newx_gallery_qh_${index}_holder
            iget-object v5, v2, ${delegate.w0Field}
            goto :piko_newx_gallery_have_q1
            :piko_newx_gallery_qh_${index}_holder
            $postHunt
            goto :piko_newx_gallery_original
            :piko_newx_gallery_next_$index
        """.trimIndent()
    }.joinToString("\n")
    // NOTE: instance-of is format 22c (both registers 4-bit). The event parameter
    // lives in a high register, so it must be copied down with move-object/from16
    // first; referencing it directly drops the instruction at assembly time and
    // breaks verification (if-eqz on an undefined register). The same applies to
    // the range invokes below: all argument registers stay within v0..v7.
    return """
        move-object/from16 v0, v$eventRegister
        instance-of v1, v0, ${target.itemEventType}
        if-eqz v1, :piko_newx_gallery_original
        check-cast v0, ${target.itemEventType}
        iget-object v0, v0, ${target.itemEventField}
        move-object v2, v0
        $delegateCascade
        goto :piko_newx_gallery_original
        :piko_newx_gallery_have_q1
        invoke-static {v2}, $GALLERY_EXTENSION->takePendingPhotoIndex(Ljava/lang/Object;)I
        move-result v1
        if-ltz v1, :piko_newx_gallery_original
        ${event.mediaGetter.opcode} {v4}, ${event.mediaGetter.descriptor}
        move-result-object v2
        invoke-interface {v2}, $JAVA_LIST->size()I
        move-result v3
        invoke-static {v3, v1}, $GALLERY_EXTENSION->reportGalleryTap(II)V
        if-ltz v1, :piko_newx_gallery_clear
        if-ge v1, v3, :piko_newx_gallery_clear
        move v6, v1
        move-object v7, v2
        move-object v1, v0
        check-cast v1, ${event.postType}
        move-object v4, v5
        new-instance v0, ${event.ebClass}
        const/16 v2, 0x0
        const/16 v3, 0x0
        const/16 v5, 0x0
        invoke-direct/range {v0 .. v5}, ${event.ebConstructor.descriptor}
        move-object v3, v0
        new-instance v0, ${event.routeClass}
        move-object v1, v7
        move v2, v6
        const/16 v4, 0x0
        const/16 v5, 0x0
        const/16 v6, 0x0
        const/16 v7, 0x${event.viewerFlags.toString(16)}
        invoke-direct/range {v0 .. v7}, ${event.routeConstructor.descriptor}
        move-object v5, v0
        move-object/from16 v7, v$receiverRegister
        iget-object v6, v7, ${target.navigationField}
        const/4 v7, 0
        ${event.navigationCall.opcode} {v6, v5, v7}, ${event.navigationCall.descriptor}
        return-void
        :piko_newx_gallery_clear
        invoke-static {}, $GALLERY_EXTENSION->clearPendingPhoto()V
        :piko_newx_gallery_original
    """.trimIndent()
}

context(context: BytecodePatchContext)
private fun resolveItemClickViewerTargets(
    event: NativePhotoViewerTarget,
): Pair<List<ItemClickViewerTarget>, List<ItemMediaDelegate>> {
    // The gallery tap invokes the timeline item-click callback, which wraps the item
    // in an item-click event (x0 wrapping the o0 item) and dispatches it to the URT
    // component. Hooking the media-tap handler (g5.z) can never fire from that path,
    // so the divert moves to the item-event consumers instead.
    val mediaOwner = event.mediaGetter.descriptor.substringBefore("->")
    val navigationOwner = event.navigationCall.descriptor.substringBefore("->")
    val classDefs = allClassDefs(context)
    val classByType = classDefs.associateBy { it.type.toString() }

    val closures = mutableMapOf<String, Set<String>>()
    fun closure(type: String): Set<String> {
        closures[type]?.let { return it }
        closures[type] = emptySet()
        val classDef = classByType[type] ?: return emptySet()
        val result = mutableSetOf(type)
        classDef.interfaces.mapTo(result) { it.toString() }
        classDef.interfaces.forEach { result.addAll(closure(it.toString())) }
        classDef.superclass?.toString()?.let { result.addAll(closure(it)) }
        closures[type] = result
        return result
    }
    fun implements(classType: String, target: String): Boolean = target in closure(classType)

    // Item-click event fields: the single instance field of a final event class whose type
    // is an interface that a media holder also implements. On this target that is x0.a:o0
    // with the j1 timeline item delegating its u5 media contract to its o6 holder field.
    data class ItemEvent(val field: String, val eventType: String, val itemType: String)
    val itemEvents = classDefs.mapNotNull { classDef ->
        val type = classDef.type.toString()
        if (!AccessFlags.FINAL.isSet(classDef.accessFlags)) return@mapNotNull null
        if (AccessFlags.INTERFACE.isSet(classDef.accessFlags)) return@mapNotNull null
        if (AccessFlags.ABSTRACT.isSet(classDef.accessFlags)) return@mapNotNull null
        val instanceFields = classDef.fields.filter { !AccessFlags.STATIC.isSet(it.accessFlags) }
        if (instanceFields.size != 1) return@mapNotNull null
        val field = instanceFields[0]
        val itemType = field.type.toString()
        if (!itemType.startsWith("L")) return@mapNotNull null
        // The gallery tap wraps a timeline item model (o0 in the timelines scope),
        // not a bare media holder (o6 is also wrapped elsewhere, e.g. NFL events).
        if (!itemType.startsWith(TIMELINE_MODEL_SCOPE)) return@mapNotNull null
        val itemClass = classByType[itemType] ?: return@mapNotNull null
        if (!AccessFlags.INTERFACE.isSet(itemClass.accessFlags)) return@mapNotNull null
        val heldByMediaHolder = classDefs.any { candidate ->
            val candidateType = candidate.type.toString()
            !AccessFlags.INTERFACE.isSet(candidate.accessFlags) &&
                !AccessFlags.ABSTRACT.isSet(candidate.accessFlags) &&
                implements(candidateType, itemType) &&
                implements(candidateType, mediaOwner)
        }
        if (!heldByMediaHolder) return@mapNotNull null
        ItemEvent(field.toSmaliDescriptor(), type, itemType)
    }
    val itemEvent = requireExactlyOne(
        "NewX gallery item-click event",
        itemEvents,
    )

    // Media delegates: concrete item models carrying the u5 media contract, either
    // directly (the post model itself) or through a single holder field (j1.a:o6).
    // Each delegate also records how to reach the viewer post context from the item:
    // the w0 field for eb construction, and the fields that can hold the post model
    // (any field whose type the post model implements, e.g. j1.a:o6 for a q1 post).
    // Item types without media fall through to the original handler per tap.
    val delegates = classDefs.mapNotNull { classDef ->
        val type = classDef.type.toString()
        if (AccessFlags.INTERFACE.isSet(classDef.accessFlags)) return@mapNotNull null
        if (AccessFlags.ABSTRACT.isSet(classDef.accessFlags)) return@mapNotNull null
        if (!implements(type, itemEvent.itemType)) return@mapNotNull null
        val holderField = if (implements(type, mediaOwner)) {
            null
        } else {
            val holderFields = classDef.fields.filter { field ->
                !AccessFlags.STATIC.isSet(field.accessFlags) &&
                    field.type.toString().startsWith("L") &&
                    implements(field.type.toString(), mediaOwner)
            }
            if (holderFields.size != 1) return@mapNotNull null
            holderFields[0].toSmaliDescriptor()
        }
        val w0Fields = classDef.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString() == event.w0Type
        }
        if (w0Fields.size != 1) return@mapNotNull null
        val postFields = classDef.fields
            .filter { field ->
                !AccessFlags.STATIC.isSet(field.accessFlags) &&
                    field.type.toString().startsWith("L") &&
                    implements(event.postType, field.type.toString())
            }
            .map { it.toSmaliDescriptor() }
        ItemMediaDelegate(
            itemType = type,
            holderField = holderField,
            w0Field = w0Fields[0].toSmaliDescriptor(),
            postFields = postFields,
        )
    }
    if (delegates.isEmpty()) {
        throw PatchException(
            "NewX gallery item-click media delegates: no ${itemEvent.itemType} model carries $mediaOwner",
        )
    }

    // Hook methods: instance void methods taking a single interface parameter implemented
    // by the event class, reading the item field. The gallery tap reaches them through
    // the item-click callback; without a pending entry the preamble falls through, so
    // hooking every consumer is behavior-preserving.
    val targets = mutableListOf<ItemClickViewerTarget>()
    classDefs.forEach { classDef ->
        val ownerType = classDef.type.toString()
        classDef.methods.forEach methodLoop@{ method ->
            if (AccessFlags.STATIC.isSet(method.accessFlags)) return@methodLoop
            if (method.returnType.toString() != VOID_DESCRIPTOR) return@methodLoop
            if (method.parameterTypes.size != 1) return@methodLoop
            val parameterType = method.parameterTypes.single().toString()
            if (!parameterType.startsWith("L")) return@methodLoop
            val parameterClass = classByType[parameterType] ?: return@methodLoop
            if (!AccessFlags.INTERFACE.isSet(parameterClass.accessFlags)) return@methodLoop
            val instructions = method.implementation?.instructions?.toList() ?: return@methodLoop
            if (!implements(itemEvent.eventType, parameterType)) return@methodLoop
            val readsItem = instructions.any { instruction ->
                if (instruction.opcode != Opcode.IGET_OBJECT) return@any false
                (instruction as? ReferenceInstruction)?.reference?.toString() == itemEvent.field
            }
            if (!readsItem) return@methodLoop
            val navigationFields = classDef.fields.filter { field ->
                !AccessFlags.STATIC.isSet(field.accessFlags) &&
                    field.type.toString() == navigationOwner
            }
            val navigationField = requireExactlyOne(
                "NewX gallery item-click navigation field in $ownerType",
                navigationFields,
            )
            targets.add(
                ItemClickViewerTarget(
                    ownerType = ownerType,
                    methodName = method.name.toString(),
                    parameterTypes = method.parameterTypes.map(CharSequence::toString),
                    itemEventType = itemEvent.eventType,
                    itemEventField = itemEvent.field,
                    navigationField = navigationField.toSmaliDescriptor(),
                ),
            )
        }
    }
    if (targets.isEmpty()) {
        throw PatchException(
            "NewX gallery item-click viewer targets: no item-event consumer with a navigation controller found",
        )
    }
    return targets.distinct() to delegates
}

context(context: BytecodePatchContext)
private fun patchItemClickPhotoViewer() {
    val event = resolveNativePhotoViewerTarget()
    val (targets, delegates) = resolveItemClickViewerTargets(event)
    targets.forEach { target ->
        val owner = context.mutableClassDefBy(target.ownerType)
        val method = requireExactlyOne(
            "NewX gallery item-click viewer method ${target.ownerType}->${target.methodName}",
            owner.methods.filter { method ->
                method.name.toString() == target.methodName &&
                    method.parameterTypes.map(CharSequence::toString) == target.parameterTypes
            },
        )
        val expanded = method.cloneMutable(additionalRegisters = 8)
        owner.methods.remove(method)
        owner.methods.add(expanded)
        expanded.addInstructions(
            0,
            itemClickViewerInstructions(
                event = event,
                target = target,
                delegates = delegates,
                eventRegister = parameterRegister(expanded, 0),
                receiverRegister = receiverRegister(expanded),
            ),
        )
    }
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
            patchItemClickPhotoViewer()
        }
    }
