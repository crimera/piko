package app.crimera.patches.newx.misc.navbar

import app.crimera.patches.newx.misc.drawer.isDrawerRowRenderer
import app.crimera.patches.newx.misc.drawer.isStringResourceLookup
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingsRegistrationState
import app.crimera.patches.newx.settings.newXCustomScreen
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.NAV_BAR_FILTER_DESCRIPTOR
import app.crimera.patches.newx.utils.OBJECT_MOVE_OPCODES
import app.crimera.patches.newx.utils.destinationRegisterOrNull
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.newx.utils.resolveIntegerLiteralOnCurrentPath
import app.crimera.patches.newx.utils.valueReachesRegister
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.cloneMutable
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderSwitchElement
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
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val NAV_BAR_REPLACEMENT_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/NavBarReplacement;"
private const val NAV_BAR_CATALOG_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/NavBarCatalog;"
private const val NAV_BAR_EDITOR_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/NavBarEditorFragment;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION2_DESCRIPTOR = "Lkotlin/jvm/functions/Function2;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val ICONS_DESCRIPTOR_PREFIX = "Lcom/x/icons/"

private const val OPEN_REPLACEMENT_DESCRIPTOR =
    "$NAV_BAR_REPLACEMENT_DESCRIPTOR->openReplacementFor($OBJECT_DESCRIPTOR)Z"
private const val OVERRIDE_ICON_DESCRIPTOR =
    "$NAV_BAR_REPLACEMENT_DESCRIPTOR->overrideIcon($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"
private const val OVERRIDE_LABEL_DESCRIPTOR =
    "$NAV_BAR_REPLACEMENT_DESCRIPTOR->overrideLabel($OBJECT_DESCRIPTOR$STRING_DESCRIPTOR)$STRING_DESCRIPTOR"
private const val REGISTER_DESTINATION_DESCRIPTOR =
    "$NAV_BAR_CATALOG_DESCRIPTOR->registerDestination($STRING_DESCRIPTOR I$OBJECT_DESCRIPTOR I)V"
private const val REGISTER_ICON_DESCRIPTOR =
    "$NAV_BAR_CATALOG_DESCRIPTOR->registerIcon($STRING_DESCRIPTOR$OBJECT_DESCRIPTOR I$STRING_DESCRIPTOR)V"
private const val REGISTER_TAB_DESCRIPTOR =
    "$NAV_BAR_CATALOG_DESCRIPTOR->registerTab($STRING_DESCRIPTOR I$STRING_DESCRIPTOR)V"
private const val SET_DESTINATION_CLICK_DESCRIPTOR =
    "$NAV_BAR_REPLACEMENT_DESCRIPTOR->setDestinationClick($STRING_DESCRIPTOR$FUNCTION0_DESCRIPTOR)V"

private data class NavBarDestinationSpec(
    val id: String,
    val titleResourceName: String,
    val optionTitleResourceName: String,
)

private val NAV_BAR_DESTINATIONS =
    listOf(
        NavBarDestinationSpec("BOOKMARKS", "bookmarks_title", "piko_newx_nav_replace_bookmarks"),
        NavBarDestinationSpec("PROFILE", "drawer_profile_title", "piko_newx_nav_replace_profile"),
    )

/** Navigation tab enum constants and their localized names. */
private val NAV_BAR_TAB_OPTIONS =
    listOf(
        "HOME" to "piko_newx_nav_bar_home",
        "EXPLORE" to "piko_newx_nav_bar_explore",
        "GROK" to "piko_newx_nav_bar_grok",
        "NOTIFICATIONS" to "piko_newx_nav_bar_notifications",
        "DM" to "piko_newx_nav_bar_dm",
        "COMMUNITIES" to "piko_newx_nav_bar_communities",
        "SPACES" to "piko_newx_nav_bar_spaces",
    )

private data class ResolvedNavBarDestination(
    val spec: NavBarDestinationSpec,
    val titleResourceId: Long,
    val iconField: FieldReference,
    val method: MutableMethod,
    val callIndex: Int,
    val clickRegister: Int,
)

private data class NavBarItemContentTarget(
    val method: MutableMethod,
    val navigationField: FieldReference,
    val iconType: String,
    val rendererCallIndex: Int,
    val iconRegister: Int,
    val labelRegister: Int,
    val thisRegister: Int,
    val tabIconFields: Map<String, FieldReference>,
)

