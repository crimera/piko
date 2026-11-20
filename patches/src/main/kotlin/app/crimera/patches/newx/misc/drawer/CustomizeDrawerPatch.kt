package app.crimera.patches.newx.misc.drawer

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.MultiChoiceSettingDefinition
import app.crimera.patches.settings.SettingReadRegisterConstraint
import app.crimera.patches.settings.SettingsRegistrationState
import app.crimera.patches.settings.ToggleSettingDefinition
import app.crimera.patches.settings.choice
import app.crimera.patches.settings.injectRead
import app.crimera.patches.newx.settings.newXCustomScreen
import app.crimera.patches.newx.settings.newXSettingsPatch
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.pikoSettingsIconGetter
import app.crimera.patches.newx.settings.resolveSettingsIconField
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.settings.newXMultiChoice
import app.crimera.patches.newx.misc.navbar.NewXNavBarTabData
import app.crimera.patches.newx.misc.navbar.NewXTabDataFingerprint
import app.crimera.patches.newx.misc.navbar.resolveIconField
import app.crimera.patches.newx.misc.navbar.resolveNavBarItemContent
import app.crimera.patches.newx.misc.navbar.resolveTabIconFieldsFromHelper
import app.crimera.patches.newx.misc.navbar.resolveTitleResourceIdAtRowCall
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resource.resourceId
import app.crimera.patches.newx.misc.navbar.toSmaliDescriptor
import app.crimera.patches.newx.misc.navbar.validateNewXNavBarTabData
import app.crimera.patches.newx.misc.navbar.resolveIconDrawables
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_CATALOG_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_EDITOR_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_ITEM_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_TAB_OPENER_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.PIKO_SETTINGS_ICON_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.patches.common.OBJECT_MOVE_OPCODES
import app.crimera.patches.common.hasComposeShape
import app.crimera.patches.common.parameterDescriptors
import app.crimera.bytecode.Block
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.destinationRegisterOrNull
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.resolveIntegerLiteralOnCurrentPath
import app.crimera.patches.common.resolveIntegerLiterals
import app.crimera.patches.common.valueReachesRegister
import app.crimera.patches.common.writesObjectRegister
import app.crimera.patches.common.scopedMatchAll
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.util.indexOfFirstInstruction

private const val DRAWER_RESOURCE_ITEM_ID_PREFIX = "RESOURCE_NAME_"
private const val DRAWER_RESOURCE_TYPE = "string"
/** Skip target of the replaced `DrawerTabOpener` shortcut bodies: the component was never captured. */
private const val DRAWER_TAB_DONE_LABEL = "piko_drawer_tab_done"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION2_DESCRIPTOR = "Lkotlin/jvm/functions/Function2;"
private const val FUNCTION3_DESCRIPTOR = "Lkotlin/jvm/functions/Function3;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

/**
 * Resolves the current APK's string entry names before the bytecode patch emits option IDs.
 * Resource IDs are intentionally not used as persisted identifiers: aapt2 is free to renumber
 * them on every app update.
 */
private object DrawerResourceNames {
    @Volatile
    private var namesById: Map<Int, String> = emptyMap()

    fun replace(names: Map<Int, String>) {
        namesById = names.toMap()
    }

    fun requireName(resourceId: Int): String =
        namesById[resourceId]
            ?: throw PatchException(
                "NewX drawer string resource has no entry name: " +
                    "0x${resourceId.toUInt().toString(16)}",
            )
}

/**
 * The resource mapping patch decodes resources.arsc to public.xml. Keep the reverse lookup in a
 * drawer-local table so the bytecode patch can turn discovered string IDs into stable names.
 */
private val drawerResourceNamesPatch =
    patch(
        name = "NewX: Resolve drawer resource names",
        description = "Resolves stable names for dynamically discovered drawer strings.",
        default = false,
    ) {

        execute {
            resourceXml("res/values/public.xml").use { publicXml ->
                val entries = linkedMapOf<Int, String>()
                publicXml.elements("public").forEach { element ->
                    if (element.attribute("type") != DRAWER_RESOURCE_TYPE) return@forEach

                    val idText = element.attribute("id").orEmpty()
                    val resourceId =
                        idText
                            .removePrefix("0x")
                            .toLongOrNull(16)
                            ?.takeIf { it in 1..0xffffffffL }
                            ?.toInt()
                            ?: throw PatchException(
                                "Invalid NewX drawer string resource id: $idText",
                            )
                    val entryName = element.attribute("name").orEmpty()
                    if (entryName.isBlank()) {
                        throw PatchException(
                            "NewX drawer string resource 0x${resourceId.toUInt().toString(16)} " +
                                "has no entry name",
                        )
                    }
                    val previousName = entries.put(resourceId, entryName)
                    if (previousName != null && previousName != entryName) {
                        throw PatchException(
                            "Duplicate NewX drawer string resource id " +
                                "0x${resourceId.toUInt().toString(16)}: " +
                                "$previousName and $entryName",
                        )
                    }
                }
                if (entries.isEmpty()) {
                    throw PatchException("NewX drawer resource table has no string entries")
                }
                DrawerResourceNames.replace(entries)
            }
        }
    }

private object NewXDrawerContentClassFingerprint : Fingerprint(
    definingClass = DRAWER_SCOPE,
    returnType = "V",
    custom = { method, _ ->
        val parameters = method.parameterTypes.map(CharSequence::toString)
        parameters.size >= 30 &&
            parameters.any {
                it.startsWith("Landroidx/compose/material3/") || it.startsWith(DRAWER_SCOPE)
            } &&
            parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
            parameters.count { it == "Ljava/util/List;" } >= 1 &&
            parameters.count { it == "Ljava/util/Map;" } == 1 &&
            parameters.count { it == "Z" } >= 2 &&
            parameters.lastOrNull() == "I" &&
            parameters.count { it == FUNCTION0_DESCRIPTOR } >= 10 &&
            parameters.count { it == FUNCTION1_DESCRIPTOR } >= 2 &&
            "Landroidx/compose/ui/Modifier;" in parameters
    },
)

private object NewXDrawerMenuItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    custom = { method, _ ->
        method.isDrawerRowRenderer() && method.parameterTypes.any { it.toString() == "Z" }
    },
)

// FOOTER ROWS: settings/help/feedback/media/imprint/debug pass their localized title.
private object NewXDrawerSharedFooterItemFingerprint : Fingerprint(
    definingClass = DRAWER_SCOPE,
    returnType = "V",
    custom = { method, _ ->
        val params = method.parameterTypes.map(CharSequence::toString)
        val isLegacyFooter = method.isDrawerRowRenderer() && params.none { it == "Z" }
        val isNewXFooter =
            params.size == 7 &&
                params[0].startsWith("Lcom/x/icons/") &&
                params[1].startsWith(DRAWER_SCOPE) &&
                params[2] == FUNCTION0_DESCRIPTOR &&
                params[3] == "Ljava/lang/String;" &&
                params[4] == "Landroidx/compose/ui/Modifier;" &&
                params[5] == COMPOSER_DESCRIPTOR &&
                params[6] == "I"
        isLegacyFooter || isNewXFooter
    },
)

private object NewXDrawerThemeToggleFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    custom = { method, _ ->
        method.parameterDescriptors().hasComposeShape(
            required =
                listOf(
                    COMPOSER_DESCRIPTOR,
                    "Landroidx/compose/ui/Modifier;",
                    FUNCTION0_DESCRIPTOR,
                ),
            first = "I",
        )
    },
    filters =
        listOf(
            methodCall(
                definingClass = "Landroidx/compose/material3/",
                parameters =
                    listOf(
                        "Lkotlin/jvm/functions/Function0;",
                        "Landroidx/compose/ui/Modifier;",
                        "Z",
                        "L",
                        "L",
                        "Lkotlin/jvm/functions/Function2;",
                        "Landroidx/compose/runtime/Composer;",
                        "I",
                        "I",
                    ),
                returnType = "V",
            ),
        ),
)

