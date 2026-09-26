package app.crimera.patches.newx.misc.navbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import app.crimera.patches.newx.utils.destinationRegisterOrNull
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.newx.utils.resolveIntegerLiteralOnCurrentPath
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderSwitchElement
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
internal const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
internal const val FUNCTION2_DESCRIPTOR = "Lkotlin/jvm/functions/Function2;"
internal const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
internal const val STRING_DESCRIPTOR = "Ljava/lang/String;"
internal const val ICONS_DESCRIPTOR_PREFIX = "Lcom/x/icons/"

/** Native navigation slots and their localized names. */
internal val NAV_BAR_NATIVE_TAB_OPTIONS =
    listOf(
        "HOME" to "piko_newx_nav_bar_home",
        "EXPLORE" to "piko_newx_nav_bar_explore",
        "GROK" to "piko_newx_nav_bar_grok",
        "NOTIFICATIONS" to "piko_newx_nav_bar_notifications",
        "DM" to "piko_newx_nav_bar_dm",
    )

internal data class NavBarItemContentTarget(
    val method: MutableMethod,
    val navigationField: FieldReference,
    val iconType: String,
    val tabDataValueType: String,
    val rendererCallIndex: Int,
    val iconRegister: Int,
    val labelRegister: Int,
    val tabDataValueRegister: Int,
    val thisRegister: Int,
    val tabIconFields: Map<String, FieldReference>,
)

/** Resolved NewX navigation tab data shared by all navigation bar patches. */
internal data class NewXNavBarTabData(
    val navigationType: String,
    val tabDataValueType: String,
)

/** Filters NewX navigation state before the landing component consumes it. */
internal object NewXTabDataFingerprint : Fingerprint(
    definingClass = "Lcom/x/main/",
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                name = "getEntries",
                parameters = emptyList(),
                returnType = "Lkotlin/enums/EnumEntries;",
            ),
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "COMMUNITIES",
            ),
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "SPACES",
            ),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Ljava/util/Map;",
                name = "put",
                parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
                returnType = "Ljava/lang/Object;",
            ),
        ),
)

internal const val FINGERPRINT_ANCHOR_COUNT = 4

internal fun validateNewXNavBarTabData(match: Match): NewXNavBarTabData {
    if (match.instructionMatches.size != FINGERPRINT_ANCHOR_COUNT) {
        throw PatchException(
            "NewX tabData fingerprint returned ${match.instructionMatches.size} anchors; " +
                "expected $FINGERPRINT_ANCHOR_COUNT",
        )
    }

    val entriesReference =
        match.instructionMatches[0].instruction.getReference<MethodReference>()
            ?: throw PatchException("NewX tabData getEntries anchor has no method reference")
    val communityReference =
        match.instructionMatches[1].instruction.getReference<FieldReference>()
            ?: throw PatchException("NewX tabData COMMUNITIES anchor has no field reference")
    val spacesReference =
        match.instructionMatches[2].instruction.getReference<FieldReference>()
            ?: throw PatchException("NewX tabData SPACES anchor has no field reference")
    val tabTypeDescriptor = entriesReference.definingClass.toString()
    if (entriesReference.name != "getEntries" ||
        entriesReference.parameterTypes.isNotEmpty() ||
        entriesReference.returnType.toString() != "Lkotlin/enums/EnumEntries;" ||
        communityReference.definingClass.toString() != tabTypeDescriptor ||
        communityReference.type.toString() != tabTypeDescriptor ||
        spacesReference.definingClass.toString() != tabTypeDescriptor ||
        spacesReference.type.toString() != tabTypeDescriptor ||
        communityReference.name != "COMMUNITIES" ||
        spacesReference.name != "SPACES"
    ) {
        throw PatchException("NewX tabData navigation enum anchors are inconsistent")
    }

    val putIndex = match.instructionMatches.last().index
    val putInstruction = match.method.instructions.getOrNull(putIndex)
    val put = putInstruction as? Instruction35c
        ?: throw PatchException("NewX tabData Map.put anchor is not an invoke-interface instruction")
    if (put.opcode != Opcode.INVOKE_INTERFACE) {
        throw PatchException("NewX tabData Map.put anchor is not an invoke-interface instruction")
    }
    val tabDataValueType = match.method.resolveTabDataValueType(putIndex, put.registerE)
        ?: throw PatchException("NewX tabData Map.put value type could not be resolved")

    return NewXNavBarTabData(
        navigationType = tabTypeDescriptor,
        tabDataValueType = tabDataValueType,
    )
}

