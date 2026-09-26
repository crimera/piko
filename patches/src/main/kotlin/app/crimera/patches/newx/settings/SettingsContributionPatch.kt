package app.crimera.patches.newx.settings

import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Block
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
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
        if (loadMethod.instructions.isEmpty()) {
            error("NewX settings registry load target has no instructions")
        }
        val insertion =
            loadMethod.insertHook(
                index = 0,
                // The old external label pointed at the original first instruction, so a branch
                // that reached it kept skipping the load guard; the labels stay where they were.
                relocateBranchTargets = false,
            ) {
                invokeStatic(methodReference("$SETTINGS_REGISTRY_DESCRIPTOR->isLoaded()Z"))
                moveResult(0, "Z")
                ifNez(0, Target.Original)
            }
        states[context] =
            ContextState(
                loadMethod = WeakReference(loadMethod),
                groupTracker = SettingsGroupRegistrationTracker(),
                nextRegistrationIndex = insertion.lastIndex + 1,
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
    fun inject(context: BytecodePatchContext, block: Block.() -> Unit) {
        val state = states[context] ?: error("NewX settings registration state was not prepared")
        val loadMethod = state.loadMethod.get()
            ?: error("NewX settings registry load target was not prepared")
        // Keep parent registration before later contribution items. Inserting every block at zero
        // would reverse the blocks and allow an item to run before its shared group. Every
        // registration lands in front of the load guard's jump target, whose labels stay on it.
        val insertion =
            loadMethod.insertHook(
                index = state.nextRegistrationIndex,
                relocateBranchTargets = false,
                block = block,
            )
        state.nextRegistrationIndex = insertion.lastIndex + 1
    }
}

