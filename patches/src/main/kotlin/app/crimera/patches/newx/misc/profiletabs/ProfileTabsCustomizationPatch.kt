package app.crimera.patches.newx.misc.profiletabs

import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.OBJECT_MOVE_OPCODES
import app.crimera.patches.common.destinationRegisterOrNull
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.resolveIntegerLiteralOnCurrentPath
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXCustomScreen
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.PROFILE_TABS_CATALOG_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.PROFILE_TABS_EDITOR_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.PROFILE_TABS_FILTER_DESCRIPTOR
import app.crimera.patches.settings.SettingsRegistrationState
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val OBJECT_ARRAY_DESCRIPTOR = "[Ljava/lang/Object;"
private const val LIST_OF_NOT_NULL_CLASS = "Lkotlin/collections/CollectionsKt;"
private const val ENUM_NAME_DESCRIPTOR = "$ENUM_DESCRIPTOR->name()Ljava/lang/String;"
private const val PROFILE_TAB_ID_BRIDGE_NAME = "tabIdOf"
private const val PROFILE_TAB_FILTER_DESCRIPTOR =
    "$PROFILE_TABS_FILTER_DESCRIPTOR->filter($LIST_DESCRIPTOR)$LIST_DESCRIPTOR"
private const val PROFILE_TAB_REGISTER_DESCRIPTOR =
    "$PROFILE_TABS_CATALOG_DESCRIPTOR->registerTab(${STRING_DESCRIPTOR}I)V"

/**
 * Feature-switch literals that exist only in the profile page builder's tab-list arm. Each one is
 * unique in the APK, and the method has to contain all three, so a relocated builder still
 * resolves and a split builder fails closed instead of matching a partial shape.
 */
private val PROFILE_TAB_STABLE_STRINGS =
    listOf(
        "x_lite_profile_combined_posts_highlights_enabled",
        "x_lite_profile_photos_videos_tabs_enabled",
        "x_lite_profile_all_posts_tab_enabled",
    )

/** Constant names the enum must declare before its title literals are trusted. */
private val REQUIRED_PROFILE_TAB_IDS = listOf("Posts", "Replies")

/**
 * Enum constants that are still resolved and title-validated but never registered with the editor
 * catalog, so the editor does not list them.
 *
 * Under the default combined-posts/highlights layout the builder emits one `All` page whose
 * sub-tabs carry `Posts`, and under the default photos/videos layout it emits one `Videos` page
 * whose sub-tabs carry `Photos`; standalone `Photos`/`Media`/`Posts` pages only exist for other
 * feature-switch combinations. None of them is the top-level page the default profile moves, so
 * only the movable entries stay in the editor. In the non-combined builds this also means the
 * app's top-level `Posts` page cannot be reordered or hidden (it still renders in the app order).
 */
private val EDITOR_HIDDEN_PROFILE_TAB_IDS = listOf("Photos", "Media", "Posts")

/**
 * Editor title overrides: the registered id stays the key (it is the stored id and the id the
 * runtime filter matches), but the editor labels it with the app's own localized title of the
 * mapped constant. The default profile layout labels the combined `Videos` page as `Media`.
 */
private val EDITOR_TITLE_SOURCE = mapOf("Videos" to "Media")

private val INVOKE_OPCODES =
    setOf(
        Opcode.INVOKE_STATIC,
        Opcode.INVOKE_STATIC_RANGE,
        Opcode.INVOKE_VIRTUAL,
        Opcode.INVOKE_VIRTUAL_RANGE,
        Opcode.INVOKE_INTERFACE,
        Opcode.INVOKE_INTERFACE_RANGE,
        Opcode.INVOKE_DIRECT,
        Opcode.INVOKE_DIRECT_RANGE,
        Opcode.INVOKE_SUPER,
        Opcode.INVOKE_SUPER_RANGE,
    )

private data class ProfileTabListTarget(
    val method: MutableMethod,
    val insertionIndex: Int,
    val listRegister: Int,
    val pageConfigType: String,
)