/** Follows the put value register back to the constructor of the map value model. */
private fun MutableMethod.resolveTabDataValueType(
    putIndex: Int,
    valueRegister: Int,
): String? {
    for (index in putIndex - 1 downTo 0) {
        val instruction: Instruction = instructions[index]
        if (instruction.opcode == Opcode.NEW_INSTANCE &&
            (instruction as? OneRegisterInstruction)?.registerA == valueRegister
        ) {
            return instruction.getReference<TypeReference>()?.toString()
        }
        if (instruction.destinationRegisterOrNull() == valueRegister) return null
    }
    return null
}

internal data class TabDataFilterTarget(
    val method: MutableMethod,
    val insertionIndex: Int,
    val tabDataRegister: Int,
    val resultType: String,
)

private const val TAB_DATA_ARG_INDEX = 9
private const val STATE_TAB_DATA_PARAMETER_INDEX = 8
private val STATE_CONSTRUCTOR_PARAMETER_COUNTS = setOf(16, 17, 18)
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val MAP_DESCRIPTOR = "Ljava/util/Map;"
private const val LINKED_HASH_MAP_DESCRIPTOR = "Ljava/util/LinkedHashMap;"

/** Resolves the state constructor argument that receives the tab map. */
internal fun resolveNewXNavBarFilterTarget(match: Match): TabDataFilterTarget {
    val mapPutIndex = match.instructionMatches.last().index
    val mapPutInstruction = match.method.instructions.getOrNull(mapPutIndex)
    val mapPutReference = mapPutInstruction?.getReference<MethodReference>()
    if (mapPutInstruction?.opcode != Opcode.INVOKE_INTERFACE ||
        mapPutReference?.definingClass != MAP_DESCRIPTOR ||
        mapPutReference.name != "put" ||
        mapPutReference.parameterTypes.map { it.toString() } !=
            listOf("Ljava/lang/Object;", "Ljava/lang/Object;") ||
        mapPutReference.returnType != "Ljava/lang/Object;"
    ) {
        throw PatchException("NewX tabData Map.put fingerprint anchor is invalid")
    }

    val mapPut =
        mapPutInstruction as? Instruction35c
            ?: throw PatchException("NewX tabData Map.put is not a 35c instruction")
    val mapRegister =
        mapPut.registersUsed.firstOrNull()
            ?: throw PatchException("NewX tabData Map.put has no map register")

    val stateInitIndex = match.method.findStateInitIndex(mapPutIndex)
    if (stateInitIndex != null) {
        val stateInitInstruction =
            match.method.instructions.getOrNull(stateInitIndex) as? Instruction3rc
                ?: throw PatchException("NewX tabData State constructor is not a range instruction")
        if (stateInitInstruction.opcode != Opcode.INVOKE_DIRECT_RANGE) {
            throw PatchException("NewX tabData State constructor is not invoke-direct/range")
        }
        if (TAB_DATA_ARG_INDEX !in 0 until stateInitInstruction.registerCount) {
            throw PatchException(
                "NewX tabData argument index $TAB_DATA_ARG_INDEX is outside the " +
                    "${stateInitInstruction.registerCount}-register State constructor range",
            )
        }
        return TabDataFilterTarget(
            method = match.method,
            insertionIndex = stateInitIndex,
            tabDataRegister = stateInitInstruction.startRegister + TAB_DATA_ARG_INDEX,
            resultType = MAP_DESCRIPTOR,
        )
    }

    // 12.29 wraps the tab map in a landing wrapper constructor instead of a state holder.
    val wrapperInitIndex = match.method.findTabDataWrapperInitIndex(mapPutIndex, mapRegister)
    val wrapperInitInstruction =
        match.method.instructions.getOrNull(wrapperInitIndex) as? Instruction35c
            ?: throw PatchException("NewX tabData wrapper constructor is not a 35c instruction")
    val wrapperReference =
        wrapperInitInstruction.getReference<MethodReference>()
            ?: throw PatchException("NewX tabData wrapper constructor has no method reference")
    val mapArgumentIndex =
        wrapperReference.parameterTypes.indexOfFirst { type ->
            type.toString() == LINKED_HASH_MAP_DESCRIPTOR
        }
    if (mapArgumentIndex < 0) {
        throw PatchException("NewX tabData wrapper constructor has no LinkedHashMap argument")
    }
    return TabDataFilterTarget(
        method = match.method,
        insertionIndex = wrapperInitIndex,
        tabDataRegister = wrapperInitInstruction.registersUsed[mapArgumentIndex + 1],
        resultType = LINKED_HASH_MAP_DESCRIPTOR,
    )
}

