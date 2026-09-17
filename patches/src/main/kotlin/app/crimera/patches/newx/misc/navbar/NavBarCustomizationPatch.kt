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
import app.morphe.patches.all.misc.resources.hasResourceId
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.Method
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
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"

private const val OPEN_REPLACEMENT_DESCRIPTOR =
    "$NAV_BAR_REPLACEMENT_DESCRIPTOR->openReplacementFor($OBJECT_DESCRIPTOR)Z"
private const val OVERRIDE_ICON_DESCRIPTOR =
    "$NAV_BAR_REPLACEMENT_DESCRIPTOR->overrideIcon($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"
private const val OVERRIDE_LABEL_DESCRIPTOR =
    "$NAV_BAR_REPLACEMENT_DESCRIPTOR->overrideLabel($OBJECT_DESCRIPTOR$STRING_DESCRIPTOR)$STRING_DESCRIPTOR"
private const val REGISTER_DESTINATION_DESCRIPTOR =
    "$NAV_BAR_CATALOG_DESCRIPTOR->registerDestination($STRING_DESCRIPTOR I$OBJECT_DESCRIPTOR I)V"
private const val REGISTER_TAB_DESCRIPTOR =
    "$NAV_BAR_CATALOG_DESCRIPTOR->registerTab($STRING_DESCRIPTOR I$STRING_DESCRIPTOR)V"
private const val SET_DESTINATION_CLICK_DESCRIPTOR =
    "$NAV_BAR_REPLACEMENT_DESCRIPTOR->setDestinationClick($STRING_DESCRIPTOR$FUNCTION0_DESCRIPTOR)V"

private data class NavBarDestinationSpec(
    val id: String,
    val titleResourceName: String,
    val optional: Boolean = false,
    val alternateTitleResourceName: String? = null,
)

private val NAV_BAR_DESTINATIONS =
    listOf(
        NavBarDestinationSpec("BOOKMARKS", "bookmarks_title"),
        NavBarDestinationSpec("PROFILE", "drawer_profile_title"),
        NavBarDestinationSpec("LISTS", "drawer_lists"),
        NavBarDestinationSpec(
            "COMMUNITIES",
            "drawer_communities_title",
        ),
        NavBarDestinationSpec(
            "HISTORY",
            "drawer_history_title",
            optional = true,
            alternateTitleResourceName = "bookmarks_title",
        ),
        NavBarDestinationSpec("SPACES", "spaces_tab_name"),
        NavBarDestinationSpec(
            "CREATOR_STUDIO",
            "creator_studio_drawer_menu_title",
        ),
    )

private data class ResolvedNavBarDestination(
    val spec: NavBarDestinationSpec,
    val titleResourceId: Long,
    val iconField: FieldReference,
    val method: MutableMethod,
    val callIndex: Int,
    val clickRegister: Int,
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
            iconResourceName = "ic_vector_bulleted_list",
        )

        execute {
            val tabDataMatches = NewXTabDataFingerprint.scopedMatchAll()
            val tabDataMatch = requireExactlyOne("NewX tabData builder", tabDataMatches)
            val tabData = validateNewXNavBarTabData(tabDataMatch)

            injectNavBarFilter(tabDataMatch)

            val drawerRows = resolveDrawerRowCalls()
            val destinations =
                NAV_BAR_DESTINATIONS.mapNotNull { spec ->
                    if (!hasResourceId(ResourceType.STRING, spec.titleResourceName)) {
                        if (spec.optional) return@mapNotNull null
                        throw PatchException(
                            "NewX required navigation destination title resource is missing: " +
                                spec.titleResourceName,
                        )
                    }
                    resolveNavBarDestination(spec, drawerRows)
                }
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
    val method: Method,
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
    // Keep this APK-wide scan immutable. Calling mutableClassDefBy for every class materializes a
    // mutable proxy for the entire APK and is the source of patch-time OOMs on manager-sized heaps.
    context.classDefForEach { classDef ->
        classDef.methods.forEach { method ->
            if (method.implementation == null) return@forEach
            val methodInstructions = method.implementation?.instructions?.toList() ?: return@forEach
            methodInstructions.forEachIndexed { index, instruction ->
                if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE) return@forEachIndexed
                val call = instruction as? Instruction3rc ?: return@forEachIndexed
                val renderer = instruction.getReference<MethodReference>() ?: return@forEachIndexed
                if (!renderer.isDrawerRowRenderer()) return@forEachIndexed
                val titleRegister = call.startRegister
                val titleResourceId =
                    methodInstructions.resolveTitleResourceIdAtRowCall(index, titleRegister)
                        ?: return@forEachIndexed
                val iconField =
                    methodInstructions.resolveIconField(index, titleRegister + 1)
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
    val alternateTitleResourceId =
        spec.alternateTitleResourceName?.let { getResourceId(ResourceType.STRING, it) }
    val titleResourceIds = listOfNotNull(titleResourceId, alternateTitleResourceId).toSet()
    val matches = rows.filter { it.titleResourceId.toLong() in titleResourceIds }
    val row =
        requireExactlyOne(
            "NewX drawer row for ${spec.titleResourceName}",
            matches,
        ) { "${it.method} @ ${it.callIndex}" }
    val mutableMethodCandidates =
        context.mutableClassDefBy(row.method.definingClass).methods.filter { method ->
            method.name == row.method.name &&
                method.returnType.toString() == row.method.returnType.toString() &&
                method.parameterTypes.map(CharSequence::toString) ==
                row.method.parameterTypes.map(CharSequence::toString)
        }
    val mutableMethod =
        requireExactlyOne(
            "NewX mutable drawer row method ${row.method}",
            mutableMethodCandidates,
        ) { it.toString() }
    return ResolvedNavBarDestination(
        spec = spec,
        titleResourceId = titleResourceId.toLong(),
        iconField = row.iconField,
        method = mutableMethod,
        callIndex = row.callIndex,
        clickRegister = row.call.startRegister + 2,
    )
}

internal fun List<Instruction>.resolveTitleResourceIdAtRowCall(
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

internal fun List<Instruction>.resolveIconField(
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
    val tabInstructions =
        NAV_BAR_NATIVE_TAB_OPTIONS.joinToString("\n") { (name, labelResourceName) ->
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
        destinationInstructions + "\n" + tabInstructions,
    )
}

context(context: BytecodePatchContext)
internal fun resolveIconDrawables(fields: Collection<FieldReference>): Map<String, Int> {
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
internal fun MutableMethod.resolveIconDrawableMap(iconType: String): Map<String, Int> {
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