/**
 * GROK PATH: dedicated "Get Grok / Open Grok" button rendered without a title parameter.
 * Title is resolved inside from drawer_get_grok / drawer_open_grok, so filter by stable ID.
 */
private object NewXDrawerGrokButtonFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    // The 4-parameter no-title ABI is the only discriminator for this optional row; widening the
    // shape matched six sibling composables on 12.29.1.
    // newx-resolver-lint: allow rigid-signature
    parameters =
        listOf(
            "L",
            "Lkotlin/jvm/functions/Function1;",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
)

// Title-based filtering for rows that expose localized titles.
// TODO: Remove this path only when no supported release uses title-based drawer rows.
private fun MutableMethod.injectDrawerItemGuard(hiddenItems: MultiChoiceSettingDefinition) {
    val stringParamIndex =
        parameterTypes.indexOf("Ljava/lang/String;").takeIf { it >= 0 }
            ?: throw PatchException("NewX drawer item renderer does not have a String parameter: $this")
    val precedingRegisters =
        parameterTypes.subList(0, stringParamIndex).sumOf { type ->
            if (type == "J" || type == "D") 2 else 1
        }
    val titleParameterRegister = p0Register + precedingRegisters
    injectDrawerGuard(
        hiddenItems = hiddenItems,
        predicateMethod = "shouldHide",
        // The title is loaded from the parameter, so neither injected register may reuse it.
        readExcludedRegisters = listOf(titleParameterRegister),
        scratchExcludedRegisters = listOf(titleParameterRegister),
    ) { titleRegister ->
        move(titleRegister, titleParameterRegister, "Ljava/lang/String;")
    }
}

// ID-based filtering for title-less buttons (Grok, theme toggle).
private fun MutableMethod.injectFixedDrawerItemGuard(
    hiddenItems: MultiChoiceSettingDefinition,
    itemId: String,
) {
    val parameterRegisterCount =
        parameterTypes.sumOf { type -> if (type == "J" || type == "D") 2 else 1 }
    injectDrawerGuard(
        hiddenItems = hiddenItems,
        predicateMethod = "shouldHideId",
        // The title is a literal, so no app register has to survive the read; the scratch
        // register below keeps the exclusion list of the lookup it replaces.
        readExcludedRegisters = emptyList(),
        scratchExcludedRegisters = (0 until parameterRegisterCount).toList(),
    ) { titleRegister ->
        constString(titleRegister, itemId)
    }
}

/**
 * Hides the rendered row when [predicateMethod] classifies it as hidden. The setting read and the
 * guard both sit in front of the renderer's first instruction; [titleLoader] loads the row title
 * into the scratch register the guard allocated for it.
 *
 * @param readExcludedRegisters registers the setting read must leave alone, because the guard
 *   still reads them.
 * @param scratchExcludedRegisters registers the guard's scratch register must avoid, in addition
 *   to the read's register and the registers the liveness search reports as free.
 */