private fun MutableMethod.findStateInitIndex(anchorIndex: Int): Int? {
    val candidates =
        instructions
            .drop(anchorIndex + 1)
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.INVOKE_DIRECT_RANGE) return@mapNotNull null

                val range = instruction as? Instruction3rc ?: return@mapNotNull null
                val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                val parameters = reference.parameterTypes.map { it.toString() }
                if (reference.name != "<init>" ||
                    reference.returnType != "V" ||
                    parameters.size !in STATE_CONSTRUCTOR_PARAMETER_COUNTS ||
                    range.registerCount != parameters.size + 1 ||
                    parameters.getOrNull(0)?.startsWith("L") != true ||
                    parameters.getOrNull(6) != LIST_DESCRIPTOR ||
                    parameters.getOrNull(7) != MAP_DESCRIPTOR ||
                    parameters.getOrNull(STATE_TAB_DATA_PARAMETER_INDEX) != MAP_DESCRIPTOR
                ) {
                    return@mapNotNull null
                }

                instruction.location.index
            }

    return requireAtMostOne("stable NewX tabData State constructor", candidates)
}

private fun MutableMethod.findTabDataWrapperInitIndex(
    anchorIndex: Int,
    mapRegister: Int,
): Int {
    val candidates =
        instructions
            .drop(anchorIndex + 1)
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.INVOKE_DIRECT) return@mapNotNull null

                val invoke = instruction as? Instruction35c ?: return@mapNotNull null
                val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                val parameters = reference.parameterTypes.map { it.toString() }
                if (reference.name != "<init>" ||
                    reference.returnType != "V" ||
                    parameters != listOf(MAP_DESCRIPTOR, "Z", LINKED_HASH_MAP_DESCRIPTOR)
                ) {
                    return@mapNotNull null
                }
                // Tie the wrapper to the exact map the fingerprint built, so no obfuscated owner
                // is needed and a stray (Map, Z, LinkedHashMap) constructor cannot match.
                val linkedHashMapIndex = parameters.indexOf(LINKED_HASH_MAP_DESCRIPTOR)
                if (invoke.registersUsed.getOrNull(linkedHashMapIndex + 1) != mapRegister) {
                    return@mapNotNull null
                }

                instruction.location.index
            }

    return requireExactlyOne("NewX tabData wrapper constructor", candidates)
}

/**
 * The tab change methods consume the navigation tab (optionally with a reselect Function0) and
 * perform the stack navigation. The plain parameter shape excludes the MainActivity deep-link
 * handlers, which share the stack-navigation call but also take a route and arguments.
 */
