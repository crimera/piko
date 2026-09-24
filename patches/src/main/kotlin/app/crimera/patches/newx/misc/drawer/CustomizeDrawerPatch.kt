package app.crimera.patches.newx.misc.drawer

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.MultiChoiceSettingDefinition
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.SettingsRegistrationState
import app.crimera.patches.newx.settings.ToggleSettingDefinition
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXCustomScreen
import app.crimera.patches.newx.settings.newXSettingsPatch
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.resolveSettingsIconField
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXMultiChoice
import app.crimera.patches.newx.misc.navbar.NewXNavBarTabData
import app.crimera.patches.newx.misc.navbar.NewXTabDataFingerprint
import app.crimera.patches.newx.misc.navbar.resolveIconField
import app.crimera.patches.newx.misc.navbar.resolveNavBarItemContent
import app.crimera.patches.newx.misc.navbar.resolveTitleResourceIdAtRowCall
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.crimera.patches.newx.misc.navbar.resolveComponentTabChangeMethod
import app.crimera.patches.newx.misc.navbar.toSmaliDescriptor
import app.crimera.patches.newx.misc.navbar.validateNewXNavBarTabData
import app.crimera.patches.newx.misc.navbar.resolveIconDrawables
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_CATALOG_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_EDITOR_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_ITEM_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_TAB_OPENER_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.patches.newx.utils.OBJECT_MOVE_OPCODES
import app.crimera.patches.newx.utils.destinationRegisterOrNull
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.newx.utils.resolveIntegerLiteralOnCurrentPath
import app.crimera.patches.newx.utils.resolveIntegerLiterals
import app.crimera.patches.newx.utils.valueReachesRegister
import app.crimera.patches.newx.utils.writesObjectRegister
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.w3c.dom.Element

private const val DRAWER_RESOURCE_ITEM_ID_PREFIX = "RESOURCE_NAME_"
private const val DRAWER_RESOURCE_TYPE = "string"
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
    resourcePatch(
        name = "NewX: Resolve drawer resource names",
        description = "Resolves stable names for dynamically discovered drawer strings.",
        default = false,
    ) {
        dependsOn(resourceMappingPatch)

        execute {
            document("res/values/public.xml").use { publicXml ->
                val entries = linkedMapOf<Int, String>()
                val nodes = publicXml.getElementsByTagName("public")
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    if (element.getAttribute("type") != DRAWER_RESOURCE_TYPE) continue

                    val idText = element.getAttribute("id")
                    val resourceId =
                        idText
                            .removePrefix("0x")
                            .toLongOrNull(16)
                            ?.takeIf { it in 1..0xffffffffL }
                            ?.toInt()
                            ?: throw PatchException(
                                "Invalid NewX drawer string resource id: $idText",
                            )
                    val entryName = element.getAttribute("name")
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

private val NEWX_DRAWER_MENU_ITEM_PARAMETERS =
    listOf(
        "Ljava/lang/String;",
        "L",
        FUNCTION0_DESCRIPTOR,
        "L",
        "Z",
        "L",
        COMPOSER_DESCRIPTOR,
        "I",
        "I",
    )

// Newer releases add an auxiliary content lambda before the Composer parameter.
private val NEWX_DRAWER_MENU_ITEM_WITH_AUXILIARY_CONTENT_PARAMETERS =
    listOf(
        "Ljava/lang/String;",
        "L",
        FUNCTION0_DESCRIPTOR,
        "L",
        "Z",
        "L",
        FUNCTION3_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        "I",
        "I",
    )

private val NEWX_DRAWER_FOOTER_ITEM_PARAMETERS =
    listOf(
        "I",
        COMPOSER_DESCRIPTOR,
        "L",
        "L",
        "Ljava/lang/String;",
        FUNCTION0_DESCRIPTOR,
    )

// 12.25 moved footer rows to the shared title/icon/click renderer.
private val NEWX_DRAWER_SHARED_FOOTER_ITEM_PARAMETERS =
    listOf(
        "Ljava/lang/String;",
        "L",
        FUNCTION0_DESCRIPTOR,
        "Landroidx/compose/ui/Modifier;",
        FUNCTION2_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        "I",
        "I",
    )

private object NewXDrawerContentClassFingerprint : Fingerprint(
    definingClass = DRAWER_SCOPE,
    returnType = "V",
    custom = { method, _ ->
        val parameters = method.parameterTypes.map(CharSequence::toString)
        parameters.size >= 40 &&
            parameters.any { it.startsWith("Landroidx/compose/material3/") } &&
            parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
            parameters.count { it == "Ljava/util/List;" } == 1 &&
            parameters.count { it == "Ljava/util/Map;" } == 1 &&
            parameters.count { it == "Z" } >= 10 &&
            parameters.lastOrNull() == "I" &&
            parameters.count { it == FUNCTION0_DESCRIPTOR } >= 10 &&
            parameters.count { it == FUNCTION1_DESCRIPTOR } >= 2 &&
            "Landroidx/compose/ui/Modifier;" in parameters
    },
)

private object NewXDrawerMenuItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    parameters = NEWX_DRAWER_MENU_ITEM_PARAMETERS,
)

private object NewXDrawerMenuItemWithAuxiliaryContentFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    parameters = NEWX_DRAWER_MENU_ITEM_WITH_AUXILIARY_CONTENT_PARAMETERS,
)

// FOOTER ROWS: settings/help/feedback/media/imprint/debug pass their localized title.
private object NewXDrawerFooterItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    parameters = NEWX_DRAWER_FOOTER_ITEM_PARAMETERS,
)

private object NewXDrawerSharedFooterItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    parameters = NEWX_DRAWER_SHARED_FOOTER_ITEM_PARAMETERS,
)

/**
 * THEME PATH: sun/moon IconButton pinned to the drawer bottom (content-desc
 * drawer_display_settings, opens theme settings). Takes no title, so filter by stable ID.
 * Shares its parameter shape with the under-review badge renderer; the material3
 * IconButton call below is what distinguishes it. The material3 owner/param slots use
 * prefix/wildcard types because the IconButton class shifts between releases.
 */
private object NewXDrawerThemeToggleFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    parameters =
        listOf(
            "I",
            "Landroidx/compose/runtime/Composer;",
            "Landroidx/compose/ui/Modifier;",
            "Lkotlin/jvm/functions/Function0;",
        ),
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
    val titleRegister =
        getFreeRegisterProvider(0, 1, titleParameterRegister)
            .getFreeRegister4Bit()
    injectDrawerGuard(
        hiddenItems = hiddenItems,
        titleRegister = titleRegister,
        titleInstruction = "move-object/from16 v$titleRegister, v$titleParameterRegister",
        predicateMethod = "shouldHide",
        labelSuffix = "${parameterTypes.size}_$stringParamIndex",
        excludedRegisters = listOf(titleParameterRegister, titleRegister),
    )
}

// ID-based filtering for title-less buttons (Grok, theme toggle).
private fun MutableMethod.injectFixedDrawerItemGuard(
    hiddenItems: MultiChoiceSettingDefinition,
    itemId: String,
) {
    val parameterRegisterCount =
        parameterTypes.sumOf { type -> if (type == "J" || type == "D") 2 else 1 }
    val titleRegister =
        getFreeRegisterProvider(0, 1, *(0 until parameterRegisterCount).toList().toIntArray())
            .getFreeRegister4Bit()
    injectDrawerGuard(
        hiddenItems = hiddenItems,
        titleRegister = titleRegister,
        titleInstruction = "const-string v$titleRegister, \"$itemId\"",
        predicateMethod = "shouldHideId",
        labelSuffix = "fixed_$itemId",
        excludedRegisters = listOf(titleRegister),
    )
}