private fun MutableMethod.injectDrawerGuard(
    hiddenItems: MultiChoiceSettingDefinition,
    predicateMethod: String,
    readExcludedRegisters: List<Int>,
    scratchExcludedRegisters: List<Int>,
    titleLoader: Block.(titleRegister: Int) -> Unit,
) {
    if (instructions.isEmpty()) {
        throw PatchException("NewX drawer item renderer has no instructions: $this")
    }
    val read =
        hiddenItems.injectRead(
            method = this,
            index = 0,
            excludedRegisters = readExcludedRegisters,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    insertHook(
        index = read.nextIndex,
        // The read's set is live across the guard, so the scratch register must not reuse it.
        excludedRegisters = scratchExcludedRegisters + read.register,
        // The guard consumes the set the read block produced, so a branch that reaches the
        // original first instruction keeps jumping to it instead of into the guard.
        relocateBranchTargets = false,
    ) {
        val titleRegister = scratchRegister()
        titleLoader(titleRegister)
        invokeStatic(
            methodReference(
                "$DRAWER_ITEM_FILTER_DESCRIPTOR->$predicateMethod(Ljava/lang/String;Ljava/util/Set;)Z",
            ),
            titleRegister,
            read.register,
        )
        moveResult(titleRegister, "Z")
        ifEqz(titleRegister, Target.Original)
        returnVoid()
    }
}

private data class DrawerFooterTarget(
    val method: MutableMethod,
    val callIndex: Int,
    val call: Instruction3rc,
    val renderer: MethodReference,
)

private data class DrawerFooterCall(
    val index: Int,
    val call: Instruction3rc,
    val renderer: MethodReference,
)

private data class DrawerRendererCall(
    val method: MutableMethod,
    val index: Int,
    val call: Instruction3rc,
    val renderer: MethodReference,
)

private data class ResolvedDrawerFooterCalls(
    val dividerIndex: Int,
    val renderer: MethodReference,
    val calls: List<IndexedValue<Instruction3rc>>,
)

private fun MethodReference.stringResourceIdRegisterIndex(): Int? {
    if (returnType != "Ljava/lang/String;") return null
    val params = parameterTypes.map(CharSequence::toString)
    if (isStringResourceLookup()) return 1
    // R8 outlined stringResource wrapper: (groupKey: I, resId: I, composer, composer, Z) -> String
    if (params.size == 5 &&
        params[0] == "I" &&
        params[1] == "I" &&
        params[4] == "Z" &&
        (params[2] == COMPOSER_DESCRIPTOR || params[2].startsWith("Landroidx/compose/runtime/"))
    ) {
        return 1
    }
    return null
}

private fun List<Instruction>.extractAllStringResourceIds(): Set<Int> {
    val namespaces =
        indices.mapNotNull { index ->
            val inst = this[index]
            if (inst.opcode != Opcode.INVOKE_STATIC && inst.opcode != Opcode.INVOKE_STATIC_RANGE) return@mapNotNull null
            val ref = inst.getReference<MethodReference>() ?: return@mapNotNull null
            val resRegIdx = ref.stringResourceIdRegisterIndex() ?: return@mapNotNull null
            val regs = inst.registersUsed
            if (resRegIdx >= regs.size) return@mapNotNull null
            resolveIntegerLiteralOnCurrentPath(index, regs[resRegIdx])
        }.filter { it != 0 }.map { it ushr 16 }.toSet()

    val ids = linkedSetOf<Int>()
    indices.forEach { index ->
        val inst = this[index]
        if (inst.opcode != Opcode.INVOKE_STATIC && inst.opcode != Opcode.INVOKE_STATIC_RANGE) return@forEach
        val ref = inst.getReference<MethodReference>() ?: return@forEach
        val resRegIdx = ref.stringResourceIdRegisterIndex() ?: return@forEach
        val regs = inst.registersUsed
        if (resRegIdx >= regs.size) return@forEach
        resolveIntegerLiterals(index, regs[resRegIdx])
            .filterTo(ids) { id -> id != 0 && (namespaces.isEmpty() || id ushr 16 in namespaces) }
    }
    return ids
}

private fun List<Instruction>.resolveDrawerTitleResourceIds(
    callIndex: Int,
    call: Instruction3rc,
    renderer: MethodReference,
): Set<Int> {
    val parameters = renderer.parameterTypes.map(CharSequence::toString)
    val titleParameterIndices = parameters.indices.filter { parameters[it] == "Ljava/lang/String;" }
    if (titleParameterIndices.size != 1) {
        throw PatchException("NewX drawer row renderer has an unexpected title parameter: $renderer")
    }

    val resourceIds = linkedSetOf<Int>()
    for (lookupIndex in 0 until callIndex) {
        val lookupInstruction = this[lookupIndex]
        if (lookupInstruction.opcode != Opcode.INVOKE_STATIC &&
            lookupInstruction.opcode != Opcode.INVOKE_STATIC_RANGE
        ) continue
        val lookupReference = lookupInstruction.getReference<MethodReference>() ?: continue

        val resRegIdx = lookupReference.stringResourceIdRegisterIndex()
        if (resRegIdx != null) {
            val registers = lookupInstruction.registersUsed
            if (resRegIdx < registers.size) {
                resolveIntegerLiterals(lookupIndex, registers[resRegIdx])
                    .filterTo(resourceIds) { it != 0 && (it ushr 16) == 0x7f13 }
            }
        }
    }

    // 12.44+: drawer row titles are resolved upstream in a DRAWER_SCOPE helper
    // taking (Lcom/x/main/api/landing/...;, Z, Composer) -> Ljava/lang/String; (e.g. x;->v)
    if (resourceIds.isEmpty()) {
        Fingerprint(
            definingClass = DRAWER_SCOPE,
            returnType = "Ljava/lang/String;",
            custom = { method, _ ->
                val params = method.parameterTypes.map(CharSequence::toString)
                params.size == 3 &&
                    params[0].startsWith("Lcom/x/main/api/landing/") &&
                    params[1] == "Z" &&
                    params[2] == COMPOSER_DESCRIPTOR
            },
        ).matchAllOrNull().orEmpty().forEach { helperMatch ->
            resourceIds += helperMatch.method.instructions.extractAllStringResourceIds()
        }
    }

    return resourceIds
}
private fun resolveDrawerRendererCalls(renderer: MethodReference): List<DrawerRendererCall> {
    val expectedRegisterCount = renderer.parameterTypes.sumOf { type ->
        if (type.toString() == "J" || type.toString() == "D") 2 else 1
    }
    val rendererCall =
        methodCall(
            definingClass = renderer.definingClass,
            name = renderer.name,
            parameters = renderer.parameterTypes.map(CharSequence::toString),
            returnType = renderer.returnType,
            opcode = Opcode.INVOKE_STATIC_RANGE,
        )
    // A method can call the renderer more than once, so every call in each matched method is used.
    val calls = buildList {
        Fingerprint(
            definingClass = DRAWER_SCOPE,
            filters = listOf(rendererCall),
        ).matchAllOrNull().orEmpty().forEach { match ->
            val method = match.method
            method.instructions.forEachIndexed { index, instruction ->
                if (!rendererCall.matches(method, instruction)) return@forEachIndexed
                val call = instruction as Instruction3rc
                if (call.registerCount != expectedRegisterCount) {
                    throw PatchException(
                        "NewX drawer renderer call register count changed in $method @ $index: " +
                            "expected $expectedRegisterCount, found ${call.registerCount}",
                    )
                }
                add(DrawerRendererCall(method, index, call, call.reference as MethodReference))
            }
        }
    }
    if (calls.isEmpty()) {
        throw PatchException("Expected at least one NewX drawer renderer call for $renderer")
    }
    return calls
}

private fun resolveDrawerTitleResourceIds(
    renderer: MethodReference,
    calls: List<DrawerRendererCall>,
): List<Int> {
    val resourceIds = linkedSetOf<Int>()
    calls.forEach { call ->
        resourceIds += call.method.instructions.resolveDrawerTitleResourceIds(
            callIndex = call.index,
            call = call.call,
            renderer = renderer,
        )
    }
    if (resourceIds.isEmpty()) {
        throw PatchException(
            "NewX drawer renderer has no resolvable title string resources: $renderer; calls=" +
                calls.joinToString { "${it.method} @${it.index}" },
        )
    }
    return resourceIds.toList()
}

private fun resourceDrawerOptionId(resourceId: Int): String =
    DRAWER_RESOURCE_ITEM_ID_PREFIX + DrawerResourceNames.requireName(resourceId)

// Catalog ids for the editor shortcuts. Mirrors DrawerEditorFragment.SHORTCUTS.
private const val DRAWER_SHORTCUT_PIKO = "DRAWER_SHORTCUT_PIKO"
private const val PIKO_SETTINGS_ICON_DRAWABLE_ID_DESCRIPTOR =
    "$PIKO_SETTINGS_ICON_DESCRIPTOR->getDrawableId()I"
private const val DRAWER_REGISTER_ITEM_DESCRIPTOR =
    "$DRAWER_CATALOG_DESCRIPTOR->registerItem(Ljava/lang/String;I)V"
private const val SETTINGS_REGISTER_CHOICE_OPTION_RESOURCE_DESCRIPTOR =
    "$SETTINGS_REGISTRY_DESCRIPTOR->registerChoiceOptionResource(Ljava/lang/String;Ljava/lang/String;IZ)V"
private const val DRAWER_SHORTCUT_MESSAGES = "DRAWER_SHORTCUT_MESSAGES"
private const val DRAWER_SHORTCUT_GROK = "DRAWER_SHORTCUT_GROK"
private const val DRAWER_SHORTCUT_NOTIFICATIONS = "DRAWER_SHORTCUT_NOTIFICATIONS"

private data class DrawerCatalogEntry(
    val optionId: String,
    val iconField: FieldReference?,
)

/**
 * Maps every distinct drawer title to its row icon, when the icon resolves to a stable
 * icon field. Rows without a resolvable icon are still listed so the editor shows them
 * without an icon.
 */
private fun drawerCatalogEntries(calls: List<DrawerRendererCall>): List<DrawerCatalogEntry> {
    val iconsByResource = linkedMapOf<Int, FieldReference?>()
    calls.forEach { call ->
        val parameters = call.renderer.parameterTypes.map(CharSequence::toString)
        val iconParameterIndex =
            parameters.indexOfFirst { it.startsWith("Lcom/x/icons/") }.takeIf { it >= 0 } ?: 1
        val iconRegister = call.call.startRegister + iconParameterIndex
        val iconField =
            call.method.instructions.resolveIconField(call.index, iconRegister)
                ?.takeIf { field -> field.type.startsWith("Lcom/x/icons/") }
        call.method.instructions.resolveDrawerTitleResourceIds(
            callIndex = call.index,
            call = call.call,
            renderer = call.renderer,
        ).forEach { resourceId ->
            iconsByResource.putIfAbsent(resourceId, iconField)
            if (iconsByResource[resourceId] == null && iconField != null) {
                iconsByResource[resourceId] = iconField
            }
        }
    }
    return iconsByResource.map { (resourceId, iconField) ->
        DrawerCatalogEntry(resourceDrawerOptionId(resourceId), iconField)
    }
}

private fun injectDrawerCatalog(
    nativeEntries: List<DrawerCatalogEntry>,
    shortcutIcons: Map<String, FieldReference>,
) {
    val iconFields =
        (nativeEntries.mapNotNull { it.iconField } + shortcutIcons.values)
            .distinctBy(FieldReference::toString)
    val drawables = resolveIconDrawables(iconFields)
    SettingsRegistrationState.inject {
        nativeEntries.forEach { entry ->
            val drawable = entry.iconField?.let { drawables[it.toString()] } ?: 0
            emitDrawerCatalogEntry(entry.optionId, drawable)
        }
        shortcutIcons.forEach { (optionId, iconField) ->
            emitDrawerCatalogEntry(optionId, drawables.getValue(iconField.toString()))
        }
        // The Piko drawable is added by the patch, so its id only exists at runtime.
        constString(0, DRAWER_SHORTCUT_PIKO)
        invokeStatic(methodReference(PIKO_SETTINGS_ICON_DRAWABLE_ID_DESCRIPTOR))
        moveResult(1, "I")
        invokeStatic(methodReference(DRAWER_REGISTER_ITEM_DESCRIPTOR), 0, 1)
        listOf("GROK", "THEME_TOGGLE").forEach { optionId ->
            emitDrawerCatalogEntry(optionId, 0)
        }
    }
}

private fun Block.emitDrawerCatalogEntry(optionId: String, drawable: Int) {
    constString(0, optionId)
    constInt(1, drawable)
    invokeStatic(methodReference(DRAWER_REGISTER_ITEM_DESCRIPTOR), 0, 1)
}

private fun injectDynamicDrawerOptions(
    hiddenItems: MultiChoiceSettingDefinition,
    resourceIds: List<Int>,
) {
    if (resourceIds.isEmpty()) {
        throw PatchException("Expected at least one dynamic NewX drawer title resource")
    }
    SettingsRegistrationState.inject {
        resourceIds.distinct().forEach { resourceId ->
            constString(0, hiddenItems.id)
            constString(1, resourceDrawerOptionId(resourceId))
            constInt(2, resourceId)
            constInt(3, 0)
            invokeStatic(
                methodReference(SETTINGS_REGISTER_CHOICE_OPTION_RESOURCE_DESCRIPTOR),
                0,
                1,
                2,
                3,
            )
        }
    }
}

private fun MethodReference.isDrawerFooterDivider(renderer: MethodReference): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    val isFourParamShape =
        parameters.size == 4 &&
            (parameters[0].startsWith("Landroidx/compose/material3/") ||
                parameters[0].startsWith(DRAWER_SCOPE)) &&
            parameters[1] == FUNCTION1_DESCRIPTOR &&
            parameters[2] == COMPOSER_DESCRIPTOR &&
            parameters[3] == "I"
    val isFiveParamShape =
        parameters.size == 5 &&
            parameters[0].startsWith(DRAWER_SCOPE) &&
            parameters[1] == FUNCTION1_DESCRIPTOR &&
            parameters[2].startsWith(DRAWER_SCOPE) &&
            parameters[3] == COMPOSER_DESCRIPTOR &&
            parameters[4] == "I"
    return definingClass == renderer.definingClass &&
        returnType == "V" &&
        (isFourParamShape || isFiveParamShape)
}

