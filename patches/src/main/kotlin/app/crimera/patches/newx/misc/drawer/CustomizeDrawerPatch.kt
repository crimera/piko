package app.crimera.patches.newx.misc.drawer

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.MultiChoiceSettingDefinition
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.ToggleSettingDefinition
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXSettingsPatch
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.resolveSettingsIconField
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXMultiChoice
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.COMPOSE_SETTINGS_HOOK_DESCRIPTOR
import app.crimera.patches.newx.utils.Constants.DRAWER_ITEM_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val DRAWER_SCOPE = "Lcom/x/main/drawer/"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION3_DESCRIPTOR = "Lkotlin/jvm/functions/Function3;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

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

private val NEWX_DRAWER_FOOTER_ITEM_PARAMETERS =
    listOf(
        "I",
        COMPOSER_DESCRIPTOR,
        "L",
        "L",
        "Ljava/lang/String;",
        FUNCTION0_DESCRIPTOR,
    )

private object NewXDrawerContentClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/main/drawer/",
    returnType = "V",
    filters =
        listOf(
            string("drawerState"),
            string("onBookmarkClicked"),
            string("onCommunitiesClicked"),
            string("onThemeSettingsClicked"),
        ),
)

private object NewXDrawerMenuItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    parameters = NEWX_DRAWER_MENU_ITEM_PARAMETERS,
)