@Suppress("unused")
val customizeNewXNavBarPatch =
    bytecodePatch(
        name = "NewX: Customize navigation bar",
        description = "Reorder, hide, and replace NewX bottom navigation bar items.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        newXCustomScreen(
            id = "newx.navigation.editor",
            category = Categories.NAVIGATION,
            strings = settingStrings("piko_newx_nav_editor"),
            order = 100,
            fragmentClassDescriptor = NAV_BAR_EDITOR_DESCRIPTOR,
            iconResourceName = "ic_vector_menu",
        )

        execute {
            val tabDataMatches = NewXTabDataFingerprint.scopedMatchAll()
            val tabDataMatch = requireExactlyOne("NewX tabData builder", tabDataMatches)
            val tabData = validateNewXNavBarTabData(tabDataMatch)

            injectNavBarFilter(tabDataMatch)

            val drawerRows = resolveDrawerRowCalls()
            val destinations = NAV_BAR_DESTINATIONS.map { resolveNavBarDestination(it, drawerRows) }
            val contentTarget = resolveNavBarItemContent(tabData)
            val iconFields = destinations.map { it.iconField } + contentTarget.tabIconFields.values
            val iconDrawables = resolveIconDrawables(iconFields)

            injectSettingsRegistrations(destinations, contentTarget.tabIconFields, iconDrawables)
            injectDestinationClickCaptures(destinations)

            resolveTabChangeMethod(tabData).injectReplacementGuard()
            contentTarget.injectReplacementOverride()
        }
    }

/**
 * Filters and reorders the tab map before the landing component consumes it. The extension reads
 * the editor-managed order and hidden items from its own settings.
 */
private fun injectNavBarFilter(match: Match) {
    val target = resolveNewXNavBarFilterTarget(match)
    val workRegister =
        try {
            target.method
                .getFreeRegisterProvider(target.insertionIndex, 1, target.tabDataRegister)
                .getFreeRegister4Bit()
        } catch (exception: RuntimeException) {
            throw PatchException(
                "No safe low register available for NewX tabData filtering: ${exception.message}",
            )
        }
    if (workRegister !in 0..15) {
        throw PatchException("NewX tabData work register is not 4-bit: v$workRegister")
    }
    target.method.addInstructions(
        target.insertionIndex,
        """
            move-object/from16 v$workRegister, v${target.tabDataRegister}
            invoke-static {v$workRegister}, $NAV_BAR_FILTER_DESCRIPTOR->filter(Ljava/util/Map;)Ljava/util/Map;
            move-result-object v$workRegister
            move-object/16 v${target.tabDataRegister}, v$workRegister
        """.trimIndent(),
    )
}

context(context: BytecodePatchContext)
private fun resolveTabChangeMethod(tabData: NewXNavBarTabData): MutableMethod {
    val componentClass = context.mutableClassDefBy(tabData.componentClass)
    val candidates =
        componentClass.methods.filter { method ->
            method.implementation != null &&
                method.returnType.toString() == "V" &&
                method.parameterTypes.map(CharSequence::toString) == listOf(tabData.navigationType) &&
                method.hasStackNavigationCall()
        }
    return requireExactlyOne("NewX tab change method", candidates) { it.toString() }
}