private fun MutableMethod.findDrawerFooterCalls(
    renderer: MethodReference,
): ResolvedDrawerFooterCalls? {
    val methodInstructions = instructions.toList()
    val dividerIndices =
        methodInstructions.indices.filter { index ->
            methodInstructions[index]
                .getReference<MethodReference>()
                ?.isDrawerFooterDivider(renderer) == true
        }
    val dividerIndex =
        requireAtMostOne(
            label = "NewX drawer footer divider",
            candidates = dividerIndices,
        ) ?: return null
    val footerCalls = methodInstructions.indices.mapNotNull { index ->
        if (index <= dividerIndex) return@mapNotNull null
        val instruction = methodInstructions[index]
        if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE) return@mapNotNull null
        val call = instruction as? Instruction3rc ?: return@mapNotNull null
        val footerRenderer = instruction.getReference<MethodReference>()
            ?: return@mapNotNull null
        val isRequestedRenderer =
            footerRenderer.toSmaliDescriptor() == renderer.toSmaliDescriptor()
        if (footerRenderer.definingClass != renderer.definingClass ||
            (!isRequestedRenderer && !footerRenderer.isDrawerRowRenderer()) ||
            call.registerCount != footerRenderer.parameterTypes.size
        ) {
            return@mapNotNull null
        }
        DrawerFooterCall(
            index = index,
            call = call,
            renderer = footerRenderer,
        )
    }
    if (footerCalls.isEmpty()) return null
    val footerRenderer =
        requireExactlyOne(
            label = "NewX drawer footer renderer",
            candidates = footerCalls.distinctBy { call -> call.renderer.toSmaliDescriptor() },
            describe = { call -> call.renderer.toSmaliDescriptor() },
        ).renderer
    return ResolvedDrawerFooterCalls(
        dividerIndex = dividerIndex,
        renderer = footerRenderer,
        calls = footerCalls.map { call ->
            IndexedValue(call.index, call.call)
        }
    )
}

/** Proves which footer call receives the resolved settings icon, without relying on call order. */
private fun List<Instruction>.hasFieldArgument(
    callIndex: Int,
    call: Instruction3rc,
    renderer: MethodReference,
    parameterIndex: Int,
    field: FieldReference,
    lowerBound: Int,
): Boolean {
    val argumentRegister =
        call.startRegister +
            renderer.parameterTypes.take(parameterIndex).sumOf { type ->
                if (type.toString() == "J" || type.toString() == "D") 2 else 1
            }
    var register = argumentRegister
    for (index in callIndex - 1 downTo lowerBound) {
        val instruction = this[index]
        if (instruction.opcode == Opcode.SGET_OBJECT) {
            val destination = (instruction as? OneRegisterInstruction)?.registerA
            if (destination != register) continue
            val reference = instruction.getReference<FieldReference>() ?: return false
            return reference.toString() == field.toString()
        }
        if (instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return false
            if (move.registerA == register) {
                register = move.registerB
                continue
            }
        }
        if (instruction.writesObjectRegister(register)) return false
    }
    return false
}

private fun resolveDrawerFooterTarget(
    renderer: MethodReference,
    settingsIconField: FieldReference,
): DrawerFooterTarget {
    val candidates = buildList {
        classDefForEach { classDef ->
            if (!classDef.type.startsWith(DRAWER_SCOPE)) return@classDefForEach
            if (!classDef.interfaces.any { it.toString() in setOf(FUNCTION2_DESCRIPTOR, FUNCTION3_DESCRIPTOR) }) {
                return@classDefForEach
            }

            val mutableClass = mutableClassDefBy(classDef.type)
            mutableClass.methods
                .filter { method ->
                    val parameterTypes = method.parameterTypes.map(CharSequence::toString)
                    method.name == "invoke" &&
                        method.returnType == OBJECT_DESCRIPTOR &&
                        (parameterTypes == List(2) { OBJECT_DESCRIPTOR } ||
                            parameterTypes == List(3) { OBJECT_DESCRIPTOR })
                }.forEach { method ->
                    method.findDrawerFooterCalls(renderer)?.let { footer ->
                        val iconParameterIndex = footer.renderer.parameterTypes.indexOfFirst { type ->
                            type.toString().startsWith("Lcom/x/icons/")
                        }
                        if (iconParameterIndex < 0) {
                            throw PatchException(
                                "NewX drawer footer renderer has no icon parameter: ${footer.renderer}",
                            )
                        }
                        val settingsCalls = footer.calls.filter { (callIndex, call) ->
                            method.instructions.hasFieldArgument(
                                callIndex = callIndex,
                                call = call,
                                renderer = footer.renderer,
                                parameterIndex = iconParameterIndex,
                                field = settingsIconField,
                                lowerBound = footer.dividerIndex + 1,
                            )
                        }
                        if (settingsCalls.size != 1) {
                            throw PatchException(
                                "Expected exactly one NewX drawer settings-icon footer call in candidate $method, " +
                                    "found ${settingsCalls.size}; all footer calls: " +
                                    footer.calls.joinToString { "${it.index}:${it.value}" },
                            )
                        }
                        val footerCall = settingsCalls.single()
                        add(
                            DrawerFooterTarget(
                                method = method,
                                callIndex = footerCall.index,
                                call = footerCall.value,
                                renderer = footer.renderer,
                            ),
                        )
                    }
                }
        }
    }
    if (candidates.size == 1) {
        return candidates.single()
    }

    // 12.44+: footer rows are rendered dynamically inside a DRAWER_SCOPE Function3 lambda (h.smali)
    // calling x;->i(Icon, y, Function0, String, Modifier, Composer, I)
    val dynamicCalls = buildList {
        classDefForEach { classDef ->
            if (!classDef.type.startsWith(DRAWER_SCOPE)) return@classDefForEach
            if (!classDef.interfaces.any { it.toString() in setOf(FUNCTION2_DESCRIPTOR, FUNCTION3_DESCRIPTOR) }) {
                return@classDefForEach
            }
            val mutableClass = mutableClassDefBy(classDef.type)
            mutableClass.methods
                .filter { m -> m.name == "invoke" && m.returnType == OBJECT_DESCRIPTOR }
                .forEach { method ->
                    method.instructions.forEachIndexed { index, inst ->
                        if (inst.opcode != Opcode.INVOKE_STATIC_RANGE) return@forEachIndexed
                        val call = inst as? Instruction3rc ?: return@forEachIndexed
                        val ref = inst.getReference<MethodReference>() ?: return@forEachIndexed
                        if (ref.toSmaliDescriptor() == renderer.toSmaliDescriptor() &&
                            call.registerCount == renderer.parameterTypes.size
                        ) {
                            add(DrawerFooterTarget(method, index, call, ref))
                        }
                    }
                }
        }
    }
    if (dynamicCalls.size == 1) {
        return dynamicCalls.single()
    }

    throw PatchException(
        "Expected one NewX drawer content lambda for ${renderer}, found " +
            "${candidates.size} static / ${dynamicCalls.size} dynamic",
    )
}