context(context: BytecodePatchContext)
internal fun injectSettingsContribution(contribution: SettingsContributionCatalog) {
    SettingsRegistrationState.inject(context) { registrationInstructions(context, contribution) }
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
    val settingRegister = registerRange.first
    val defaultRegister = registerRange.last
    val insertion =
        method.insertHook(
            index = index,
            // The old plain insertion left incoming labels on the original instruction.
            relocateBranchTargets = false,
        ) {
            constString(settingRegister, id)
            constInt(defaultRegister, if (defaultValue) 1 else 0)
            invokeStatic(
                methodReference(
                    "$SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;Z)Z",
                ),
                settingRegister,
                defaultRegister,
            )
            moveResult(settingRegister, "Z")
        }
    return InjectedSettingRead(register = settingRegister, nextIndex = insertion.lastIndex + 1)
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

// The caller reserves this register when normal liveness-based allocation cannot find safe
// locals in dense methods (e.g. Compose presenters holding every low register live at the hook
// point). The register must be a fresh local below the parameter block: clone with
// additionalRegisters = numberOfParameterRegisters + needed, then use originalRegisterCount
// upwards. BYTE/range form keeps high locals (v16+) usable, unlike FOUR_BIT.
internal fun MultiChoiceSettingDefinition.injectReadWithRegister(
    method: MutableMethod,
    index: Int,
    register: Int,
): InjectedSettingRead {
    require(register in 0..255) {
        "NewX setting read requires a byte register v0..v255, got v$register"
    }
    val insertion =
        method.insertHook(
            index = index,
            // The old plain insertion left incoming labels on the original instruction.
            relocateBranchTargets = false,
        ) {
            constString(register, id)
            invokeStatic(
                methodReference(
                    "$SETTINGS_REGISTRY_DESCRIPTOR->getStringSetOrDefault(Ljava/lang/String;)Ljava/util/Set;",
                ),
                register,
            )
            moveResult(register, "Ljava/util/Set;")
        }
    return InjectedSettingRead(register = register, nextIndex = insertion.lastIndex + 1)
}

private fun SettingItemDefinition.injectSettingRead(
    method: MutableMethod,
    index: Int,
    excludedRegisters: List<Int>,
    registerConstraint: SettingReadRegisterConstraint,
    methodName: String,
    returnType: String,
): InjectedSettingRead {
    val target =
        methodReference("$SETTINGS_REGISTRY_DESCRIPTOR->$methodName(Ljava/lang/String;)$returnType")
    val insertion =
        method.insertHook(
            index = index,
            excludedRegisters = excludedRegisters,
            // The old plain insertion left incoming labels on the original instruction.
            relocateBranchTargets = false,
        ) {
            val register = scratchRegister(registerConstraint.toRegisterLimit())
            constString(register, id)
            invokeStatic(target, register)
            moveResult(register, returnType)
        }
    return InjectedSettingRead(
        register = insertion.registers.single(),
        nextIndex = insertion.lastIndex + 1,
    )
}

private fun SettingReadRegisterConstraint.toRegisterLimit(): RegisterLimit =
    when (this) {
        SettingReadRegisterConstraint.FOUR_BIT -> RegisterLimit.FOUR_BIT
        SettingReadRegisterConstraint.BYTE -> RegisterLimit.BYTE
    }

private fun Block.registrationInstructions(
    context: BytecodePatchContext,
    catalog: SettingsContributionCatalog,
) {
    catalog.categories.forEach { category ->
        appendGroupRegistrationIfNeeded(context, null, category, category = true)
        category.children.forEach { child ->
            appendNodeRegistration(context, category.id, child)
        }
    }
}

private fun Block.appendNodeRegistration(
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

private fun Block.appendGroupRegistrationIfNeeded(
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

private fun Block.appendGroupRegistration(
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

private fun Block.appendItemRegistration(
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
            booleanValue(item.visible),
        )
    appendInvoke(values, "$method(${values.descriptor()})V")
}

private fun Block.appendConfiguration(
    id: String,
    values: List<RegistrationValue>,
    method: String,
) {
    appendInvoke(listOf(stringValue(id)) + values, method)
}

private fun Block.appendInvoke(
    values: List<RegistrationValue>,
    method: String,
) {
    require(values.size <= SETTINGS_REGISTRATION_REGISTER_COUNT) {
        "NewX registry call uses too many registers: $method"
    }
    // Registration payloads use v0.. upward, which `prepareSettingsRegistryLoad` guarantees exist.
    values.forEachIndexed { index, value -> value.emit(this, index) }
    invokeStatic(
        methodReference("$SETTINGS_REGISTRY_DESCRIPTOR->$method"),
        *IntArray(values.size) { it },
    )
}

private class RegistrationValue(
    val type: String,
    val emit: Block.(Int) -> Unit,
)

private fun stringValue(value: String) =
    RegistrationValue("Ljava/lang/String;") { register -> constString(register, value) }

private fun nullableStringValue(value: String?) =
    value?.let(::stringValue)
        ?: RegistrationValue("Ljava/lang/String;") { register -> constInt(register, 0) }

private fun booleanValue(value: Boolean) =
    RegistrationValue("Z") { register -> constInt(register, if (value) 1 else 0) }

private fun intValue(value: Int) =
    RegistrationValue("I") { register -> constInt(register, value) }

private fun List<RegistrationValue>.descriptor() = joinToString("") { it.type }

private fun ToggleSettingDefinition.guard(
    method: MutableMethod,
    index: Int,
    enabled: Boolean,
) {
    val read =
        injectRead(
            method = method,
            index = index,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    method.insertHook(
        index = read.nextIndex,
        // The external label used to sit on the instruction directly behind the read, so branches
        // reaching it kept skipping the guard; the labels stay where the old insertion left them.
        relocateBranchTargets = false,
    ) {
        // `if-eqz`/`if-nez` are format 21t, which encodes a byte register.
        if (enabled) {
            ifEqz(read.register, Target.Original)
        } else {
            ifNez(read.register, Target.Original)
        }
        returnVoid()
    }
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
    val targetIndex = method.instructions.indexOfFirst { instruction -> instruction === target }
    if (targetIndex < 0) {
        throw PatchException("NewX setting branch target is not an instruction of $method")
    }
    // The injected branch is one instruction in front of the instruction that carried the old
    // external label, so an instruction `targetIndex - index` places past the read is that label.
    val offset = targetIndex - index
    if (offset < 0) {
        throw PatchException(
            "NewX setting branch target at instruction $targetIndex precedes the read insertion " +
                "point $index in $method; a typed hook cannot branch backwards.",
        )
    }
    val read =
        injectRead(
            method = method,
            index = index,
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val typedTarget =
        if (offset == 0) Target.Original else Target.AfterOriginal(offset)
    method.insertHook(
        index = read.nextIndex,
        // The external label used to sit on the target instruction, so branches that reached it
        // kept skipping the hook; the labels stay where the old insertion left them.
        relocateBranchTargets = false,
    ) {
        // `if-eqz`/`if-nez` are format 21t, which encodes a byte register.
        if (enabled) {
            ifNez(read.register, typedTarget)
        } else {
            ifEqz(read.register, typedTarget)
        }
    }
}