// FOOTER ROWS: settings/help/feedback/media/imprint/debug pass their localized title.
// (Also matches the legacy alpha footer shape, hence the loose object-typed slots.)
private object NewXDrawerFooterItemFingerprint : Fingerprint(
    classFingerprint = NewXDrawerContentClassFingerprint,
    returnType = "V",
    parameters = NEWX_DRAWER_FOOTER_ITEM_PARAMETERS,
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

private data class ResolvedDrawerFooterCalls(
    val dividerIndex: Int,
    val renderer: MethodReference,
    val calls: List<IndexedValue<Instruction3rc>>,
)

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

private fun MethodReference.isDrawerRowRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType.toString() == "V" &&
        parameters.size in 8..9 &&
        parameters.count { it == "Ljava/lang/String;" } == 1 &&
        parameters.count { it.startsWith("Lcom/x/icons/") } == 1 &&
        parameters.count { it == FUNCTION0_DESCRIPTOR } == 1 &&
        parameters.count { it == "Landroidx/compose/ui/Modifier;" } == 1 &&
        parameters.count { it == "Lkotlin/jvm/functions/Function2;" } == 1 &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.count { it == "I" } == 2
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
    if (dividerIndices.size != 1) return null

    val dividerIndex = dividerIndices.single()
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

private val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private fun Instruction.writesObjectRegister(register: Int): Boolean {
    if (opcode in OBJECT_MOVE_OPCODES) {
        return (this as? TwoRegisterInstruction)?.registerA == register
    }
    if (opcode == Opcode.SGET_OBJECT) {
        return (this as? OneRegisterInstruction)?.registerA == register
    }
    return false
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
    if (settingsIconField.type.toString() != parameters[iconIndex]) {
        throw PatchException(
            "NewX drawer settings icon type changed: renderer=${parameters[iconIndex]}, " +
                "field=${settingsIconField.type}",
        )
    }

    val startRegister = target.call.startRegister
    val endRegister = startRegister + target.call.registerCount - 1
    if (startRegister < 0 || endRegister > 255) {
        throw PatchException(
            "NewX drawer footer call registers are outside v0..v255: " +
                "v$startRegister..v$endRegister",
        )
    }

    val continuationInstruction =
        instructions.getOrNull(target.callIndex + 1)
            ?: throw PatchException("NewX drawer footer settings call has no continuation: ${target.method}")
    val settingRead =
        showPikoSettingsInDrawer.injectRead(
            method = this,
            index = target.callIndex + 1,
            excludedRegisters = (startRegister..endRegister).toList(),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val rowDescriptor = renderer.toSmaliDescriptor()
    val titleRegister = startRegister + titleIndex
    val iconRegister = startRegister + iconIndex
    val clickRegister = startRegister + clickIndex
    val continueLabel = "piko_newx_settings_drawer_continue"
    addInstructionsWithLabels(
        settingRead.nextIndex,
        """
            if-eqz v${settingRead.register}, :$continueLabel
            invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsTitle()Ljava/lang/String;
            move-result-object v$titleRegister
            sget-object v$iconRegister, $settingsIconField
            invoke-static {}, $COMPOSE_SETTINGS_HOOK_DESCRIPTOR->getSettingsClickHandler()$FUNCTION0_DESCRIPTOR
            move-result-object v$clickRegister
            invoke-static/range {v$startRegister .. v$endRegister}, $rowDescriptor
        """.trimIndent(),
        ExternalLabel(continueLabel, continuationInstruction),
    )
}

@Suppress("unused")
val customizeNewXDrawerPatch =
    bytecodePatch(
        name = "NewX: Customize drawer items",
        description = "Lets you hide selected items from the NewX navigation drawer.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXSettingsPatch)

        val showPikoSettingsInDrawer =
            newXToggle(
                id = "newx.navigation.show_piko_settings_in_drawer",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_show_piko_settings_in_drawer"),
                order = 100,
                defaultValue = true,
            )

        val hiddenItems =
            newXMultiChoice(
                id = "newx.content.hidden_drawer_items",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_drawer"),
                order = 200,
                defaultValue = emptySet(),
                options =
                    listOf(
                        choice("PROFILE", "piko_newx_drawer_profile"),
                        choice("PREMIUM", "piko_newx_drawer_premium"),
                        choice("MONEY", "piko_newx_drawer_money"),
                        choice("COMMUNITIES", "piko_newx_drawer_communities"),
                        choice("BOOKMARKS", "piko_newx_drawer_bookmarks"),
                        choice("COMMUNITY_NOTES", "piko_newx_drawer_community_notes"),
                        choice("OFFLINE_VIDEOS", "piko_newx_drawer_offline_videos"),
                        choice("LISTS", "piko_newx_drawer_lists"),
                        choice("BOOST", "piko_newx_drawer_boost"),
                        choice("SPACES", "piko_newx_drawer_spaces"),
                        choice("FOLLOW_REQUESTS", "piko_newx_drawer_follow_requests"),
                        choice("MONETIZATION", "piko_newx_drawer_monetization"),
                        choice("CREATOR_STUDIO", "piko_newx_drawer_creator_studio"),
                        choice("ANALYTICS", "piko_newx_drawer_analytics"),
                        choice("SWITCH_TO_X", "piko_newx_drawer_switch_to_x"),
                        choice("GROK", "piko_newx_drawer_grok"),
                        choice("SETTINGS", "piko_newx_drawer_settings"),
                        choice("HELP_CENTER", "piko_newx_drawer_help_center"),
                        choice("FEEDBACK", "piko_newx_drawer_feedback"),
                        choice("MEDIA_TRANSPARENCY", "piko_newx_drawer_media_transparency"),
                        choice("IMPRINT", "piko_newx_drawer_imprint"),
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

            val menuMatches = NewXDrawerMenuItemFingerprint.scopedMatchAll()
            if (menuMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX drawer menu item renderer, found ${menuMatches.size}: " +
                        menuMatches.joinToString { it.originalMethod.toString() },
                )
            }
            menuMatches.single().method.injectDrawerItemGuard(hiddenItems)

            // FOOTER ROWS: settings/help/feedback/media/imprint/debug render with a title.
            val footerMatches = NewXDrawerFooterItemFingerprint.scopedMatchAllOrNull().orEmpty()
            if (footerMatches.size > 1) {
                throw PatchException(
                    "Expected one NewX drawer footer row renderer, found " +
                        "${footerMatches.size}: ${footerMatches.joinToString { it.originalMethod.toString() }}",
                )
            }
            val footerRendererMatch =
                footerMatches.singleOrNull()?.also { it.method.injectDrawerItemGuard(hiddenItems) }
                    ?: menuMatches.single()
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
            resolveDrawerFooterTarget(footerRenderer, settingsIconField).let { target ->
                target.method.injectPikoSettingsDrawerItem(
                    target = target,
                    renderer = target.renderer,
                    settingsIconField = settingsIconField,
                    showPikoSettingsInDrawer = showPikoSettingsInDrawer,
                )
            }

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