private fun MutableMethod.injectDrawerGuard(
    hiddenItems: MultiChoiceSettingDefinition,
    titleRegister: Int,
    titleInstruction: String,
    predicateMethod: String,
    labelSuffix: String,
    excludedRegisters: List<Int>,
) {
    val originalInstruction =
        instructions.firstOrNull()
            ?: throw PatchException("NewX drawer item renderer has no instructions")
    val read =
        hiddenItems.injectRead(
            method = this,
            index = 0,
            excludedRegisters = excludedRegisters,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val continueLabel = "piko_newx_drawer_item_continue_$labelSuffix"

    addInstructionsWithLabels(
        read.nextIndex,
        """
            $titleInstruction
            invoke-static {v$titleRegister, v${read.register}}, $DRAWER_ITEM_FILTER_DESCRIPTOR->$predicateMethod(Ljava/lang/String;Ljava/util/Set;)Z
            move-result v$titleRegister
            if-eqz v$titleRegister, :$continueLabel
            return-void
        """.trimIndent(),
        ExternalLabel(continueLabel, originalInstruction),
    )
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
    val titleParameterIndex = titleParameterIndices.single()
    val titleRegister = call.startRegister + parameters.take(titleParameterIndex).sumOf { type ->
        if (type == "J" || type == "D") 2 else 1
    }

    val stringResourceNamespaces =
        indices.mapNotNull { index ->
            val lookupInstruction = this[index]
            if (lookupInstruction.opcode != Opcode.INVOKE_STATIC &&
                lookupInstruction.opcode != Opcode.INVOKE_STATIC_RANGE
            ) {
                return@mapNotNull null
            }
            val lookupReference = lookupInstruction.getReference<MethodReference>() ?: return@mapNotNull null
            if (!lookupReference.isStringResourceLookup()) return@mapNotNull null
            val registers = lookupInstruction.registersUsed
            if (registers.size != 2) return@mapNotNull null
            resolveIntegerLiteralOnCurrentPath(index, registers[1])
        }.filter { it != 0 }
        .map { it ushr 16 }
        .toSet()

    val resourceIds = linkedSetOf<Int>()
    for (resultIndex in 1 until callIndex) {
        val resultInstruction = this[resultIndex]
        if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) continue
        val resultRegister = (resultInstruction as? OneRegisterInstruction)?.registerA ?: continue
        if (!valueReachesRegister(resultIndex, resultRegister, callIndex, titleRegister)) continue

        val lookupIndex = resultIndex - 1
        val lookupInstruction = this.getOrNull(lookupIndex) ?: continue
        if (lookupInstruction.opcode != Opcode.INVOKE_STATIC &&
            lookupInstruction.opcode != Opcode.INVOKE_STATIC_RANGE
        ) continue
        val lookupReference = lookupInstruction.getReference<MethodReference>() ?: continue
        if (!lookupReference.isStringResourceLookup()) continue

        val registers = lookupInstruction.registersUsed
        if (registers.size != 2) continue
        resolveIntegerLiterals(lookupIndex, registers[1])
            .filterTo(resourceIds) { resourceId ->
                resourceId != 0 && resourceId ushr 16 in stringResourceNamespaces
            }
    }

    return resourceIds
}

context(context: BytecodePatchContext)
private fun resolveDrawerRendererCalls(renderer: MethodReference): List<DrawerRendererCall> {
    val expectedRegisterCount = renderer.parameterTypes.sumOf { type ->
        if (type.toString() == "J" || type.toString() == "D") 2 else 1
    }
    val calls = buildList {
        context.classDefForEach { classDef ->
            if (!classDef.type.startsWith(DRAWER_SCOPE)) return@classDefForEach
            val mutableClass = context.mutableClassDefBy(classDef.type)
            mutableClass.methods.forEach { method ->
                method.instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE) return@forEachIndexed
                    val call = instruction as? Instruction3rc ?: return@forEachIndexed
                    val reference = instruction.getReference<MethodReference>()
                        ?: return@forEachIndexed
                    if (reference.toSmaliDescriptor() != renderer.toSmaliDescriptor()) {
                        return@forEachIndexed
                    }
                    if (call.registerCount != expectedRegisterCount) {
                        throw PatchException(
                            "NewX drawer renderer call register count changed in $method @ $index: " +
                                "expected $expectedRegisterCount, found ${call.registerCount}",
                        )
                    }
                    add(DrawerRendererCall(method, index, call, reference))
                }
            }
        }
    }
    if (calls.isEmpty()) {
        throw PatchException("Expected at least one NewX drawer renderer call for $renderer")
    }
    return calls
}

context(context: BytecodePatchContext)
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
                ?.takeIf { field -> field.type.toString().startsWith("Lcom/x/icons/") }
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

context(context: BytecodePatchContext)
private fun injectDrawerCatalog(
    nativeEntries: List<DrawerCatalogEntry>,
    shortcutIcons: Map<String, FieldReference>,
    settingsIconField: FieldReference,
) {
    val iconFields =
        (nativeEntries.mapNotNull { it.iconField } +
            shortcutIcons.values + settingsIconField)
            .distinctBy(FieldReference::toString)
    val drawables = resolveIconDrawables(iconFields)
    val instructions = buildString {
        nativeEntries.forEach { entry ->
            val drawable = entry.iconField?.let { drawables[it.toString()] } ?: 0
            appendLine("const-string v0, \"${entry.optionId}\"")
            appendLine("const v1, ${drawable.toDrawerSmaliLiteral()}")
            appendLine(
                "invoke-static {v0, v1}, " +
                    "$DRAWER_CATALOG_DESCRIPTOR->registerItem(Ljava/lang/String;I)V",
            )
        }
        shortcutIcons.forEach { (optionId, iconField) ->
            val drawable = drawables.getValue(iconField.toString()).toDrawerSmaliLiteral()
            appendLine("const-string v0, \"$optionId\"")
            appendLine("const v1, $drawable")
            appendLine(
                "invoke-static {v0, v1}, " +
                    "$DRAWER_CATALOG_DESCRIPTOR->registerItem(Ljava/lang/String;I)V",
            )
        }
        appendLine("const-string v0, \"$DRAWER_SHORTCUT_PIKO\"")
        appendLine(
            "const v1, " +
                drawables.getValue(settingsIconField.toString()).toDrawerSmaliLiteral(),
        )
        appendLine(
            "invoke-static {v0, v1}, " +
                "$DRAWER_CATALOG_DESCRIPTOR->registerItem(Ljava/lang/String;I)V",
        )
        listOf("GROK", "THEME_TOGGLE").forEach { optionId ->
            appendLine("const-string v0, \"$optionId\"")
            appendLine("const v1, 0x0")
            appendLine(
                "invoke-static {v0, v1}, " +
                    "$DRAWER_CATALOG_DESCRIPTOR->registerItem(Ljava/lang/String;I)V",
            )
        }
    }
    SettingsRegistrationState.inject(context, instructions)
}