private data class ProfileTabSpec(
    val id: String,
    val titleResourceId: Int,
)

@Suppress("unused")
val customizeNewXProfileTabsPatch =
    bytecodePatch(
        name = "NewX: Customize profile tabs",
        description = "Reorder and hide the tabs shown on profile screens.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        newXCustomScreen(
            id = "newx.profile.tabs.editor",
            category = Categories.POST_ACTIONS_MEDIA,
            strings = settingStrings("piko_newx_profile_tabs_editor"),
            order = 160,
            fragmentClassDescriptor = PROFILE_TABS_EDITOR_DESCRIPTOR,
            iconResourceName = "ic_vector_bulleted_list",
        )

        execute {
            val target = resolveProfileTabListTarget()
            val tabField = resolveProfileTabField(target.pageConfigType)
            val tabs = resolveProfileTabSpecs(tabField.type.toString())

            injectProfileTabFilterHook(
                method = target.method,
                insertionIndex = target.insertionIndex,
                listRegister = target.listRegister,
            )
            injectProfileTabIdBridge(tabField)
            injectProfileTabsRegistration(tabs)
        }
    }

/**
 * Replaces the page-config list with the editor-filtered one in the register that already holds
 * `listOfNotNull`'s result.
 *
 * The consumer call is the insertion point: it is the last instruction that sees the unfiltered
 * list, and every path that reaches it has to run the filter, so any incoming label is relocated
 * to the hook head (`relocateBranchTargets = true`). Reusing the list register instead of taking a
 * scratch one avoids the 4-bit/8-bit scratch limits in the 234-register builder frame.
 */
internal fun injectProfileTabFilterHook(
    method: MutableMethod,
    insertionIndex: Int,
    listRegister: Int,
) = method.insertHook(
    index = insertionIndex,
    excludedRegisters = listOf(listRegister),
    relocateBranchTargets = true,
) {
    invokeStatic(methodReference(PROFILE_TAB_FILTER_DESCRIPTOR), listRegister)
    moveResult(listRegister, LIST_DESCRIPTOR)
}

/**
 * Replaces the extension-side `ProfileTabsCatalog.tabIdOf` placeholder with the resolved
 * page-config field read, so the runtime never reflects on a release-specific type.
 */
context(context: BytecodePatchContext)
private fun injectProfileTabIdBridge(tabField: FieldReference) {
    val catalogClass = context.mutableClassDefBy(PROFILE_TABS_CATALOG_DESCRIPTOR)
    val placeholder =
        requireExactlyOne(
            "NewX profile tab id bridge placeholder",
            catalogClass.methods.filter { method ->
                method.name == PROFILE_TAB_ID_BRIDGE_NAME &&
                    method.parameterTypes.map(CharSequence::toString) == listOf(OBJECT_DESCRIPTOR) &&
                    method.returnType == STRING_DESCRIPTOR
            },
        )

    val implementation =
        MethodImplementationBuilder(2).apply {
            addInstruction("return-void".toInstruction())
        }.methodImplementation
    val bridgeMethod =
        MutableMethod(
            ImmutableMethod(
                catalogClass.type,
                PROFILE_TAB_ID_BRIDGE_NAME,
                listOf(ImmutableMethodParameter(OBJECT_DESCRIPTOR, emptySet(), null)),
                STRING_DESCRIPTOR,
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet(),
                emptySet(),
                implementation,
            ),
        )
    val bridgeImplementation =
        bridgeMethod.implementation
            ?: throw PatchException("NewX profile tab id bridge has no implementation")
    bridgeImplementation.removeInstruction(bridgeImplementation.instructions.lastIndex)
    bridgeMethod.injectProfileTabIdBridgeBody(tabField)

    catalogClass.methods.remove(placeholder)
    catalogClass.methods.add(bridgeMethod)
}

/**
 * Emits the `tabIdOf` body. The cast is required because the parameter is an `Object`: Dalvik's
 * verifier requires the receiver to be assignable to the field's declaring class before the
 * `iget-object`, and the filter only ever passes page-config objects from the resolved list.
 */