private fun MethodReference.toSmaliDescriptor(): String =
    "${definingClass}->${name}(${parameterTypes.joinToString("")})${returnType}"

private fun MutableMethod.injectPikoSettingsDrawerItem(
    target: DrawerFooterTarget,
    renderer: MethodReference,
    settingsIconType: String,
    settingsIconField: FieldReference,
    showPikoSettingsInDrawer: ToggleSettingDefinition,
) {
    val iconIndex = renderer.parameterTypes.indexOfFirst { it.toString().startsWith("Lcom/x/icons/") }
    val matchIcon =
        if (iconIndex >= 0) {
            iconIndex to settingsIconField
        } else {
            null
        }
    injectAdditionalDrawerRow(
        target = target,
        renderer = renderer,
        icon = DrawerRowIcon.Accessor(pikoSettingsIconGetter(settingsIconType)),
        toggle = showPikoSettingsInDrawer,
        titleDescriptor = "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitle()Ljava/lang/String;",
        clickDescriptor = "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()$FUNCTION0_DESCRIPTOR",
        matchIcon = matchIcon,
    )
}

private fun MutableMethod.injectAdditionalDrawerRow(
    target: DrawerFooterTarget,
    renderer: MethodReference,
    icon: DrawerRowIcon,
    toggle: ToggleSettingDefinition?,
    titleDescriptor: String,
    clickDescriptor: String,
    registerConstraint: SettingReadRegisterConstraint = SettingReadRegisterConstraint.FOUR_BIT,
    matchIcon: Pair<Int, FieldReference>? = null,
) {
    injectSnapshotDrawerRow(
        callIndex = target.callIndex,
        startRegister = target.call.startRegister,
        registerCount = target.call.registerCount,
        renderer = renderer,
        icon = icon,
        toggle = toggle,
        titleDescriptor = titleDescriptor,
        clickDescriptor = clickDescriptor,
        registerConstraint = registerConstraint,
        matchIcon = matchIcon,
    )
}

/** Where an injected drawer row reads its icon: an app icon field or a static icon accessor. */
private sealed interface DrawerRowIcon {
    val type: String

    data class Field(val reference: FieldReference) : DrawerRowIcon {
        override val type: String get() = reference.type
    }

    data class Accessor(val getter: MethodReference) : DrawerRowIcon {
        override val type: String get() = getter.returnType
    }
}

/**
 * Emits one extra drawer row reusing an executed row call's registers. Only title, icon,
 * and click are replaced; the shared parameters keep values the app itself populated, so
 * the emission must sit at the same call site. The title null-guard skips emission when
 * the anchor call did not execute on this path.
 */
private fun MutableMethod.injectSnapshotDrawerRow(
    callIndex: Int,
    startRegister: Int,
    registerCount: Int,
    renderer: MethodReference,
    icon: DrawerRowIcon,
    toggle: ToggleSettingDefinition?,
    titleDescriptor: String,
    clickDescriptor: String,
    registerConstraint: SettingReadRegisterConstraint = SettingReadRegisterConstraint.FOUR_BIT,
    matchIcon: Pair<Int, FieldReference>? = null,
) {
    val parameters = renderer.parameterTypes.map(CharSequence::toString)
    val titleIndices = parameters.indices.filter { parameters[it] == "Ljava/lang/String;" }
    val clickIndices = parameters.indices.filter { parameters[it] == FUNCTION0_DESCRIPTOR }
    val composerIndices = parameters.indices.filter { parameters[it] == COMPOSER_DESCRIPTOR }
    val iconIndices = parameters.indices.filter { index -> parameters[index].startsWith("Lcom/x/icons/") }
    if (titleIndices.size != 1 || clickIndices.size != 1 || composerIndices.size != 1 || iconIndices.size != 1) {
        throw PatchException("NewX drawer footer renderer has an unexpected parameter shape: ${renderer}")
    }

    val titleIndex = titleIndices.single()
    val clickIndex = clickIndices.single()
    val iconIndex = iconIndices.single()
    if (icon.type != parameters[iconIndex]) {
        throw PatchException(
            "NewX drawer icon type changed: renderer=${parameters[iconIndex]}, " +
                "icon=$icon",
        )
    }

    val endRegister = startRegister + registerCount - 1
    if (startRegister < 0 || endRegister > 255) {
        throw PatchException(
            "NewX drawer row registers are outside v0..v255: " +
                "v$startRegister..v$endRegister",
        )
    }
    if (registerCount != parameters.size) {
        throw PatchException(
            "NewX drawer row register count changed: expected ${parameters.size}, found $registerCount",
        )
    }

    if (callIndex + 1 >= instructions.size) {
        throw PatchException("NewX drawer row injection point has no continuation: $this")
    }
    // A null toggle skips the setting read entirely: the title provider returns null while
    // disabled, and the null title skips the row. Dense call sites may have no free register.
    val settingRead =
        toggle?.injectRead(
            method = this,
            index = callIndex + 1,
            excludedRegisters = (startRegister..endRegister).toList(),
            registerConstraint = registerConstraint,
        )
    val insertionIndex = settingRead?.nextIndex ?: (callIndex + 1)
    val titleRegister = startRegister + titleIndex
    val iconRegister = startRegister + iconIndex
    val clickRegister = startRegister + clickIndex
    insertHook(
        index = insertionIndex,
        excludedRegisters = (startRegister..endRegister).toList(),
        // The block renders with the anchor call's register window, so a branch that skipped the
        // anchor has to keep jumping to the original instruction instead of into the block.
        relocateBranchTargets = false,
    ) {
        if (matchIcon != null) {
            val (matchedIconIndex, expectedIconField) = matchIcon
            val actualIconReg = startRegister + matchedIconIndex
            val expectedIconReg = scratchRegister()
            sget(expectedIconReg, expectedIconField)
            ifNe(actualIconReg, expectedIconReg, Target.Original)
        }
        if (settingRead != null) {
            ifEqz(settingRead.register, Target.Original)
        }
        invokeStatic(methodReference(titleDescriptor))
        moveResult(titleRegister, "Ljava/lang/String;")
        ifEqz(titleRegister, Target.Original)
        when (icon) {
            is DrawerRowIcon.Field -> sget(iconRegister, icon.reference)
            is DrawerRowIcon.Accessor -> {
                invokeStatic(icon.getter)
                moveResult(iconRegister, icon.type)
            }
        }
        invokeStatic(methodReference(clickDescriptor))
        moveResult(clickRegister, FUNCTION0_DESCRIPTOR)
        invokeStatic(renderer, *(startRegister..endRegister).toList().toIntArray())
    }
}

/**
 * Finds the Profile menu row: the only unconditionally composed top-section row, which makes
 * its registers safe to snapshot for rows emitted later at the end of the menu section.
 */