context(context: BytecodePatchContext)
internal fun resolveTabChangeMethods(tabData: NewXNavBarTabData): List<MutableMethod> {
    val matches = mutableListOf<TabChangeMethodKey>()
    context.classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            if (method.implementation == null) return@forEach
            if (method.returnType.toString() != "V") return@forEach
            val parameters = method.parameterTypes.map(CharSequence::toString)
            if (parameters.firstOrNull() != tabData.navigationType) return@forEach
            if (!parameters.isTabChangeSignature()) return@forEach
            if (!method.hasStackNavigationCall()) return@forEach
            matches += TabChangeMethodKey(classDef.type.toString(), method.name, parameters)
        }
    }
    if (matches.isEmpty()) {
        throw PatchException(
            "Expected at least one NewX tab change method for ${tabData.navigationType}, found 0",
        )
    }
    return matches.map { key -> mutableTabChangeMethod(key) }
}

private fun List<String>.isTabChangeSignature(): Boolean =
    size == 1 || (size == 2 && this[1] == FUNCTION0_DESCRIPTOR)

internal data class TabChangeMethodKey(
    val definingClass: String,
    val name: String,
    val parameterTypes: List<String>,
)

/** Resolves a single navigation component's tab change method, or null when it has none (12.29+). */
context(context: BytecodePatchContext)
internal fun resolveComponentTabChangeMethod(
    componentClass: String,
    navigationType: String,
): MutableMethod? {
    val candidates =
        context.mutableClassDefBy(componentClass).methods.filter { method ->
            method.implementation != null &&
                method.returnType.toString() == "V" &&
                method.parameterTypes.map(CharSequence::toString) == listOf(navigationType) &&
                method.hasStackNavigationCall()
        }
    return requireAtMostOne("NewX tab change method", candidates) { it.toString() }
}

context(context: BytecodePatchContext)
private fun mutableTabChangeMethod(key: TabChangeMethodKey): MutableMethod {
    val candidates =
        context.mutableClassDefBy(key.definingClass).methods.filter { method ->
            method.name == key.name &&
                method.returnType.toString() == "V" &&
                method.parameterTypes.map(CharSequence::toString) == key.parameterTypes
        }
    return requireExactlyOne("NewX tab change method ${key.name}", candidates) { it.toString() }
}

context(context: BytecodePatchContext)
internal fun resolveTabDataValueConstructor(tabDataValueType: String): MethodReference {
    val classDef = context.mutableClassDefBy(tabDataValueType)
    val instanceFields = classDef.fields.filter { !com.android.tools.smali.dexlib2.AccessFlags.STATIC.isSet(it.accessFlags) }
    val fieldTypes = instanceFields.map { it.type.toString() }.sorted()
    
    val primaryConstructors = classDef.methods.filter { method ->
        method.name == "<init>" && method.parameterTypes.map { it.toString() }.sorted() == fieldTypes
    }
    
    val constructor = requireExactlyOne("NewX TabDataValueType primary constructor", primaryConstructors)
    return ImmutableMethodReference(
        tabDataValueType,
        "<init>",
        constructor.parameterTypes,
        "V"
    )
}

internal fun Method.hasStackNavigationCall(): Boolean {
    val methodInstructions = implementation?.instructions?.toList() ?: return false
    return methodInstructions.withIndex().any { (index, instruction) ->
        if (instruction.opcode != Opcode.IGET_OBJECT) return@any false
        val fieldLoad = instruction as? TwoRegisterInstruction ?: return@any false
        val field = instruction.getReference<FieldReference>() ?: return@any false
        val call = methodInstructions.getOrNull(index + 1) ?: return@any false
        if (call.opcode != Opcode.INVOKE_VIRTUAL && call.opcode != Opcode.INVOKE_VIRTUAL_RANGE) {
            return@any false
        }
        val reference = call.getReference<MethodReference>() ?: return@any false
        call.registersUsed.firstOrNull() == fieldLoad.registerA &&
            field.type.toString() == reference.definingClass.toString() &&
            reference.returnType.toString() == "V" &&
            reference.parameterTypes.map(CharSequence::toString) ==
                listOf(FUNCTION2_DESCRIPTOR, FUNCTION1_DESCRIPTOR)
    }
}