private fun MutableMethod.hasStackNavigationCall(): Boolean =
    instructions.withIndex().any { (index, instruction) ->
        if (instruction.opcode != Opcode.IGET_OBJECT) return@any false
        val fieldLoad = instruction as? TwoRegisterInstruction ?: return@any false
        val field = instruction.getReference<FieldReference>() ?: return@any false
        val call = instructions.getOrNull(index + 1) ?: return@any false
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

/**
 * Redirects the configured navigation bar item to the captured drawer click. Every other tab
 * keeps the original tab change behavior.
 */
private fun MutableMethod.injectReplacementGuard() {
    val firstInstruction = instructions.firstOrNull()
        ?: throw PatchException("NewX tab change method has no instructions: $this")
    val receiverRegister = p0Register
    val tabRegister = p0Register + 1
    val workRegister =
        try {
            getFreeRegisterProvider(0, 1, receiverRegister, tabRegister).getFreeRegister()
        } catch (exception: RuntimeException) {
            throw PatchException("No safe register for the NewX tab replacement guard: ${exception.message}")
        }
    val continueLabel = "piko_newx_replace_continue"
    addInstructionsWithLabels(
        0,
        """
            ${invokeStaticSingle(tabRegister, OPEN_REPLACEMENT_DESCRIPTOR)}
            move-result v$workRegister
            if-eqz v$workRegister, :$continueLabel
            return-void
        """.trimIndent(),
        ExternalLabel(continueLabel, firstInstruction),
    )
}

context(context: BytecodePatchContext)
private fun resolveNavBarItemContent(tabData: NewXNavBarTabData): NavBarItemContentTarget {
    val rendererCandidates = mutableListOf<ImmutableMethodReference>()
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
    }
    val renderer =
        requireExactlyOne("NewX navigation bar item renderer", rendererCandidates) { it.toString() }
    val rendererDescriptor = renderer.toSmaliDescriptor()

    // The item content lambda captures (selected, tab, badge); R8 erases its field types, so the
    // constructor is the stable identity.
    val contentClasses = mutableListOf<Pair<String, MutableMethod>>()
    context.classDefForEach { classDef ->
        val mutableClass = context.mutableClassDefBy(classDef.type)
        val constructors =
            mutableClass.methods.filter { method ->
                method.returnType.toString() == "V" &&
                    method.parameterTypes.map(CharSequence::toString) ==
                    listOf("Z", tabData.navigationType, tabData.tabDataValueType)
            }
        if (constructors.isNotEmpty()) {
            val constructor =
                requireExactlyOne(
                    "NewX navigation bar item content constructor in ${classDef.type}",
                    constructors,
                ) { it.toString() }
            contentClasses += classDef.type.toString() to constructor
        }
    }
    val (consumerClass, contentConstructor) =
        requireExactlyOne("NewX navigation bar item content class", contentClasses) { "${it.first} ${it.second}" }

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
    context.mutableClassDefBy(consumerClass).methods.forEach { method ->
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
    val consumerClassDef = context.mutableClassDefBy(consumerClass)
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

    return NavBarItemContentTarget(
        method = consumerMethod,
        navigationField = navigationField,
        iconType = renderer.parameterTypes.first().toString(),
        rendererCallIndex = rendererCallIndex,
        iconRegister = iconRegister,
        labelRegister = labelRegister,
        thisRegister = originalRegisterCount,
        tabIconFields = consumerMethod.resolveNavigationTabIcons(tabData.navigationType, iconRegister),
    )
}

private data class PackedSwitchCase(
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
private fun MutableMethod.resolveNavigationTabIcons(
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
    return NAV_BAR_TAB_OPTIONS.associate { (name, _) ->
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
private fun resolveEnumSwitchCases(
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

private fun MutableMethod.packedSwitchCases(switchIndex: Int): List<PackedSwitchCase> {
    val instruction = instructions[switchIndex] as? BuilderInstruction31t
        ?: throw PatchException("NewX navigation icon switch is not a mutable packed switch: $this")
    val payload = instruction.target.location.instruction as? SwitchPayload
        ?: throw PatchException("NewX navigation icon switch payload is missing: $this")
    val elements =
        payload.switchElements.map { element ->
            element as? BuilderSwitchElement
                ?: throw PatchException("NewX navigation icon switch case is not mutable: $this")
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

private fun List<Instruction>.resolveCaseIconField(
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

private fun List<Instruction>.resolveDefaultIconField(
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

/**
 * Substitutes the rendered icon and label of the configured navigation bar item. Runs before the
 * resource lookup so the app still localizes the replacement label.
 *
 * The receiver cannot be read at the convergence point: the compiler reuses parameter registers
 * for the icon and label there. It is preserved in an unused local at method entry instead.
 */
/**
 * Substitutes the rendered icon and label of the configured navigation bar item.
 *
 * The hook must sit at the renderer call: the label switch's internal jump target is the
 * conversion instruction, so any earlier insertion is skipped by every case but the fall-through
 * one. The label is therefore replaced after localization instead of via its resource id.
 *
 * The receiver cannot be read at the render call either: the compiler reuses parameter registers
 * for the icon and label there, so it is preserved in an unused local at method entry.
 */
private fun NavBarItemContentTarget.injectReplacementOverride() {
    val workRegister =
        try {
            method.getFreeRegisterProvider(
                rendererCallIndex,
                1,
                iconRegister,
                labelRegister,
                thisRegister,
            ).getFreeRegister4Bit()
        } catch (exception: RuntimeException) {
            throw PatchException("No safe register for the NewX navigation bar replacement: ${exception.message}")
        }
    method.addInstructions(
        rendererCallIndex,
        """
            iget-object v$workRegister, v$thisRegister, $navigationField
            invoke-static {v$workRegister, v$iconRegister}, $OVERRIDE_ICON_DESCRIPTOR
            move-result-object v$workRegister
            check-cast v$workRegister, $iconType
            move-object/from16 v$iconRegister, v$workRegister
            iget-object v$workRegister, v$thisRegister, $navigationField
            invoke-static {v$workRegister, v$labelRegister}, $OVERRIDE_LABEL_DESCRIPTOR
            move-result-object v$labelRegister
        """.trimIndent(),
    )
    method.addInstructions(0, "move-object/from16 v$thisRegister, p0")
}

private data class DrawerRowCall(
    val method: MutableMethod,
    val callIndex: Int,
    val call: Instruction3rc,
    val titleResourceId: Int,
    val iconField: FieldReference,
)

/**
 * Collects every title-based drawer row renderer call. Rows can live in a lazy row lambda outside
 * the drawer package, so no package scope is assumed.
 */
context(context: BytecodePatchContext)
private fun resolveDrawerRowCalls(): List<DrawerRowCall> {
    val rows = mutableListOf<DrawerRowCall>()
    context.classDefForEach { classDef ->
        val mutableClass = context.mutableClassDefBy(classDef.type)
        mutableClass.methods.forEach { method ->
            if (method.implementation == null) return@forEach
            method.instructions.forEachIndexed { index, instruction ->
                if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE) return@forEachIndexed
                val call = instruction as? Instruction3rc ?: return@forEachIndexed
                val renderer = instruction.getReference<MethodReference>() ?: return@forEachIndexed
                if (!renderer.isDrawerRowRenderer()) return@forEachIndexed
                val titleRegister = call.startRegister
                val titleResourceId =
                    method.instructions.resolveTitleResourceIdAtRowCall(index, titleRegister)
                        ?: return@forEachIndexed
                val iconField =
                    method.instructions.resolveIconField(index, titleRegister + 1)
                        ?: return@forEachIndexed
                rows +=
                    DrawerRowCall(
                        method = method,
                        callIndex = index,
                        call = call,
                        titleResourceId = titleResourceId,
                        iconField = iconField,
                    )
            }
        }
    }
    return rows
}

context(context: BytecodePatchContext)
private fun resolveNavBarDestination(
    spec: NavBarDestinationSpec,
    rows: List<DrawerRowCall>,
): ResolvedNavBarDestination {
    val titleResourceId = getResourceId(ResourceType.STRING, spec.titleResourceName)
    val matches = rows.filter { it.titleResourceId.toLong() == titleResourceId }
    val row =
        requireExactlyOne(
            "NewX drawer row for ${spec.titleResourceName}",
            matches,
        ) { "${it.method} @ ${it.callIndex}" }
    return ResolvedNavBarDestination(
        spec = spec,
        titleResourceId = titleResourceId,
        iconField = row.iconField,
        method = row.method,
        callIndex = row.callIndex,
        clickRegister = row.call.startRegister + 2,
    )
}

private fun List<Instruction>.resolveTitleResourceIdAtRowCall(
    callIndex: Int,
    titleRegister: Int,
): Int? {
    for (index in callIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.opcode != Opcode.MOVE_RESULT_OBJECT) continue
        val resultRegister = (instruction as? OneRegisterInstruction)?.registerA ?: continue
        if (!valueReachesRegister(index, resultRegister, callIndex, titleRegister)) continue
        val conversionIndex = index - 1
        val conversion = getOrNull(conversionIndex) ?: continue
        val conversionReference = conversion.getReference<MethodReference>() ?: continue
        if (!conversionReference.isStringResourceLookup()) continue
        val registers = conversion.registersUsed
        if (registers.size != 2) continue
        return resolveIntegerLiteralOnCurrentPath(conversionIndex, registers[1])
    }
    return null
}

private fun List<Instruction>.resolveIconField(
    callIndex: Int,
    iconRegister: Int,
): FieldReference? {
    var register = iconRegister
    for (index in callIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return null
            if (move.registerA == register) {
                register = move.registerB
            }
            continue
        }
        if (instruction.opcode == Opcode.SGET_OBJECT) {
            if ((instruction as? OneRegisterInstruction)?.registerA == register) {
                return instruction.getReference<FieldReference>()
            }
            continue
        }
        if (instruction.destinationRegisterOrNull() == register) return null
    }
    return null
}

context(context: BytecodePatchContext)
private fun injectSettingsRegistrations(
    destinations: List<ResolvedNavBarDestination>,
    tabIconFields: Map<String, FieldReference>,
    iconDrawables: Map<String, Int>,
) {
    val destinationInstructions =
        destinations.joinToString("\n") { destination ->
            """
                const-string v0, "${destination.spec.id}"
                const v1, ${destination.titleResourceId.toSmaliLiteral()}
                sget-object v2, ${destination.iconField}
                const v3, ${iconDrawables.getValue(destination.iconField.toString()).toSmaliLiteral()}
                invoke-static {v0, v1, v2, v3}, $REGISTER_DESTINATION_DESCRIPTOR
            """.trimIndent()
        }
    val tabTitles = NAV_BAR_TAB_OPTIONS.toMap()
    val iconInstructions =
        (destinations.map { destination ->
            Triple(
                "ICON_${destination.spec.id}",
                destination.iconField,
                destination.spec.optionTitleResourceName,
            )
        } + tabIconFields.map { (name, field) ->
            Triple("ICON_$name", field, tabTitles.getValue(name))
        }).joinToString("\n") { (id, field, labelResourceName) ->
            """
                const-string v0, "$id"
                sget-object v1, $field
                const v2, ${iconDrawables.getValue(field.toString()).toSmaliLiteral()}
                const-string v3, "$labelResourceName"
                invoke-static {v0, v1, v2, v3}, $REGISTER_ICON_DESCRIPTOR
            """.trimIndent()
        }
    val tabInstructions =
        NAV_BAR_TAB_OPTIONS.joinToString("\n") { (name, labelResourceName) ->
            val drawableResource =
                iconDrawables.getValue(tabIconFields.getValue(name).toString()).toSmaliLiteral()
            """
                const-string v0, "$name"
                const v1, $drawableResource
                const-string v2, "$labelResourceName"
                invoke-static {v0, v1, v2}, $REGISTER_TAB_DESCRIPTOR
            """.trimIndent()
        }
    SettingsRegistrationState.inject(
        context,
        destinationInstructions + "\n" + iconInstructions + "\n" + tabInstructions,
    )
}

context(context: BytecodePatchContext)
private fun resolveIconDrawables(fields: Collection<FieldReference>): Map<String, Int> {
    val drawables = mutableMapOf<String, Int>()
    fields.groupBy { field -> field.definingClass.toString() }.forEach { (classType, classFields) ->
        val iconClass = context.mutableClassDefBy(classType)
        val initializers =
            iconClass.methods.filter { method ->
                method.name == "<clinit>" && method.parameterTypes.isEmpty() && method.returnType == "V"
            }
        val initializer =
            requireExactlyOne("NewX icon initializer for $classType", initializers) { it.toString() }
        val iconTypes = classFields.map { field -> field.type.toString() }.distinct()
        val iconType = requireExactlyOne("NewX icon type for $classType", iconTypes)
        val available = initializer.resolveIconDrawableMap(iconType)
        classFields.forEach { field ->
            drawables[field.toString()] =
                available[field.toString()]
                    ?: throw PatchException("NewX icon drawable was not resolved for $field")
        }
    }
    return drawables
}

/** Maps each resolved static icon field to its drawable resource id. */
private fun MutableMethod.resolveIconDrawableMap(iconType: String): Map<String, Int> {
    val drawables = linkedMapOf<String, Int>()
    var allocationRegister: Int? = null
    var drawableResource: Int? = null
    instructions.forEachIndexed { index, instruction ->
        if (instruction.opcode == Opcode.NEW_INSTANCE &&
            instruction.getReference<TypeReference>()?.toString() == iconType
        ) {
            allocationRegister = (instruction as? OneRegisterInstruction)?.registerA
            drawableResource = null
            return@forEachIndexed
        }
        if (instruction.opcode == Opcode.INVOKE_DIRECT) {
            val reference = instruction.getReference<MethodReference>()
            val registers = instruction.registersUsed
            if (reference?.definingClass?.toString() == iconType &&
                reference.name == "<init>" &&
                reference.parameterTypes.map { it.toString() } == listOf("I") &&
                registers.size == 2 &&
                registers[0] == allocationRegister
            ) {
                drawableResource = instructions.resolveIntegerLiteralOnCurrentPath(index, registers[1])
            }
            return@forEachIndexed
        }
        if (instruction.opcode != Opcode.SPUT_OBJECT) return@forEachIndexed
        val field = instruction.getReference<FieldReference>()
        val register = (instruction as? OneRegisterInstruction)?.registerA
        val resource = drawableResource
        if (field != null && register != null && resource != null &&
            field.type.toString() == iconType && register == allocationRegister
        ) {
            drawables[field.toString()] = resource
            allocationRegister = null
            drawableResource = null
        }
    }
    return drawables
}

context(context: BytecodePatchContext)
private fun injectDestinationClickCaptures(destinations: List<ResolvedNavBarDestination>) {
    val classDef = context.mutableClassDefBy(NAV_BAR_REPLACEMENT_DESCRIPTOR)
    destinations.sortedByDescending { it.callIndex }.forEach { destination ->
        val destinationIndex =
            NAV_BAR_DESTINATIONS.indexOfFirst { spec -> spec.id == destination.spec.id }
        if (destinationIndex < 0) {
            throw PatchException("NewX destination is not registered: ${destination.spec.id}")
        }
        val bridgeName = "captureDestinationClick$destinationIndex"
        if (classDef.methods.any { method -> method.name == bridgeName }) {
            throw PatchException("NewX destination click bridge already exists: $bridgeName")
        }

        // The row call site has no spare low register for the destination id string, so the id is
        // baked into a one-argument bridge method that the row can call with the click alone.
        val implementation =
            MethodImplementationBuilder(2).apply {
                addInstruction("return-void".toInstruction())
            }.methodImplementation
        val bridgeMethod =
            MutableMethod(
                ImmutableMethod(
                    classDef.type,
                    bridgeName,
                    listOf(ImmutableMethodParameter(FUNCTION0_DESCRIPTOR, emptySet(), null)),
                    "V",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    emptySet(),
                    emptySet(),
                    implementation,
                ),
            )
        classDef.methods.add(bridgeMethod)
        val placeholderImplementation =
            bridgeMethod.implementation
                ?: throw PatchException("NewX destination click bridge has no implementation")
        placeholderImplementation.removeInstruction(placeholderImplementation.instructions.lastIndex)
        bridgeMethod.addInstructions(
            0,
            """
                const-string v0, "${destination.spec.id}"
                invoke-static {v0, p0}, $SET_DESTINATION_CLICK_DESCRIPTOR
                return-void
            """.trimIndent(),
        )

        destination.method.addInstructions(
            destination.callIndex,
            invokeStaticSingle(
                destination.clickRegister,
                "$NAV_BAR_REPLACEMENT_DESCRIPTOR->$bridgeName($FUNCTION0_DESCRIPTOR)V",
            ),
        )
    }
}

private fun invokeStaticSingle(register: Int, target: String): String =
    if (register in 0..15) {
        "invoke-static {v$register}, $target"
    } else {
        "invoke-static/range {v$register .. v$register}, $target"
    }

private fun Long.toSmaliLiteral(): String = "0x${toString(16)}"

private fun Int.toSmaliLiteral(): String = "0x${toString(16)}"

private fun Instruction.referencesMethod(descriptor: String): Boolean =
    getReference<MethodReference>()?.toSmaliDescriptor() == descriptor

private fun MethodReference.toSmaliDescriptor(): String =
    "${definingClass}->${name}(${parameterTypes.joinToString("")})${returnType}"
