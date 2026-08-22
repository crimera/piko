package app.crimera.patches.newx.settings

import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal fun newXSettingsContributionPatch(
    contribution: SettingsContributionCatalog,
) = bytecodePatch(default = false) {
    dependsOn(newXSettingsPatch)

    execute {
        injectSettingsContribution(contribution)
    }
}

private data class SettingsGroupRegistration(
    val parentId: String?,
    val id: String,
    val titleResourceName: String,
    val summaryResourceName: String?,
    val iconResourceName: String?,
    val order: Int,
    val category: Boolean,
)

internal class SettingsGroupRegistrationTracker {
    private val registrations = linkedMapOf<String, SettingsGroupRegistration>()

    fun shouldEmit(
        parentId: String?,
        group: SettingsGroupDefinition,
        category: Boolean,
    ): Boolean {
        val candidate =
            SettingsGroupRegistration(
                parentId = parentId,
                id = group.id,
                titleResourceName = group.titleResourceName,
                summaryResourceName = group.summaryResourceName,
                iconResourceName = group.iconResourceName,
                order = group.order,
                category = category,
            )
        val existing = registrations[candidate.id]
        if (existing == null) {
            registrations[candidate.id] = candidate
            return true
        }
        if (existing != candidate) {
            throw PatchException(
                "Conflicting NewX settings group registration for ${candidate.id}: " +
                    "existing=$existing, candidate=$candidate",
            )
        }
        return false
    }
}

internal object SettingsRegistrationState {
    private data class ContextState(
        val loadMethod: WeakReference<MutableMethod>,
        val groupTracker: SettingsGroupRegistrationTracker,
        var nextRegistrationIndex: Int = 0,
    )

    private val states = WeakHashMap<BytecodePatchContext, ContextState>()