context(context: BytecodePatchContext)
internal fun resolveNavBarItemContent(tabData: NewXNavBarTabData): NavBarItemContentTarget {
    // Single traversal collects both the item renderer and the content class: the two
    // predicates are disjoint method shapes, so merging the passes preserves the exact
    // candidate sets of the former separate scans.
    val rendererCandidates = mutableListOf<ImmutableMethodReference>()
    val contentClasses = mutableListOf<String>()
    context.classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            if (method.implementation == null) return@forEach
            val parameters = method.parameterTypes.map(CharSequence::toString)
            if (method.returnType.toString() == "V" &&
                parameters.size == 5 &&
                parameters[0].startsWith(ICONS_DESCRIPTOR_PREFIX) &&
                parameters[1] == STRING_DESCRIPTOR &&
                parameters[2] == tabData.tabDataValueType &&
                parameters[3] == COMPOSER_DESCRIPTOR &&
                parameters[4] == "I"
            ) {
                rendererCandidates +=
                    ImmutableMethodReference(
                        classDef.type.toString(),
                        method.name,
                        parameters,
                        "V",
                    )
            }
        }
        val constructors =
            classDef.methods.filter { method ->
                method.returnType.toString() == "V" &&
                    method.parameterTypes.map(CharSequence::toString) ==
                    listOf("Z", tabData.navigationType, tabData.tabDataValueType)
            }
        if (constructors.isNotEmpty()) {
            requireExactlyOne(
                "NewX navigation bar item content constructor in ${classDef.type}",
                constructors,
            ) { it.toString() }
            contentClasses += classDef.type.toString()
        }
    }
    val renderer =
        requireExactlyOne("NewX navigation bar item renderer", rendererCandidates) { it.toString() }
    val rendererDescriptor = renderer.toSmaliDescriptor()

    // The item content lambda captures (selected, tab, badge); R8 erases its field types, so the
    // constructor is the stable identity.
    // This is a read-only discovery pass. Converting every class to a mutable proxy here keeps
    // the whole APK alive and can exceed the manager's 512 MB minimum heap on large NewX builds.
    val consumerClass = requireExactlyOne("NewX navigation bar item content class", contentClasses)
    val consumerClassDef = context.mutableClassDefBy(consumerClass)
    val contentConstructor =
        requireExactlyOne(
            "NewX navigation bar item content constructor in $consumerClass",
            consumerClassDef.methods.filter { method ->
                method.returnType.toString() == "V" &&
                    method.parameterTypes.map(CharSequence::toString) ==
                    listOf("Z", tabData.navigationType, tabData.tabDataValueType)
            },
        ) { it.toString() }

    val tabParameterRegister = contentConstructor.p0Register + 2
    val navigationFieldInstructions =
        contentConstructor.instructions.filter { instruction ->
            instruction.opcode == Opcode.IPUT_OBJECT &&
                (instruction as? TwoRegisterInstruction)?.registerA == tabParameterRegister
        }
    val navigationField =
        requireExactlyOne("NewX navigation bar item tab field", navigationFieldInstructions) {
            it.getReference<FieldReference>()?.toString() ?: "null"
        }.getReference<FieldReference>()
            ?: throw PatchException("NewX navigation bar item tab field has no field reference")

    val rendererCallerMethods = mutableListOf<MutableMethod>()
    consumerClassDef.methods.forEach { method ->
        if (method.implementation == null) return@forEach
        if (method.instructions.any { instruction -> instruction.referencesMethod(rendererDescriptor) }) {
            rendererCallerMethods += method
        }
    }
    val originalMethod =
        requireExactlyOne("NewX navigation bar item content renderer call", rendererCallerMethods) { it.toString() }

    // The compiler reuses parameter registers for the icon and the resolved label, so the receiver
    // cannot be read at the convergence point. One extra local preserves the receiver for the whole
    // method; cloneMutable copies the original parameters before shifting them.
    val originalRegisterCount =
        originalMethod.implementation?.registerCount
            ?: throw PatchException("NewX navigation bar item content has no implementation: $originalMethod")
    val consumerMethod =
        originalMethod.cloneMutable(
            additionalRegisters = originalMethod.numberOfParameterRegisters + 1,
        )
    consumerClassDef.methods.remove(originalMethod)
    consumerClassDef.methods.add(consumerMethod)

    val rendererCallIndices =
        consumerMethod.instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf { instruction.referencesMethod(rendererDescriptor) }
        }
    val rendererCallIndex =
        requireExactlyOne("NewX navigation bar item renderer call", rendererCallIndices) { it.toString() }
    val rendererCall =
        consumerMethod.instructions.getOrNull(rendererCallIndex) as? Instruction35c
            ?: throw PatchException("NewX navigation bar item renderer call is not a 5-register invoke")
    val iconRegister = rendererCall.registerC
    val labelRegister = rendererCall.registerD
    val tabDataValueRegister = rendererCall.registerE

    return NavBarItemContentTarget(
        method = consumerMethod,
        navigationField = navigationField,
        iconType = renderer.parameterTypes.first().toString(),
        tabDataValueType = tabData.tabDataValueType,
        rendererCallIndex = rendererCallIndex,
        iconRegister = iconRegister,
        labelRegister = labelRegister,
        tabDataValueRegister = tabDataValueRegister,
        thisRegister = originalRegisterCount,
        tabIconFields = consumerMethod.resolveNavigationTabIcons(tabData.navigationType, iconRegister),
    )
}