private fun Int.toDrawerSmaliLiteral(): String =
    if (this < 0) "-0x${(-this).toString(16)}" else "0x${toString(16)}"

context(context: BytecodePatchContext)
private fun injectDynamicDrawerOptions(
    hiddenItems: MultiChoiceSettingDefinition,
    resourceIds: List<Int>,
) {
    if (resourceIds.isEmpty()) {
        throw PatchException("Expected at least one dynamic NewX drawer title resource")
    }
    val instructions = resourceIds.distinct().joinToString("\n") { resourceId ->
        val optionId = resourceDrawerOptionId(resourceId)
        """
            const-string v0, "${hiddenItems.id}"
            const-string v1, "$optionId"
            const v2, ${resourceId.toDrawerSmaliLiteral()}
            const/4 v3, 0x0
            invoke-static/range {v0 .. v3}, $SETTINGS_REGISTRY_DESCRIPTOR->registerChoiceOptionResource(Ljava/lang/String;Ljava/lang/String;IZ)V
        """.trimIndent()
    }
    SettingsRegistrationState.inject(context, instructions)
}

private fun MethodReference.isDrawerFooterDivider(renderer: MethodReference): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return definingClass.toString() == renderer.definingClass.toString() &&
        returnType.toString() == "V" &&
        parameters.size == 4 &&
        parameters[0].startsWith("Landroidx/compose/material3/") &&
        parameters[1] == FUNCTION1_DESCRIPTOR &&
        parameters[2] == COMPOSER_DESCRIPTOR &&
        parameters[3] == "I"
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
        if (footerRenderer.definingClass.toString() != renderer.definingClass.toString() ||
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

context(context: BytecodePatchContext)
private fun resolveDrawerFooterTarget(
    renderer: MethodReference,
    settingsIconField: FieldReference,
): DrawerFooterTarget {
    val candidates = buildList {
        context.classDefForEach { classDef ->
            if (!classDef.type.startsWith(DRAWER_SCOPE)) return@classDefForEach
            if (!classDef.interfaces.any { it.toString() == FUNCTION3_DESCRIPTOR }) {
                return@classDefForEach
            }

            val mutableClass = context.mutableClassDefBy(classDef.type)
            mutableClass.methods
                .filter { method ->
                    method.name == "invoke" &&
                        method.returnType.toString() == OBJECT_DESCRIPTOR &&
                        method.parameterTypes.map(CharSequence::toString) ==
                            List(3) { OBJECT_DESCRIPTOR }
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
    if (candidates.size != 1) {
        throw PatchException(
            "Expected one NewX drawer content lambda for ${renderer}, found " +
                "${candidates.size}: ${candidates.joinToString { "${it.method} @${it.callIndex}" }}",
        )
    }
    return candidates.single()
}

private fun MethodReference.toSmaliDescriptor(): String =
    "${definingClass}->${name}(${parameterTypes.joinToString("")})${returnType}"

private fun MutableMethod.injectPikoSettingsDrawerItem(
    target: DrawerFooterTarget,
    renderer: MethodReference,
    settingsIconField: FieldReference,
    showPikoSettingsInDrawer: ToggleSettingDefinition,
) {
    injectAdditionalDrawerRow(
        target = target,
        renderer = renderer,
        iconField = settingsIconField,
        toggle = showPikoSettingsInDrawer,
        titleDescriptor = "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitle()Ljava/lang/String;",
        clickDescriptor = "$COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()$FUNCTION0_DESCRIPTOR",
        labelSuffix = "settings_drawer_continue",
    )
}

/**
 * Emits one extra drawer row after an executed row call, reusing its registers for the
 * shared parameters. Each injection captures the instruction currently following the call as
 * its skip target, so sequential injections chain correctly.
 */
private fun MutableMethod.injectAdditionalDrawerRow(
    target: DrawerFooterTarget,
    renderer: MethodReference,
    iconField: FieldReference,
    toggle: ToggleSettingDefinition?,
    titleDescriptor: String,
    clickDescriptor: String,
    labelSuffix: String,
    registerConstraint: SettingReadRegisterConstraint = SettingReadRegisterConstraint.FOUR_BIT,
) {
    injectSnapshotDrawerRow(
        callIndex = target.callIndex,
        startRegister = target.call.startRegister,
        registerCount = target.call.registerCount,
        renderer = renderer,
        iconField = iconField,
        toggle = toggle,
        titleDescriptor = titleDescriptor,
        clickDescriptor = clickDescriptor,
        labelSuffix = labelSuffix,
        registerConstraint = registerConstraint,
    )
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
    iconField: FieldReference,
    toggle: ToggleSettingDefinition?,
    titleDescriptor: String,
    clickDescriptor: String,
    labelSuffix: String,
    registerConstraint: SettingReadRegisterConstraint = SettingReadRegisterConstraint.FOUR_BIT,
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
    if (iconField.type.toString() != parameters[iconIndex]) {
        throw PatchException(
            "NewX drawer icon type changed: renderer=${parameters[iconIndex]}, " +
                "field=${iconField.type}",
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

    val continuationInstruction =
        instructions.getOrNull(callIndex + 1)
            ?: throw PatchException("NewX drawer row injection point has no continuation: $this")
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
    val rowDescriptor = renderer.toSmaliDescriptor()
    val titleRegister = startRegister + titleIndex
    val iconRegister = startRegister + iconIndex
    val clickRegister = startRegister + clickIndex
    val continueLabel = "piko_newx_${labelSuffix}"
    val toggleGuard =
        if (settingRead == null) {
            ""
        } else {
            "if-eqz v${settingRead.register}, :$continueLabel\n"
        }
    addInstructionsWithLabels(
        insertionIndex,
        """
            ${toggleGuard}invoke-static {}, $titleDescriptor
            move-result-object v$titleRegister
            if-eqz v$titleRegister, :$continueLabel
            sget-object v$iconRegister, $iconField
            invoke-static {}, $clickDescriptor
            move-result-object v$clickRegister
            invoke-static/range {v$startRegister .. v$endRegister}, $rowDescriptor
        """.trimIndent(),
        ExternalLabel(continueLabel, continuationInstruction),
    )
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
    val profileTitleId = getResourceId(ResourceType.STRING, "drawer_profile_title")
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
    val tabChangeDescriptor: String?,
    val tabChangeFunctionField: FieldReference?,
    val closerField: FieldReference,
    val closerMethod: MethodReference,
    val closerArgField: FieldReference,
)

private val CASE_END_OPCODES = setOf(Opcode.RETURN_OBJECT, Opcode.RETURN_VOID, Opcode.THROW)

/**
 * Resolves the 12.29+ tab change shape: a captured Function1 field invoked with the tab in the
 * Communities click. Returns the invoke index and the field it loads, or null for older releases
 * that push the stack frame inline.
 */
private fun List<Instruction>.resolveTabChangeFunction(
    communitiesIndex: Int,
    caseEndIndex: Int,
): Pair<Int, FieldReference>? {
    for (index in communitiesIndex + 1 until caseEndIndex) {
        val instruction = this[index]
        if (instruction.opcode != Opcode.INVOKE_INTERFACE &&
            instruction.opcode != Opcode.INVOKE_INTERFACE_RANGE
        ) continue
        val reference = instruction.getReference<MethodReference>() ?: continue
        if (reference.definingClass.toString() != FUNCTION1_DESCRIPTOR ||
            reference.name != "invoke" ||
            reference.parameterTypes.map(CharSequence::toString) != listOf(OBJECT_DESCRIPTOR) ||
            reference.returnType.toString() != OBJECT_DESCRIPTOR
        ) continue
        val receiver = instruction.registersUsed.firstOrNull() ?: continue
        val field = resolveRegisterField(receiver, index) ?: continue
        return index to field
    }
    return null
}

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
 * Resolves the tab-open contract for the drawer shortcuts: the tab change method shared with
 * the navigation bar patch, plus the drawer-close triple extracted from the Communities drawer
 * click (stack push, then close). The Communities case proves both the close field on the tab
 * component and the close argument without hardcoding obfuscated owners.
 */
context(context: BytecodePatchContext)
private fun resolveDrawerTabNavigation(
    tabData: NewXNavBarTabData,
): DrawerTabNavigation {
    val communitiesField = "${tabData.navigationType}->COMMUNITIES:${tabData.navigationType}"
    // Read-only discovery pass; see the navigation bar patch for why mutable proxies are avoided.
    val dispatcherClasses = mutableListOf<String>()
    context.classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            if (method.name != "invoke" ||
                method.returnType.toString() != OBJECT_DESCRIPTOR ||
                method.parameterTypes.isNotEmpty() ||
                method.implementation == null
            ) return@forEach
            val methodInstructions = method.implementation?.instructions?.toList() ?: return@forEach
            val readsCommunities =
                methodInstructions.any { instruction ->
                    instruction.opcode == Opcode.SGET_OBJECT &&
                        instruction.getReference<FieldReference>()?.toString() == communitiesField
                }
            val hasSwitch =
                methodInstructions.any { instruction -> instruction.opcode == Opcode.PACKED_SWITCH }
            if (readsCommunities && hasSwitch) dispatcherClasses += classDef.type.toString()
        }
    }
    val dispatcherClass = requireExactlyOne("NewX drawer click dispatcher", dispatcherClasses)
    val dispatcher =
        requireExactlyOne(
            label = "NewX drawer click dispatcher invoke",
            candidates = context.mutableClassDefBy(dispatcherClass).methods.filter { method ->
                method.name == "invoke" &&
                    method.returnType.toString() == OBJECT_DESCRIPTOR &&
                    method.parameterTypes.isEmpty() &&
                    method.implementation != null
            },
        ) { it.toString() }
    val dispatcherInstructions = dispatcher.instructions.toList()
    val communitiesIndex =
        requireExactlyOne(
            label = "NewX Communities drawer click",
            candidates = dispatcherInstructions.indices.filter { index ->
                val instruction = dispatcherInstructions[index]
                instruction.opcode == Opcode.SGET_OBJECT &&
                    instruction.getReference<FieldReference>()?.toString() == communitiesField
            },
        ) { it.toString() }
    // The Communities case ends at the first return; bound the scan to that case so a later
    // click handler cannot supply a false tab change or closer.
    val caseEndIndex =
        dispatcherInstructions.indices.firstOrNull { index ->
            index > communitiesIndex &&
                dispatcherInstructions[index].opcode in CASE_END_OPCODES
        } ?: dispatcherInstructions.size
    val stackPushIndex =
        dispatcherInstructions.indices.firstOrNull { index ->
            if (index <= communitiesIndex || index >= caseEndIndex) return@firstOrNull false
            val instruction = dispatcherInstructions[index]
            if (instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE
            ) return@firstOrNull false
            val reference = instruction.getReference<MethodReference>() ?: return@firstOrNull false
            reference.returnType.toString() == "V" &&
                reference.parameterTypes.map(CharSequence::toString) ==
                listOf(FUNCTION2_DESCRIPTOR, FUNCTION1_DESCRIPTOR)
        }
    // 12.29 routes the tab change through a captured Function1 field on the drawer component
    // instead of pushing the stack frame inline.
    val tabChangeFunction =
        if (stackPushIndex == null) {
            dispatcherInstructions.resolveTabChangeFunction(communitiesIndex, caseEndIndex)
                ?: throw PatchException(
                    "NewX Communities drawer click has neither a tab stack push nor a tab " +
                        "change function: $dispatcher",
                )
        } else {
            null
        }
    val tabChangeIndex =
        stackPushIndex ?: tabChangeFunction?.first
            ?: throw PatchException("NewX Communities drawer click has no tab change: $dispatcher")
    val closerIndex =
        dispatcherInstructions.indices.firstOrNull { index ->
            if (index <= tabChangeIndex || index >= caseEndIndex) return@firstOrNull false
            val instruction = dispatcherInstructions[index]
            if (instruction.opcode != Opcode.INVOKE_INTERFACE &&
                instruction.opcode != Opcode.INVOKE_INTERFACE_RANGE
            ) return@firstOrNull false
            val reference = instruction.getReference<MethodReference>() ?: return@firstOrNull false
            reference.returnType.toString() == "V" &&
                reference.parameterTypes.map(CharSequence::toString) == listOf(OBJECT_DESCRIPTOR)
        } ?: throw PatchException("NewX Communities drawer click has no drawer close call: $dispatcher")
    val closerInstruction = dispatcherInstructions[closerIndex]
    val closerRegisters = closerInstruction.registersUsed
    if (closerRegisters.size != 2) {
        throw PatchException(
            "NewX drawer close call has an unexpected register shape in $dispatcher @ $closerIndex",
        )
    }
    val closerObjectRegister = closerRegisters[0]
    val closerArgRegister = closerRegisters[1]
    val closerMethod =
        closerInstruction.getReference<MethodReference>()
            ?: throw PatchException("NewX drawer close call has no method reference: $dispatcher")
    val closerField =
        requireExactlyOne(
            label = "NewX drawer close field",
            candidates = dispatcherInstructions.indices.filter { index ->
                if (index <= tabChangeIndex || index >= closerIndex) return@filter false
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
    // The dispatcher receiver owns both the close field and (12.27-12.28) the tab change method.
    // 12.29 moved only the tab change out; the capture target stays the close-field owner.
    val componentClass = closerField.definingClass.toString()
    if (closerField.type.toString() != closerMethod.definingClass.toString()) {
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
    val switchIndex =
        dispatcherInstructions.indices.firstOrNull { index ->
            dispatcherInstructions[index].opcode == Opcode.PACKED_SWITCH
        } ?: throw PatchException("NewX drawer click dispatcher has no dispatch switch: $dispatcher")
    if (switchIndex >= communitiesIndex) {
        throw PatchException("NewX drawer dispatch switch is outside the prologue: $dispatcher")
    }
    // Older releases load the close argument inline in the case block; newer ones hoist it
    // into the prologue before the dispatch switch. A dataflow check cannot prove the prologue
    // load because other case blocks reuse the same registers, so each shape is resolved by
    // position and the combined cardinality is asserted.
    val prologueArgFields = dispatcherInstructions.indices.mapNotNull { index ->
        if (index >= switchIndex) return@mapNotNull null
        val instruction = dispatcherInstructions[index]
        if (instruction.opcode != Opcode.SGET_OBJECT) return@mapNotNull null
        instruction.getReference<FieldReference>()
    }
    val inlineArgField = dispatcherInstructions.resolveIconField(closerIndex, closerArgRegister)
    val closerArgField =
        requireExactlyOne(
            label = "NewX drawer close argument",
            candidates =
                (prologueArgFields + listOfNotNull(inlineArgField))
                    .filter { it.type.toString().startsWith("Lcom/x/main/api/") }
                    .distinctBy(FieldReference::toString),
            describe = { it.toString() },
        )
    if (!closerArgField.type.toString().startsWith("Lcom/x/main/api/")) {
        throw PatchException(
            "NewX drawer close argument is not a stable API type: $closerArgField",
        )
    }
    val tabChangeDescriptor =
        if (tabChangeFunction == null) {
            val method =
                resolveComponentTabChangeMethod(componentClass, tabData.navigationType)
                    ?: throw PatchException(
                        "NewX tab component $componentClass has no tab change method",
                    )
            "${method.definingClass}->${method.name}(" +
                "${method.parameterTypes.joinToString("")})${method.returnType}"
        } else {
            null
        }
    return DrawerTabNavigation(
        componentClass = componentClass,
        enumType = tabData.navigationType,
        tabChangeDescriptor = tabChangeDescriptor,
        tabChangeFunctionField = tabChangeFunction?.second,
        closerField = closerField,
        closerMethod = closerMethod,
        closerArgField = closerArgField,
    )
}

/** Captures the tab component after its constructor finishes so shortcuts can open tabs. */
context(context: BytecodePatchContext)
private fun hookDrawerTabComponent(componentClass: String) {
    val classDef = context.mutableClassDefBy(componentClass)
    val superType = classDef.superclass.toString()
    val constructors =
        classDef.methods.filter { method ->
            method.name == "<init>" && method.implementation != null
        }
    if (constructors.isEmpty()) {
        throw PatchException("NewX tab component has no constructor: $componentClass")
    }
    constructors.forEach { constructor ->
        val superCallIndex =
            constructor.instructions.indexOfFirst { instruction ->
                (instruction.opcode == Opcode.INVOKE_DIRECT ||
                    instruction.opcode == Opcode.INVOKE_DIRECT_RANGE) &&
                    instruction.getReference<MethodReference>()?.let { reference ->
                        reference.name == "<init>" &&
                            reference.definingClass.toString() == superType
                    } == true
            }
        if (superCallIndex < 0) {
            throw PatchException("NewX tab component constructor has no super call: $constructor")
        }
        constructor.addInstructions(
            superCallIndex + 1,
            "invoke-static/range {p0 .. p0}, " +
                "$DRAWER_TAB_OPENER_DESCRIPTOR->setComponent(Ljava/lang/Object;)V",
        )
    }
}

/** Replaces the shortcut bodies with direct tab-open plus drawer-close invokes. */
context(context: BytecodePatchContext)
private fun replaceDrawerTabOpenerBodies(navigation: DrawerTabNavigation) {
    val openerClass = context.mutableClassDefBy(DRAWER_TAB_OPENER_DESCRIPTOR)
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
        requireExactlyOne(
            label = "DrawerTabOpener.$name",
            candidates = openerClass.methods.filter { method ->
                method.name == name && method.parameterTypes.isEmpty() && method.returnType == "V"
            },
        ) { it.toString() }
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
    val tabChange =
        navigation.tabChangeDescriptor?.let { descriptor ->
            "invoke-virtual {v1, v0}, $descriptor"
        } ?: run {
            val field = navigation.tabChangeFunctionField
                ?: throw PatchException(
                    "Drawer tab change is missing on ${navigation.componentClass}",
                )
            "iget-object v2, v1, $field\n" +
                "invoke-interface {v2, v0}, " +
                "$FUNCTION1_DESCRIPTOR->invoke(Ljava/lang/Object;)Ljava/lang/Object;"
        }
    expanded.addInstructions(
        0,
        """
            sget-object v0, ${navigation.enumType}->$enumEntry:${navigation.enumType}
            sget-object v1, $DRAWER_TAB_OPENER_DESCRIPTOR->component:Ljava/lang/Object;
            if-eqz v1, :piko_drawer_tab_done
            check-cast v1, ${navigation.componentClass}
            $tabChange
            sget-object v0, ${navigation.closerArgField}
            iget-object v2, v1, ${navigation.closerField}
            invoke-interface {v2, v0}, ${navigation.closerMethod.toSmaliDescriptor()}
            :piko_drawer_tab_done
            return-void
        """.trimIndent(),
    )
}

@Suppress("unused")
val customizeNewXDrawerPatch =
    bytecodePatch(
        name = "NewX: Customize drawer items",
        description = "Lets you hide selected items from the NewX navigation drawer, and optionally add Messages and Grok shortcuts.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
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
                (NewXDrawerMenuItemFingerprint.scopedMatchAllOrNull().orEmpty() +
                    NewXDrawerMenuItemWithAuxiliaryContentFingerprint.scopedMatchAllOrNull().orEmpty())
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
                (NewXDrawerFooterItemFingerprint.scopedMatchAllOrNull().orEmpty() +
                    NewXDrawerSharedFooterItemFingerprint.scopedMatchAllOrNull().orEmpty())
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
            val tabIconFields = resolveNavBarItemContent(tabData).tabIconFields
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
                settingsIconField = settingsIconField,
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
            footerTarget.method.injectPikoSettingsDrawerItem(
                target = footerTarget,
                renderer = footerTarget.renderer,
                settingsIconField = settingsIconField,
                showPikoSettingsInDrawer = showPikoSettingsInDrawer,
            )
            // Null toggle: the title provider returns null while disabled, so no setting
            // read register is needed at this dense call site.
            footerTarget.method.injectAdditionalDrawerRow(
                target = profileAnchor.target,
                renderer = profileAnchor.renderer,
                iconField = notificationsIcon,
                toggle = null,
                titleDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getNotificationsTitle()Ljava/lang/String;",
                clickDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getNotificationsClickHandler()$FUNCTION0_DESCRIPTOR",
                labelSuffix = "notifications_drawer_continue",
            )
            footerTarget.method.injectAdditionalDrawerRow(
                target = profileAnchor.target,
                renderer = profileAnchor.renderer,
                iconField = grokIcon,
                toggle = null,
                titleDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getGrokTitle()Ljava/lang/String;",
                clickDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getGrokClickHandler()$FUNCTION0_DESCRIPTOR",
                labelSuffix = "grok_drawer_continue",
            )
            footerTarget.method.injectAdditionalDrawerRow(
                target = profileAnchor.target,
                renderer = profileAnchor.renderer,
                iconField = messagesIcon,
                toggle = null,
                titleDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getMessagesTitle()Ljava/lang/String;",
                clickDescriptor = "$DRAWER_TAB_OPENER_DESCRIPTOR->getMessagesClickHandler()$FUNCTION0_DESCRIPTOR",
                labelSuffix = "messages_drawer_continue",
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