private data class ProfileRowAnchor(
    val target: DrawerFooterTarget,
    val renderer: MethodReference,
)

/**
 * Finds the Profile menu row among title-based row renderers. Menu rows may use a different
 * renderer than the footer rows, so the anchor carries its own renderer for the snapshot
 * and the emission.
 */
private fun MutableMethod.findProfileRowCall(): ProfileRowAnchor {
    val dynamicCandidates = buildList {
        classDefForEach { classDef ->
            if (!classDef.type.startsWith(DRAWER_SCOPE)) return@classDefForEach
            val mutableClass = mutableClassDefBy(classDef.type)
            mutableClass.methods
                .filter { m -> m.parameterTypes.firstOrNull()?.toString() == "Lcom/x/main/api/landing/x;" }
                .forEach { method ->
                    method.instructions.forEachIndexed { index, inst ->
                        if (inst.opcode != Opcode.INVOKE_STATIC_RANGE) return@forEachIndexed
                        val call = inst as? Instruction3rc ?: return@forEachIndexed
                        val renderer = inst.getReference<MethodReference>() ?: return@forEachIndexed
                        if (!renderer.isDrawerRowRenderer() || call.registerCount != renderer.parameterTypes.size) return@forEachIndexed
                        add(ProfileRowAnchor(DrawerFooterTarget(method, index, call, renderer), renderer))
                    }
                }
        }
    }
    if (dynamicCandidates.size == 1) return dynamicCandidates.single()
    val profileTitleId = resourceId(ResourceType.STRING, "drawer_profile_title")
    val candidates = instructions.indices.mapNotNull { index ->
        val instruction = instructions[index]
        if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE) return@mapNotNull null
        val call = instruction as? Instruction3rc ?: return@mapNotNull null
        val renderer = instruction.getReference<MethodReference>() ?: return@mapNotNull null
        if (!renderer.isDrawerRowRenderer()) return@mapNotNull null
        if (call.registerCount != renderer.parameterTypes.size) {
            throw PatchException(
                "NewX drawer renderer call register count changed in $this @ $index: " +
                    "expected ${renderer.parameterTypes.size}, found ${call.registerCount}",
            )
        }
        val titleId = instructions.resolveTitleResourceIdAtRowCall(index, call.startRegister)
        if (titleId?.toLong() != profileTitleId) return@mapNotNull null
        IndexedValue(index, Pair(call, renderer))
    }
    val (callIndex, row) =
        requireExactlyOne("NewX drawer profile row", candidates) { "${it.index}:${it.value.first}" }
    return ProfileRowAnchor(
        target = DrawerFooterTarget(
            method = this,
            callIndex = callIndex,
            call = row.first,
            renderer = row.second,
        ),
        renderer = row.second,
    )
}

private data class DrawerTabNavigation(
    val componentClass: String,
    val enumType: String,
    val tabChangeFunctionField: FieldReference,
    val closerField: FieldReference,
    val closerMethod: MethodReference,
    val closerArgField: FieldReference,
)

private val CASE_END_OPCODES = setOf(Opcode.RETURN_OBJECT, Opcode.RETURN_VOID, Opcode.THROW)

private val INVOKE_INTERFACE_OPCODES = listOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE)

private fun List<Instruction>.resolveRegisterField(
    register: Int,
    beforeIndex: Int,
): FieldReference? {
    for (index in beforeIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.destinationRegisterOrNull() != register) continue
        if (instruction.opcode != Opcode.IGET_OBJECT) return null
        return instruction.getReference<FieldReference>()
    }
    return null
}

/**
 * Resolves the tab-open contract for the drawer shortcuts: the captured tab change function, plus
 * the drawer-close triple extracted from the Communities drawer click (tab change, then close).
 * The Communities case proves both the close field on the tab component and the close argument
 * without hardcoding obfuscated owners.
 */
private fun resolveDrawerTabNavigation(
    tabData: NewXNavBarTabData,
): DrawerTabNavigation {
    // Read-only discovery pass; see the navigation bar patch for why mutable proxies are avoided.
    val dispatcherMatch =
        Fingerprint(
            filters = listOf(
                // Communities case.
                fieldAccess(
                    opcode = Opcode.SGET_OBJECT,
                    definingClass = tabData.navigationType,
                    name = "COMMUNITIES",
                    type = tabData.navigationType
                ),
                // Tab change: a captured Function1 field on the drawer component.
                methodCall(
                    definingClass = FUNCTION1_DESCRIPTOR,
                    name = "invoke",
                    parameters = listOf(OBJECT_DESCRIPTOR),
                    returnType = OBJECT_DESCRIPTOR,
                    opcodes = INVOKE_INTERFACE_OPCODES,
                ),
                // Drawer close.
                methodCall(
                    parameters = listOf(OBJECT_DESCRIPTOR),
                    returnType = "V",
                    opcodes = INVOKE_INTERFACE_OPCODES,
                ),
            )
        ).matchAll(1..1).single()
    val dispatcher = dispatcherMatch.originalMethod
    val dispatcherInstructions = dispatcher.instructions.toList()
    val communitiesIndex = dispatcherMatch.instructionMatches[0].index
    val tabChangeMatch = dispatcherMatch.instructionMatches[1]
    val tabChangeIndex = tabChangeMatch.index
    val closerMatch = dispatcherMatch.instructionMatches[2]
    val closerIndex = closerMatch.index
    // The Communities case ends at the first return. A tab change or closer after it belongs to
    // a later click handler.
    val caseEndIndex = dispatcher.indexOfFirstInstruction(communitiesIndex) { opcode in CASE_END_OPCODES }
    if (caseEndIndex in 0..<closerIndex) {
        throw PatchException("NewX Communities drawer click has no tab change and close call: $dispatcher")
    }
    val tabChangeFunctionField =
        dispatcherInstructions.resolveRegisterField(
            tabChangeMatch.instruction.registersUsed.first(),
            tabChangeIndex,
        ) ?: throw PatchException("NewX Communities drawer click has no tab change function: $dispatcher")
    val closerRegisters = closerMatch.instruction.registersUsed
    if (closerRegisters.size != 2) {
        throw PatchException(
            "NewX drawer close call has an unexpected register shape in $dispatcher @ $closerIndex",
        )
    }
    val closerObjectRegister = closerRegisters[0]
    val closerArgRegister = closerRegisters[1]
    val closerMethod = closerMatch.methodReference
    val closerField =
        requireExactlyOne(
            label = "NewX drawer close field",
            candidates = dispatcherInstructions.indices.filter { index ->
                if (index >= closerIndex) return@filter false
                val instruction = dispatcherInstructions[index]
                if (instruction.opcode != Opcode.IGET_OBJECT) return@filter false
                val destination = (instruction as? TwoRegisterInstruction)?.registerA
                destination == closerObjectRegister &&
                    dispatcherInstructions.valueReachesRegister(
                        index,
                        destination,
                        closerIndex,
                        closerObjectRegister,
                    )
            },
        ) { "$it:${dispatcherInstructions[it].getReference<FieldReference>()}" }
            .let { dispatcherInstructions[it].getReference<FieldReference>() }
            ?: throw PatchException("NewX drawer close field has no field reference: $dispatcher")
    // The capture target is the close-field owner.
    val componentClass = closerField.definingClass
    if (closerField.type != closerMethod.definingClass) {
        throw PatchException(
            "NewX drawer close field type changed: field=${closerField.type}, " +
                "method=${closerMethod.definingClass}",
        )
    }
    // The close argument is loaded once in the prologue before the dispatch switch and shared
    // by every drawer click; the switch jumps straight into the Communities case block, so the
    // prologue value is intact on the real path. A dataflow check cannot prove this because
    // other case blocks reuse the same registers, so the prologue load is resolved by position
    // and cross-checked against the stable API package instead.
    // Older releases load the close argument inline in the case block; newer ones hoist it
    // into the prologue before the dispatch switch. A dataflow check cannot prove the prologue
    // load because other case blocks reuse the same registers, so each shape is resolved by
    // position and the combined cardinality is asserted.
    val prologueArgFields = dispatcherInstructions.indices.mapNotNull { index ->
        if (index >= closerIndex) return@mapNotNull null
        val instruction = dispatcherInstructions[index]
        if (instruction.opcode != Opcode.SGET_OBJECT) return@mapNotNull null
        val destination = (instruction as? OneRegisterInstruction)?.registerA ?: return@mapNotNull null
        if (destination != closerArgRegister) return@mapNotNull null
        instruction.getReference<FieldReference>()
    }
    val inlineArgField = dispatcherInstructions.resolveIconField(closerIndex, closerArgRegister)
    val closerArgField =
        requireExactlyOne(
            label = "NewX drawer close argument",
            candidates =
                (prologueArgFields + listOfNotNull(inlineArgField))
                    .filter { it.type.startsWith("Lcom/x/main/api/") }
                    .distinctBy(FieldReference::toString),
            describe = { it.toString() },
        )
    if (!closerArgField.type.startsWith("Lcom/x/main/api/")) {
        throw PatchException(
            "NewX drawer close argument is not a stable API type: $closerArgField",
        )
    }
    return DrawerTabNavigation(
        componentClass = componentClass,
        enumType = tabData.navigationType,
        tabChangeFunctionField = tabChangeFunctionField,
        closerField = closerField,
        closerMethod = closerMethod,
        closerArgField = closerArgField,
    )
}