internal fun MutableMethod.injectProfileTabIdBridgeBody(tabField: FieldReference) {
    if (parameterTypes.size != 1 || returnType != STRING_DESCRIPTOR) {
        throw PatchException("NewX profile tab id bridge has an unexpected signature: $this")
    }
    insertHook(index = 0, relocateBranchTargets = false) {
        checkCast(p0Register, tabField.definingClass.toString())
        iget(p0Register, p0Register, tabField)
        // `Enum.name()` is a stable framework contract; the field descriptor is the
        // release-specific part resolved above.
        invokeVirtual(methodReference(ENUM_NAME_DESCRIPTOR), p0Register)
        moveResult(p0Register, STRING_DESCRIPTOR)
        returnObject(p0Register)
    }
}

context(context: BytecodePatchContext)
private fun injectProfileTabsRegistration(tabs: List<ProfileTabSpec>) {
    val registerTab = methodReference(PROFILE_TAB_REGISTER_DESCRIPTOR)
    val tabsById = tabs.associateBy { tab -> tab.id }
    SettingsRegistrationState.inject(context) {
        tabs.filterNot { tab -> tab.id in EDITOR_HIDDEN_PROFILE_TAB_IDS }
            .forEach { tab ->
                constString(0, tab.id)
                constInt(1, editorTitleResourceId(tab, tabsById))
                invokeStatic(registerTab, 0, 1)
            }
    }
}

/** Title resource for [tab], overridden through [EDITOR_TITLE_SOURCE] when the id has a source. */
private fun editorTitleResourceId(tab: ProfileTabSpec, tabsById: Map<String, ProfileTabSpec>): Int {
    val sourceId = EDITOR_TITLE_SOURCE[tab.id] ?: return tab.titleResourceId
    return tabsById[sourceId]?.titleResourceId
        ?: throw PatchException(
            "NewX profile tab ${tab.id} needs the $sourceId title but that constant was not resolved",
        )
}