    @Synchronized
    fun prepare(context: BytecodePatchContext, loadMethod: MutableMethod) {
        val originalFirstInstruction =
            loadMethod.instructions.firstOrNull()
                ?: error("NewX settings registry load target has no instructions")
        val sizeBefore = loadMethod.instructions.size
        loadMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $SETTINGS_REGISTRY_DESCRIPTOR->isLoaded()Z
                move-result v0
                if-nez v0, :piko_newx_settings_registration_done
            """.trimIndent(),
            ExternalLabel(
                "piko_newx_settings_registration_done",
                originalFirstInstruction,
            ),
        )
        states[context] =
            ContextState(
                loadMethod = WeakReference(loadMethod),
                groupTracker = SettingsGroupRegistrationTracker(),
                nextRegistrationIndex = loadMethod.instructions.size - sizeBefore,
            )
    }

    @Synchronized
    fun shouldEmit(
        context: BytecodePatchContext,
        parentId: String?,
        group: SettingsGroupDefinition,
        category: Boolean,
    ): Boolean =
        states[context]?.groupTracker?.shouldEmit(parentId, group, category)
            ?: error("NewX settings registration state was not prepared")

    @Synchronized
    fun inject(context: BytecodePatchContext, instructions: String) {
        val state = states[context] ?: error("NewX settings registration state was not prepared")
        val loadMethod = state.loadMethod.get()
            ?: error("NewX settings registry load target was not prepared")
        // Keep parent registration before later contribution items. Inserting every block at zero
        // would reverse the blocks and allow an item to run before its shared group.
        val sizeBefore = loadMethod.instructions.size
        loadMethod.addInstructions(state.nextRegistrationIndex, instructions)
        state.nextRegistrationIndex += loadMethod.instructions.size - sizeBefore
    }
}

context(context: BytecodePatchContext)
internal fun injectSettingsContribution(contribution: SettingsContributionCatalog) {
    SettingsRegistrationState.inject(context, contribution.registrationInstructions(context))
}

internal data class InjectedSettingRead(
    val register: Int,
    val nextIndex: Int,
)

internal enum class SettingReadRegisterConstraint {
    FOUR_BIT,
    BYTE,
}

internal fun ToggleSettingDefinition.injectRead(
    method: MutableMethod,
    index: Int,
    excludedRegisters: List<Int> = emptyList(),
    registerConstraint: SettingReadRegisterConstraint = SettingReadRegisterConstraint.BYTE,
): InjectedSettingRead =
    injectSettingRead(
        method = method,
        index = index,
        excludedRegisters = excludedRegisters,
        registerConstraint = registerConstraint,
        methodName = "getBooleanOrDefault",
        returnType = "Z",
    )

// The caller reserves this pair when normal liveness-based allocation cannot find safe locals.
internal fun ToggleSettingDefinition.injectReadWithDefault(
    method: MutableMethod,
    index: Int,
    defaultValue: Boolean,
    registerRange: IntRange,
): InjectedSettingRead {
    require(
        registerRange.first >= 0 &&
            registerRange.last == registerRange.first + 1 &&
            registerRange.last <= 255,
    ) {
        "NewX default setting read requires two consecutive registers in v0..v255: $registerRange"
    }
    method.addInstructions(
        index,
        valueReadWithDefaultInstructions(
            defaultValue = defaultValue,
            registerRange = registerRange,
        ),
    )
    return InjectedSettingRead(
        register = registerRange.first,
        nextIndex = index + READ_WITH_DEFAULT_INSTRUCTION_COUNT,
    )
}

internal fun TextInputSettingDefinition.injectRead(
    method: MutableMethod,
    index: Int,
    excludedRegisters: List<Int> = emptyList(),
    registerConstraint: SettingReadRegisterConstraint = SettingReadRegisterConstraint.BYTE,
): InjectedSettingRead =
    injectSettingRead(
        method = method,
        index = index,
        excludedRegisters = excludedRegisters,
        registerConstraint = registerConstraint,
        methodName = "getStringOrDefault",
        returnType = "Ljava/lang/String;",
    )

internal fun SingleChoiceSettingDefinition.injectRead(
    method: MutableMethod,
    index: Int,
    excludedRegisters: List<Int> = emptyList(),
    registerConstraint: SettingReadRegisterConstraint = SettingReadRegisterConstraint.BYTE,
): InjectedSettingRead =
    injectSettingRead(
        method = method,
        index = index,
        excludedRegisters = excludedRegisters,
        registerConstraint = registerConstraint,
        methodName = "getStringOrDefault",
        returnType = "Ljava/lang/String;",
    )

internal fun MultiChoiceSettingDefinition.injectRead(
    method: MutableMethod,
    index: Int,
    excludedRegisters: List<Int> = emptyList(),
    registerConstraint: SettingReadRegisterConstraint = SettingReadRegisterConstraint.FOUR_BIT,
): InjectedSettingRead =
    injectSettingRead(
        method = method,
        index = index,
        excludedRegisters = excludedRegisters,
        registerConstraint = registerConstraint,
        methodName = "getStringSetOrDefault",
        returnType = "Ljava/util/Set;",
    )

private fun SettingItemDefinition.injectSettingRead(
    method: MutableMethod,
    index: Int,
    excludedRegisters: List<Int>,
    registerConstraint: SettingReadRegisterConstraint,
    methodName: String,
    returnType: String,
): InjectedSettingRead {
    val register = allocateSettingRegister(method, index, excludedRegisters, registerConstraint)
    method.addInstructions(
        index,
        valueReadInstructions(methodName, returnType, register, registerConstraint),
    )
    return InjectedSettingRead(register = register, nextIndex = index + READ_INSTRUCTION_COUNT)
}

private fun allocateSettingRegister(
    method: MutableMethod,
    index: Int,
    excludedRegisters: List<Int>,
    constraint: SettingReadRegisterConstraint,
): Int {
    val provider =
        try {
            method.getFreeRegisterProvider(index, 1, *excludedRegisters.toIntArray())
        } catch (exception: RuntimeException) {
            throw IllegalStateException(
                "No free register available for NewX setting read at index $index",
                exception,
            )
        }
    val register =
        try {
            when (constraint) {
                SettingReadRegisterConstraint.FOUR_BIT -> provider.getFreeRegister4Bit()
                SettingReadRegisterConstraint.BYTE -> provider.getFreeRegister()
            }
        } catch (exception: RuntimeException) {
            throw IllegalStateException(
                "No ${constraint.name.lowercase()} register available for NewX setting read " +
                    "at index $index",
                exception,
            )
        }
    val maximumRegister = if (constraint == SettingReadRegisterConstraint.FOUR_BIT) 15 else 255
    if (register > maximumRegister) {
        throw IllegalStateException(
            "No ${constraint.name.lowercase()} register available for NewX setting read " +
                "at index $index; lowest available register is v$register",
        )
    }
    return register
}

private fun SettingItemDefinition.valueReadInstructions(
    methodName: String,
    returnType: String,
    destinationRegister: Int,
    registerConstraint: SettingReadRegisterConstraint,
): String {
    require(destinationRegister in 0..255) {
        "Invalid setting destination register: v$destinationRegister"
    }
    val moveResult = if (returnType.startsWith("L")) "move-result-object" else "move-result"
    val invoke =
        when (registerConstraint) {
            SettingReadRegisterConstraint.FOUR_BIT ->
                "invoke-static {v$destinationRegister}, "

            SettingReadRegisterConstraint.BYTE ->
                "invoke-static/range {v$destinationRegister .. v$destinationRegister}, "
        }
    return """
        const-string v$destinationRegister, "${smaliString(id)}"
        ${invoke}$SETTINGS_REGISTRY_DESCRIPTOR->$methodName(Ljava/lang/String;)$returnType
        $moveResult v$destinationRegister
    """.trimIndent()
}

private fun SettingItemDefinition.valueReadWithDefaultInstructions(
    defaultValue: Boolean,
    registerRange: IntRange,
): String {
    val settingRegister = registerRange.first
    val defaultRegister = registerRange.last
    return """
        const-string v$settingRegister, "${smaliString(id)}"
        const/4 v$defaultRegister, ${if (defaultValue) "0x1" else "0x0"}
        invoke-static/range {v$settingRegister .. v$defaultRegister}, $SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;Z)Z
        move-result v$settingRegister
    """.trimIndent()
}

private fun SettingsContributionCatalog.registrationInstructions(
    context: BytecodePatchContext,
): String =
    buildString {
        categories.forEach { category ->
            appendGroupRegistrationIfNeeded(context, null, category, category = true)
            category.children.forEach { child ->
                appendNodeRegistration(context, category.id, child)
            }
        }
    }.trim()

private fun StringBuilder.appendNodeRegistration(
    context: BytecodePatchContext,
    parentId: String,
    node: SettingsNodeDefinition,
) {
    when (node) {
        is SettingsGroupDefinition -> {
            appendGroupRegistrationIfNeeded(context, parentId, node, category = false)
            node.children.forEach { child ->
                appendNodeRegistration(context, node.id, child)
            }
        }

        is ToggleSettingDefinition -> {
            appendItemRegistration("registerToggle", parentId, node)
            appendConfiguration(
                id = node.id,
                values = listOf(booleanValue(node.defaultValue), booleanValue(node.rebootApp)),
                method = "configureToggle(Ljava/lang/String;ZZ)V",
            )
        }

        is TextInputSettingDefinition -> {
            appendItemRegistration("registerTextInput", parentId, node)
            appendConfiguration(
                id = node.id,
                values =
                    listOf(
                        stringValue(node.defaultValue),
                        intValue(node.inputKind.ordinal),
                        booleanValue(node.rebootApp),
                        nullableStringValue(node.validatorClassDescriptor),
                    ),
                method = "configureTextInput(Ljava/lang/String;Ljava/lang/String;IZLjava/lang/String;)V",
            )
        }

        is SingleChoiceSettingDefinition -> {
            appendItemRegistration("registerSingleChoice", parentId, node)
            appendConfiguration(
                id = node.id,
                values =
                    listOf(
                        stringValue(node.defaultValue),
                        booleanValue(node.rebootApp),
                    ),
                method = "configureSingleChoice(Ljava/lang/String;Ljava/lang/String;Z)V",
            )
            node.options.forEach { option ->
                appendConfiguration(
                    id = node.id,
                    values =
                        listOf(
                            stringValue(option.id),
                            stringValue(option.titleResourceName),
                            booleanValue(option.id == node.defaultValue),
                        ),
                    method =
                        "registerChoiceOption(Ljava/lang/String;Ljava/lang/String;" +
                            "Ljava/lang/String;Z)V",
                )
            }
        }

        is MultiChoiceSettingDefinition -> {
            appendItemRegistration("registerMultiChoice", parentId, node)
            appendConfiguration(
                id = node.id,
                values = listOf(booleanValue(node.rebootApp)),
                method = "configureMultiChoice(Ljava/lang/String;Z)V",
            )
            node.options.forEach { option ->
                appendConfiguration(
                    id = node.id,
                    values =
                        listOf(
                            stringValue(option.id),
                            stringValue(option.titleResourceName),
                            booleanValue(option.id in node.defaultValue),
                        ),
                    method =
                        "registerChoiceOption(Ljava/lang/String;Ljava/lang/String;" +
                            "Ljava/lang/String;Z)V",
                )
            }
        }

        is ActionSettingDefinition -> {
            appendItemRegistration("registerAction", parentId, node)
            appendConfiguration(
                id = node.id,
                values = listOf(stringValue(node.handlerClassDescriptor)),
                method = "configureAction(Ljava/lang/String;Ljava/lang/String;)V",
            )
        }

        is CustomScreenSettingDefinition -> {
            appendInvoke(
                listOf(
                    stringValue(parentId),
                    stringValue(node.id),
                    stringValue(node.titleResourceName),
                    nullableStringValue(node.summaryResourceName),
                    nullableStringValue(node.iconResourceName),
                    intValue(node.order),
                ),
                "registerCustomScreen(" +
                    "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" +
                    "Ljava/lang/String;I)V",
            )
            appendConfiguration(
                id = node.id,
                values = listOf(stringValue(node.fragmentClassDescriptor)),
                method = "configureCustomScreen(Ljava/lang/String;Ljava/lang/String;)V",
            )
        }
    }
}

private fun StringBuilder.appendGroupRegistrationIfNeeded(
    context: BytecodePatchContext,
    parentId: String?,
    group: SettingsGroupDefinition,
    category: Boolean,
) {
    if (!SettingsRegistrationState.shouldEmit(context, parentId, group, category)) return
    appendGroupRegistration(
        method = if (category) "registerCategory" else "registerGroup",
        parentId = parentId,
        group = group,
    )
}

private fun StringBuilder.appendGroupRegistration(
    method: String,
    parentId: String?,
    group: SettingsGroupDefinition,
) {
    val values =
        if (parentId == null) {
            listOf(
                stringValue(group.id),
                stringValue(group.titleResourceName),
                nullableStringValue(group.summaryResourceName),
                nullableStringValue(group.iconResourceName),
                intValue(group.order),
            )
        } else {
            listOf(
                stringValue(parentId),
                stringValue(group.id),
                stringValue(group.titleResourceName),
                nullableStringValue(group.summaryResourceName),
                nullableStringValue(group.iconResourceName),
                intValue(group.order),
            )
        }
    appendInvoke(values, "$method(${values.descriptor()})V")
}

private fun StringBuilder.appendItemRegistration(
    method: String,
    parentId: String,
    item: SettingItemDefinition,
) {
    val values =
        listOf(
            stringValue(parentId),
            stringValue(item.id),
            stringValue(item.titleResourceName),
            nullableStringValue(item.summaryResourceName),
            intValue(item.order),
        )
    appendInvoke(values, "$method(${values.descriptor()})V")
}

private fun StringBuilder.appendConfiguration(
    id: String,
    values: List<SmaliValue>,
    method: String,
) {
    appendInvoke(listOf(stringValue(id)) + values, method)
}

private fun StringBuilder.appendInvoke(
    values: List<SmaliValue>,
    method: String,
) {
    require(values.size <= SETTINGS_REGISTRATION_REGISTER_COUNT) {
        "NewX registry call uses too many registers: $method"
    }
    values.forEachIndexed { index, value -> appendLine(value.instruction(index)) }
    if (values.size <= 5) {
        val registers = values.indices.joinToString(", ") { "v$it" }
        appendLine("invoke-static {$registers}, $SETTINGS_REGISTRY_DESCRIPTOR->$method")
        return
    }
    appendLine(
        "invoke-static/range {v0 .. v${values.lastIndex}}, $SETTINGS_REGISTRY_DESCRIPTOR->$method",
    )
}

private data class SmaliValue(
    val type: String,
    val instruction: (Int) -> String,
)

private fun stringValue(value: String) =
    SmaliValue("Ljava/lang/String;") { register ->
        "const-string v$register, \"${smaliString(value)}\""
    }

private fun nullableStringValue(value: String?) =
    value?.let(::stringValue) ?: SmaliValue("Ljava/lang/String;") { register -> "const/4 v$register, 0x0" }

private fun booleanValue(value: Boolean) =
    SmaliValue("Z") { register -> "const/4 v$register, ${if (value) "0x1" else "0x0"}" }

private fun intValue(value: Int) =
    SmaliValue("I") { register -> "const v$register, ${value.toSmaliLiteral()}" }

private fun List<SmaliValue>.descriptor() = joinToString("") { it.type }

private fun Int.toSmaliLiteral() = if (this < 0) "-0x${(-this).toString(16)}" else "0x${toString(16)}"

private fun smaliString(value: String) =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

private const val READ_INSTRUCTION_COUNT = 3
private const val READ_WITH_DEFAULT_INSTRUCTION_COUNT = 4

private fun ToggleSettingDefinition.guard(
    method: MutableMethod,
    index: Int,
    enabled: Boolean,
) {
    val originalInstruction =
        method.instructions.getOrNull(index)
            ?: error("NewX setting guard index is out of bounds: $index")
    val read =
        injectRead(
            method = method,
            index = index,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val label = settingLabel("guard", index)
    val branch = if (enabled) "if-eqz" else "if-nez"
    method.addInstructionsWithLabels(
        read.nextIndex,
        """
            $branch v${read.register}, :$label
            return-void
        """.trimIndent(),
        ExternalLabel(label, originalInstruction),
    )
}

internal fun ToggleSettingDefinition.returnVoidIfEnabled(
    method: MutableMethod,
    index: Int,
) = guard(method, index, enabled = true)

internal fun ToggleSettingDefinition.returnVoidIfDisabled(
    method: MutableMethod,
    index: Int,
) = guard(method, index, enabled = false)

internal fun ToggleSettingDefinition.branchIfEnabled(
    method: MutableMethod,
    index: Int,
    target: Instruction,
) = branch(method, index, target, enabled = true)

internal fun ToggleSettingDefinition.branchIfDisabled(
    method: MutableMethod,
    index: Int,
    target: Instruction,
) = branch(method, index, target, enabled = false)

private fun ToggleSettingDefinition.branch(
    method: MutableMethod,
    index: Int,
    target: Instruction,
    enabled: Boolean,
) {
    val read =
        injectRead(
            method = method,
            index = index,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val label = settingLabel("branch", index)
    val branch = if (enabled) "if-nez" else "if-eqz"
    method.addInstructionsWithLabels(
        read.nextIndex,
        "$branch v${read.register}, :$label",
        ExternalLabel(label, target),
    )
}

private fun ToggleSettingDefinition.settingLabel(
    operation: String,
    index: Int,
): String =
    "piko_newx_setting_${operation}_" +
        id.replace(Regex("[^a-zA-Z0-9_]"), "_") +
        "_$index"