/** Captures the tab component after its constructor finishes so shortcuts can open tabs. */
private fun hookDrawerTabComponent(componentClass: String) {
    Fingerprint(
        definingClass = componentClass,
        name = "<init>",
        filters = listOf(
            methodCall(
                definingClass = classDefBy(componentClass).superclass!!,
                name = "<init>",
                opcodes = listOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE),
            ),
        ),
    ).matchAll().forEach { match ->
        val constructor = match.method
        constructor.insertHook(
            index = match.instructionMatches.first().index + 1,
            // Every path that reaches the end of the super call must capture the component. The
            // block only reads p0, so relocating a branch onto it cannot run with undefined state.
            relocateBranchTargets = true,
        ) {
            invokeStatic(
                methodReference("$DRAWER_TAB_OPENER_DESCRIPTOR->setComponent($OBJECT_DESCRIPTOR)V"),
                constructor.p0Register,
            )
        }
    }
}

/** Replaces the shortcut bodies with direct tab-open plus drawer-close invokes. */
private fun replaceDrawerTabOpenerBodies(navigation: DrawerTabNavigation) {
    val openerClass = mutableClassDefBy(DRAWER_TAB_OPENER_DESCRIPTOR)
    replaceDrawerTabOpenerBody(openerClass, "openMessages", "DM", navigation)
    replaceDrawerTabOpenerBody(openerClass, "openGrok", "GROK", navigation)
    replaceDrawerTabOpenerBody(openerClass, "openNotifications", "NOTIFICATIONS", navigation)
}

private fun replaceDrawerTabOpenerBody(
    openerClass: MutableClass,
    name: String,
    enumEntry: String,
    navigation: DrawerTabNavigation,
) {
    val original =
        Fingerprint(
            definingClass = DRAWER_TAB_OPENER_DESCRIPTOR,
            name = name,
            returnType = "V",
            parameters = emptyList(),
        ).match().method
    val expanded =
        original.cloneMutable(
            additionalRegisters = original.numberOfParameterRegisters + 3,
        )
    openerClass.methods.remove(original)
    openerClass.methods.add(expanded)
    val implementation =
        expanded.implementation
            ?: throw PatchException("DrawerTabOpener.$name has no implementation")
    while (implementation.instructions.isNotEmpty()) {
        implementation.removeInstruction(implementation.instructions.lastIndex)
    }
    expanded.insertHook(
        // The body was emptied above, so index 0 sits at the end of the instruction list and no
        // branch label can be attached to it.
        index = 0,
        relocateBranchTargets = false,
    ) {
        sget(0, fieldReference("${navigation.enumType}->$enumEntry:${navigation.enumType}"))
        sget(1, fieldReference("$DRAWER_TAB_OPENER_DESCRIPTOR->component:$OBJECT_DESCRIPTOR"))
        ifEqz(1, Target.Local(DRAWER_TAB_DONE_LABEL))
        checkCast(1, navigation.componentClass)
        iget(2, 1, navigation.tabChangeFunctionField)
        invokeInterface(
            methodReference(
                "$FUNCTION1_DESCRIPTOR->invoke($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR",
            ),
            2,
            0,
        )
        sget(0, navigation.closerArgField)
        iget(2, 1, navigation.closerField)
        invokeInterface(navigation.closerMethod, 2, 0)
        label(DRAWER_TAB_DONE_LABEL)
        returnVoid()
    }
}