context(context: BytecodePatchContext)
private fun resolveProfileTabListTarget(): ProfileTabListTarget {
    val candidateClasses =
        PROFILE_TAB_STABLE_STRINGS
            .flatMap { literal -> context.classDefByStrings(literal) }
            .distinctBy { classDef -> classDef.type }
    val candidateMethods =
        candidateClasses.flatMap { classDef ->
            classDef.methods.filter { method ->
                method.implementation != null &&
                    PROFILE_TAB_STABLE_STRINGS.all { literal -> methodContainsString(method, literal) }
            }
        }
    val method = requireExactlyOne("NewX profile tab page builder", candidateMethods)

    val instructions =
        method.implementation?.instructions?.toList()
            ?: throw PatchException("NewX profile tab page builder has no instructions: $method")
    val listOfNotNullCalls =
        instructions.withIndex().filter { (_, instruction) ->
            instruction.isStaticCallTo(LIST_OF_NOT_NULL_CLASS, "listOfNotNull", listOf(OBJECT_ARRAY_DESCRIPTOR))
        }
    val listOfNotNullCall =
        requireExactlyOne("NewX profile tab listOfNotNull call", listOfNotNullCalls) { (index, _) ->
            "$method @ $index"
        }
    val callIndex = listOfNotNullCall.index

    val arrayInstruction =
        instructions.getOrNull(callIndex - 2)
            ?: throw PatchException("NewX profile tab array allocation is missing in $method")
    if (arrayInstruction.opcode != Opcode.FILLED_NEW_ARRAY_RANGE) {
        throw PatchException(
            "NewX profile tab array allocation is not filled-new-array/range in $method at $callIndex: " +
                "${arrayInstruction.opcode}",
        )
    }
    val arrayDescriptor =
        arrayInstruction.getReference<TypeReference>()?.toString()
            ?: throw PatchException("NewX profile tab array allocation has no element type: $method")
    if (!arrayDescriptor.startsWith("[L") || !arrayDescriptor.endsWith(";")) {
        throw PatchException("NewX profile tab array type is not a reference array: $arrayDescriptor")
    }
    val arrayResult =
        instructions.getOrNull(callIndex - 1)
            ?: throw PatchException("NewX profile tab array result is missing in $method")
    if (arrayResult.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("NewX profile tab array result is not moved with move-result-object: $method")
    }
    val arrayRegister = (arrayResult as OneRegisterInstruction).registerA
    val argumentRegister =
        requireExactlyOne(
            "NewX profile tab listOfNotNull argument",
            listOfNotNullCall.value.registersUsed,
        ) { register -> "v$register" }
    if (arrayRegister != argumentRegister) {
        throw PatchException(
            "NewX profile tab array result v$arrayRegister does not feed the listOfNotNull call in $method",
        )
    }

    val resultInstruction =
        instructions.getOrNull(callIndex + 1)
            ?: throw PatchException("NewX profile tab list result is missing in $method")
    if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("NewX profile tab list result is not moved with move-result-object: $method")
    }
    val listRegister = (resultInstruction as OneRegisterInstruction).registerA
    val consumer =
        instructions.getOrNull(callIndex + 2)
            ?: throw PatchException("NewX profile tab list consumer is missing in $method")
    if (consumer.opcode !in INVOKE_OPCODES) {
        throw PatchException("NewX profile tab list consumer is not an invoke: $method @ ${callIndex + 2}")
    }
    if (listRegister !in consumer.registersUsed) {
        throw PatchException(
            "NewX profile tab list register v$listRegister is not consumed by the call in $method " +
                "@ ${callIndex + 2}",
        )
    }

    val mutableClass = context.mutableClassDefBy(method.definingClass)
    val mutableMethod =
        requireExactlyOne(
            "NewX profile tab page builder mutable method",
            mutableClass.methods.filter { candidate ->
                candidate.name == method.name &&
                    candidate.returnType == method.returnType &&
                    candidate.parameterTypes.map(CharSequence::toString) ==
                    method.parameterTypes.map(CharSequence::toString)
            },
        )

    return ProfileTabListTarget(
        method = mutableMethod,
        insertionIndex = callIndex + 2,
        listRegister = listRegister,
        pageConfigType = arrayDescriptor.substring(1),
    )
}

context(context: BytecodePatchContext)
private fun resolveProfileTabField(pageConfigType: String): FieldReference {
    val pageConfig =
        context.classDefByOrNull(pageConfigType)
            ?: throw PatchException("NewX profile page config class is not in the APK: $pageConfigType")
    val enumFields =
        pageConfig.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type.toString().startsWith("L") &&
                field.type.toString().endsWith(";") &&
                context.classDefByOrNull(field.type.toString())?.superclass == ENUM_DESCRIPTOR
        }
    return requireExactlyOne("NewX profile page config tab field for $pageConfigType", enumFields) { field ->
        "${field.definingClass}->${field.name}:${field.type}"
    }
}