internal data class PackedSwitchCase(
    val key: Int,
    val startIndex: Int,
    val endIndex: Int,
)

/**
 * Resolves the icon field of every navigation tab from the item content icon switches. The
 * selected switch is first, the unselected switch second; the unselected icon is used for both
 * states because the replacement is a single picker icon. The shared default icon covers the
 * Communities case that reuses the pre-switch value.
 */
context(context: BytecodePatchContext)
internal fun MutableMethod.resolveNavigationTabIcons(
    navigationType: String,
    iconRegister: Int,
): Map<String, FieldReference> {
    val mappingFields =
        instructions
            .filter { instruction ->
                instruction.opcode == Opcode.SGET_OBJECT &&
                    instruction.getReference<FieldReference>()?.type?.toString() == "[I"
            }.mapNotNull { instruction -> instruction.getReference<FieldReference>() }
            .distinctBy(FieldReference::toString)
    val mappingField =
        requireExactlyOne("NewX navigation when mapping array", mappingFields) { it.toString() }

    val enumCases = resolveEnumSwitchCases(navigationType, mappingField)
    val switchIndices =
        instructions.withIndex().filter { indexed -> indexed.value.opcode == Opcode.PACKED_SWITCH }
            .map { indexed -> indexed.index }
    val iconSwitches =
        switchIndices.filter { index ->
            packedSwitchCases(index).any { switchCase ->
                instructions.resolveCaseIconField(switchCase, iconRegister) != null
            }
        }
    if (iconSwitches.size != 2) {
        throw PatchException("Expected two NewX navigation icon switches, found ${iconSwitches.size}: $this")
    }

    val defaultIconField =
        instructions.resolveDefaultIconField(iconSwitches.first(), iconRegister)
            ?: throw PatchException("NewX navigation default icon was not resolved: $this")
    val unselectedCases = packedSwitchCases(iconSwitches.last())
    return NAV_BAR_NATIVE_TAB_OPTIONS.associate { (name, _) ->
        val case =
            enumCases[name]
                ?: throw PatchException("NewX navigation when mapping has no case for $name: $this")
        val switchCase =
            requireExactlyOne(
                "NewX navigation icon case for $name",
                unselectedCases.filter { candidate -> candidate.key == case },
            ) { it.toString() }
        name to (instructions.resolveCaseIconField(switchCase, iconRegister) ?: defaultIconField)
    }
}