@Suppress("unused")
val customizeNewXDrawerPatch =
    patch(
        name = "NewX: Customize drawer items",
        description = "Lets you hide selected items from the NewX navigation drawer, and optionally add Messages and Grok shortcuts.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")
        dependsOn(newXSettingsPatch)
        dependsOn(drawerResourceNamesPatch)

        newXCustomScreen(
            id = "newx.drawer.editor",
            category = Categories.NAVIGATION,
            strings = settingStrings("piko_newx_drawer_editor"),
            order = 90,
            fragmentClassDescriptor = DRAWER_EDITOR_DESCRIPTOR,
            iconResourceName = "ic_vector_menu",
        )

        val showPikoSettingsInDrawer =
            newXToggle(
                id = "newx.navigation.show_piko_settings_in_drawer",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_show_piko_settings_in_drawer"),
                order = 100,
                defaultValue = true,
                visible = false,
            )

        val showMessagesInDrawer =
            newXToggle(
                id = "newx.navigation.show_messages_in_drawer",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_show_messages_in_drawer"),
                order = 110,
                defaultValue = false,
                visible = false,
            )

        val showGrokInDrawer =
            newXToggle(
                id = "newx.navigation.show_grok_in_drawer",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_show_grok_in_drawer"),
                order = 120,
                defaultValue = false,
                visible = false,
            )

        val showNotificationsInDrawer =
            newXToggle(
                id = "newx.navigation.show_notifications_in_drawer",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_show_notifications_in_drawer"),
                order = 130,
                defaultValue = false,
                visible = false,
            )

        val hiddenItems =
            newXMultiChoice(
                id = "newx.content.hidden_drawer_items",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_drawer"),
                order = 200,
                defaultValue = emptySet(),
                visible = false,
                options =
                    listOf(
                        choice("GROK", "piko_newx_drawer_grok"),
                        choice("THEME_TOGGLE", "piko_newx_drawer_theme_toggle"),
                    ),
            )

        execute {
            val classMatches = NewXDrawerContentClassFingerprint.scopedMatchAll()
            if (classMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX drawer content class, found ${classMatches.size}: " +
                        classMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val menuMatches =
                NewXDrawerMenuItemFingerprint.scopedMatchAllOrNull().orEmpty()
                    .distinctBy { it.originalMethod.toString() }
            val menuMatch =
                requireExactlyOne(
                    label = "NewX drawer menu item renderer",
                    candidates = menuMatches,
                    describe = { it.originalMethod.toString() },
                )
            val menuRenderer =
                ImmutableMethodReference(
                    menuMatch.originalMethod.definingClass,
                    menuMatch.originalMethod.name,
                    menuMatch.originalMethod.parameterTypes.map(CharSequence::toString),
                    menuMatch.originalMethod.returnType,
                )
            val menuCalls = resolveDrawerRendererCalls(menuRenderer)
            val menuResourceIds = resolveDrawerTitleResourceIds(menuRenderer, menuCalls)
            menuMatch.method.injectDrawerItemGuard(hiddenItems)

            // FOOTER ROWS: settings/help/feedback/media/imprint/debug render with a title.
            val footerMatches =
                NewXDrawerSharedFooterItemFingerprint.scopedMatchAllOrNull().orEmpty()
                    .distinctBy { it.originalMethod.toString() }
            if (footerMatches.size > 1) {
                throw PatchException(
                    "Expected one NewX drawer footer row renderer, found " +
                        "${footerMatches.size}: ${footerMatches.joinToString { it.originalMethod.toString() }}",
                )
            }
            val footerRendererMatch =
                footerMatches.singleOrNull()?.also { it.method.injectDrawerItemGuard(hiddenItems) }
                    ?: menuMatch
            val footerRenderer =
                ImmutableMethodReference(
                    footerRendererMatch.originalMethod.definingClass,
                    footerRendererMatch.originalMethod.name,
                    footerRendererMatch.originalMethod.parameterTypes.map(CharSequence::toString),
                    footerRendererMatch.originalMethod.returnType,
                )
            val settingsIconTypes =
                footerRenderer.parameterTypes
                    .map(CharSequence::toString)
                    .filter { it.startsWith("Lcom/x/icons/") }
            if (settingsIconTypes.size != 1) {
                throw PatchException(
                    "NewX drawer footer renderer has ${settingsIconTypes.size} icon parameters: " +
                        footerRenderer,
                )
            }
            val settingsIconType = settingsIconTypes.single()
            val settingsIconField = resolveSettingsIconField(settingsIconType)
            val footerTarget = resolveDrawerFooterTarget(footerRenderer, settingsIconField)
            // Footer rows can be nested in conditional helpers such as the Grok bot menu.
            // Discover every call to the resolved renderer, not only direct calls in the
            // lambda that owns the settings footer divider.
            val footerRendererCalls = resolveDrawerRendererCalls(footerTarget.renderer)
            val footerResourceIds =
                resolveDrawerTitleResourceIds(
                    renderer = footerTarget.renderer,
                    calls = footerRendererCalls,
                )
            injectDynamicDrawerOptions(
                hiddenItems = hiddenItems,
                resourceIds = (menuResourceIds + footerResourceIds).distinct(),
            )
            val tabDataMatch =
                requireExactlyOne(
                    label = "NewX tabData builder",
                    candidates = NewXTabDataFingerprint.scopedMatchAll(),
                )
            val tabData = validateNewXNavBarTabData(tabDataMatch)
            val tabIconFields =
                runCatching { resolveNavBarItemContent(tabData).tabIconFields }
                    .getOrElse { resolveTabIconFieldsFromHelper(tabData.navigationType) }
            val messagesIcon =
                tabIconFields["DM"]
                    ?: throw PatchException("NewX Messages tab icon was not resolved")
            val grokIcon =
                tabIconFields["GROK"]
                    ?: throw PatchException("NewX Grok tab icon was not resolved")
            val notificationsIcon =
                tabIconFields["NOTIFICATIONS"]
                    ?: throw PatchException("NewX Notifications tab icon was not resolved")
            injectDrawerCatalog(
                nativeEntries = drawerCatalogEntries(menuCalls + footerRendererCalls),
                shortcutIcons =
                    mapOf(
                        DRAWER_SHORTCUT_MESSAGES to messagesIcon,
                        DRAWER_SHORTCUT_GROK to grokIcon,
                        DRAWER_SHORTCUT_NOTIFICATIONS to notificationsIcon,
                    ),
            )
            val tabNavigation = resolveDrawerTabNavigation(tabData)
            hookDrawerTabComponent(tabNavigation.componentClass)
            replaceDrawerTabOpenerBodies(tabNavigation)
            // Emit Messages and Grok directly after the unconditional Profile row, borrowing
            // its registers. Composer changed-flags are only valid at the call site that
            // produced them, so emitting anywhere else renders nothing. Injections run in
            // descending index order so earlier indices stay valid. Each emission captures
            // the instruction currently following the anchor as its skip target, so
            // injecting Grok first chains the fallthrough correctly.
            val profileAnchor = footerTarget.method.findProfileRowCall()
            val profileMethod = profileAnchor.target.method
            if (profileMethod.parameterTypes.firstOrNull()?.toString() == "Lcom/x/main/api/landing/x;") {
                val itemParamReg = profileMethod.p0Register
                val callEndReg = profileAnchor.target.call.startRegister + profileAnchor.target.call.registerCount
                profileMethod.insertHook(
                    index = profileAnchor.target.callIndex + 1,
                    excludedRegisters = (profileAnchor.target.call.startRegister until callEndReg).toList() + itemParamReg,
                    relocateBranchTargets = false,
                ) {
                    val lowItemReg = scratchRegister()
                    val profileSingletonReg = scratchRegister()
                    move(lowItemReg, itemParamReg, "Lcom/x/main/api/landing/x;")
                    sget(profileSingletonReg, fieldReference("Lcom/x/main/api/landing/c0;->a:Lcom/x/main/api/landing/c0;"))
                    ifNe(lowItemReg, profileSingletonReg, Target.Original)
                }
            }
            footerTarget.method.injectPikoSettingsDrawerItem(
                target = footerTarget,
                renderer = footerTarget.renderer,
                settingsIconType = settingsIconType,
                settingsIconField = settingsIconField,
                showPikoSettingsInDrawer = showPikoSettingsInDrawer,
            )
            // Null toggle: the title provider returns null while disabled, so no setting
            // read register is needed at this dense call site.
            profileMethod.injectAdditionalDrawerRow(
                target = profileAnchor.target,
                renderer = profileAnchor.renderer,
                icon = DrawerRowIcon.Field(notificationsIcon),
                toggle = null,
                titleDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getNotificationsTitle()Ljava/lang/String;",
                clickDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getNotificationsClickHandler()$FUNCTION0_DESCRIPTOR",
            )
            profileMethod.injectAdditionalDrawerRow(
                target = profileAnchor.target,
                renderer = profileAnchor.renderer,
                icon = DrawerRowIcon.Field(grokIcon),
                toggle = null,
                titleDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getGrokTitle()Ljava/lang/String;",
                clickDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getGrokClickHandler()$FUNCTION0_DESCRIPTOR",
            )
            profileMethod.injectAdditionalDrawerRow(
                target = profileAnchor.target,
                renderer = profileAnchor.renderer,
                icon = DrawerRowIcon.Field(messagesIcon),
                toggle = null,
                titleDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getMessagesTitle()Ljava/lang/String;",
                clickDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getMessagesClickHandler()$FUNCTION0_DESCRIPTOR",
            )

            // THEME PATH: sun/moon toggle button; skip when the release has no theme toggle.
            val themeMatches = NewXDrawerThemeToggleFingerprint.scopedMatchAllOrNull().orEmpty()
            if (themeMatches.size > 1) {
                throw PatchException(
                    "Expected at most one NewX drawer theme toggle, found ${themeMatches.size}: " +
                        themeMatches.joinToString { it.originalMethod.toString() },
                )
            }
            themeMatches.singleOrNull()?.method?.injectFixedDrawerItemGuard(hiddenItems, "THEME_TOGGLE")

            // GROK PATH: dedicated Get/Open Grok button; skip when the release has no Grok row.
            val grokMatches = NewXDrawerGrokButtonFingerprint.scopedMatchAllOrNull().orEmpty()
            if (grokMatches.size > 1) {
                throw PatchException(
                    "Expected at most one NewX drawer Grok button, found ${grokMatches.size}: " +
                        grokMatches.joinToString { it.originalMethod.toString() },
                )
            }
            grokMatches.singleOrNull()?.method?.injectFixedDrawerItemGuard(hiddenItems, "GROK")
        }
    }

// trigger artifact build