context(context: BytecodePatchContext)
private fun resolveProfileTabSpecs(tabEnumType: String): List<ProfileTabSpec> {
    val enumClass =
        context.classDefByOrNull(tabEnumType)
            ?: throw PatchException("NewX profile tab enum is not in the APK: $tabEnumType")
    val initializers =
        enumClass.methods.filter { method ->
            method.name == "<clinit>" && method.parameterTypes.isEmpty() && method.returnType == "V"
        }
    val initializer = requireExactlyOne("NewX profile tab enum initializer for $tabEnumType", initializers)
    val instructions =
        initializer.implementation?.instructions?.toList()
            ?: throw PatchException("NewX profile tab enum initializer has no instructions: $initializer")

    val specs = linkedMapOf<String, ProfileTabSpec>()
    instructions.forEachIndexed { index, instruction ->
        if (instruction.opcode != Opcode.INVOKE_DIRECT) return@forEachIndexed
        val reference = instruction.getReference<MethodReference>() ?: return@forEachIndexed
        if (reference.definingClass.toString() != tabEnumType) return@forEachIndexed
        if (reference.name != "<init>") return@forEachIndexed
        if (reference.parameterTypes.map(CharSequence::toString) != listOf(STRING_DESCRIPTOR, "I", "I")) {
            return@forEachIndexed
        }
        val registers = instruction.registersUsed
        if (registers.size != 4) return@forEachIndexed

        val id =
            instructions.resolveStringConstantOnCurrentPath(index, registers[1])
                ?: throw PatchException(
                    "NewX profile tab name was not resolved for $tabEnumType at instruction $index",
                )
        val titleResourceId =
            instructions.resolveIntegerLiteralOnCurrentPath(index, registers[3])
                ?: throw PatchException("NewX profile tab title resource was not resolved for $id in $tabEnumType")
        val storedName =
            instructions.resolveStoredEnumConstant(index, registers[0], tabEnumType)
                ?: throw PatchException("NewX profile tab constant for $id is not stored in $tabEnumType")
        if (storedName != id) {
            throw PatchException("NewX profile tab constant $id is stored as $storedName in $tabEnumType")
        }
        val existing = specs[id]
        if (existing != null && existing.titleResourceId != titleResourceId) {
            throw PatchException("NewX profile tab $id has conflicting title resources in $tabEnumType")
        }
        specs[id] = ProfileTabSpec(id, titleResourceId)
    }

    if (specs.isEmpty()) {
        throw PatchException("NewX profile tab enum $tabEnumType declares no tabs")
    }
    val missing = REQUIRED_PROFILE_TAB_IDS.filter { required -> required !in specs }
    if (missing.isNotEmpty()) {
        throw PatchException("NewX profile tab enum $tabEnumType is missing required tabs $missing")
    }
    return specs.values.toList()
}

/** Field name of the static enum constant stored right after the constructor call. */
private fun List<Instruction>.resolveStoredEnumConstant(
    constructorIndex: Int,
    instanceRegister: Int,
    enumType: String,
): String? {
    val limit = minOf(constructorIndex + 5, size)
    for (index in constructorIndex + 1 until limit) {
        val instruction = this[index]
        if (instruction.opcode != Opcode.SPUT_OBJECT) continue
        if ((instruction as? OneRegisterInstruction)?.registerA != instanceRegister) continue
        val field = instruction.getReference<FieldReference>() ?: return null
        if (field.definingClass.toString() != enumType || field.type.toString() != enumType) return null
        return field.name
    }
    return null
}

/** String constant that reaches [register] on the current path, following object moves. */
private fun List<Instruction>.resolveStringConstantOnCurrentPath(
    instructionIndex: Int,
    register: Int,
): String? {
    var trackedRegister = register
    for (index in instructionIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return null
            if (move.registerA != trackedRegister) continue
            trackedRegister = move.registerB
            continue
        }
        if (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) {
            if ((instruction as? OneRegisterInstruction)?.registerA != trackedRegister) continue
            return instruction.getReference<StringReference>()?.string
        }
        if (instruction.destinationRegisterOrNull() == trackedRegister) return null
    }
    return null
}

private fun Instruction.isStaticCallTo(
    owner: String,
    name: String,
    parameterTypes: List<String>,
): Boolean {
    if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return false
    val reference = getReference<MethodReference>() ?: return false
    if (reference.definingClass.toString() != owner || reference.name != name) return false
    return reference.parameterTypes.map(CharSequence::toString) == parameterTypes
}

private fun methodContainsString(
    method: Method,
    literal: String,
): Boolean {
    val instructions = method.implementation?.instructions ?: return false
    for (instruction in instructions) {
        if (instruction.opcode != Opcode.CONST_STRING && instruction.opcode != Opcode.CONST_STRING_JUMBO) continue
        val reference = (instruction as? ReferenceInstruction)?.reference as? StringReference ?: continue
        if (reference.string == literal) return true
    }
    return false
}