context(context: BytecodePatchContext)
internal fun resolveEnumSwitchCases(
    navigationType: String,
    mappingField: FieldReference,
): Map<String, Int> {
    val mappingClass = context.mutableClassDefBy(mappingField.definingClass)
    val initializers =
        mappingClass.methods.filter { method ->
            method.name == "<clinit>" && method.parameterTypes.isEmpty() && method.returnType == "V"
        }
    val initializer =
        requireExactlyOne("NewX navigation when mapping initializer", initializers) { it.toString() }
    val cases = linkedMapOf<String, Int>()
    var lastEnumFieldName: String? = null
    initializer.instructions.forEachIndexed { index, instruction ->
        if (instruction.opcode == Opcode.SGET_OBJECT) {
            val reference = instruction.getReference<FieldReference>()
            if (reference?.type?.toString() == navigationType) lastEnumFieldName = reference.name
        }
        if (instruction.opcode != Opcode.APUT) return@forEachIndexed
        val registers = instruction as? ThreeRegisterInstruction ?: return@forEachIndexed
        val literal =
            initializer.instructions.resolveIntegerLiteralOnCurrentPath(index, registers.registerA)
        val name = lastEnumFieldName
        if (literal != null && name != null) cases[name] = literal
    }
    return cases
}

internal fun MutableMethod.packedSwitchCases(switchIndex: Int): List<PackedSwitchCase> {
    val instruction = instructions[switchIndex] as? BuilderInstruction31t
        ?: throw PatchException("NewX packed switch is not a mutable packed switch: $this")
    val payload = instruction.target.location.instruction as? SwitchPayload
        ?: throw PatchException("NewX packed switch payload is missing: $this")
    val elements =
        payload.switchElements.map { element ->
            element as? BuilderSwitchElement
                ?: throw PatchException("NewX packed switch case is not mutable: $this")
        }
    return elements.map { element ->
        val start = element.target.location.index
        val end =
            elements
                .filter { other -> other.target.location.index > start }
                .minOfOrNull { other -> other.target.location.index }
                ?: instructions.size
        PackedSwitchCase(element.key, start, end)
    }
}

internal fun List<Instruction>.resolveCaseIconField(
    switchCase: PackedSwitchCase,
    iconRegister: Int,
): FieldReference? {
    for (index in switchCase.startIndex until switchCase.endIndex) {
        val instruction = this[index]
        if (instruction.opcode == Opcode.SGET_OBJECT &&
            (instruction as? OneRegisterInstruction)?.registerA == iconRegister
        ) {
            val reference = instruction.getReference<FieldReference>()
            if (reference?.type?.toString()?.startsWith(ICONS_DESCRIPTOR_PREFIX) == true) {
                return reference
            }
        }
        if (instruction.destinationRegisterOrNull() == iconRegister &&
            instruction.opcode != Opcode.SGET_OBJECT
        ) {
            return null
        }
    }
    return null
}

internal fun List<Instruction>.resolveDefaultIconField(
    switchIndex: Int,
    iconRegister: Int,
): FieldReference? {
    for (index in switchIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.destinationRegisterOrNull() != iconRegister) continue
        if (instruction.opcode != Opcode.SGET_OBJECT) return null
        return instruction.getReference<FieldReference>()
            ?.takeIf { reference -> reference.type.toString().startsWith(ICONS_DESCRIPTOR_PREFIX) }
    }
    return null
}

internal fun Instruction.referencesMethod(descriptor: String): Boolean =
    getReference<MethodReference>()?.toSmaliDescriptor() == descriptor

internal fun MethodReference.toSmaliDescriptor(): String =
    "${definingClass}->${name}(${parameterTypes.joinToString("")})${returnType}"
