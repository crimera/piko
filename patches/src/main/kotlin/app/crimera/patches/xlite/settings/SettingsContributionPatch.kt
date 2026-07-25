package app.crimera.patches.xlite.settings

import app.crimera.patches.xlite.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable

internal fun xLiteSettingsContributionPatch(block: SettingsContributionBuilder.() -> Unit) =
    SettingsContributionBuilder()
        .apply(block)
        .build()
        .let { contribution ->
            bytecodePatch(default = false) {
                dependsOn(xLiteSettingsPatch)

                execute {
                    val registryClass = mutableClassDefBy(SETTINGS_REGISTRY_DESCRIPTOR)
                    var loadMethod =
                        registryClass.methods.singleOrNull { method ->
                            method.name == "load" &&
                                method.parameterTypes.isEmpty() &&
                                method.returnType == "V"
                        } ?: error("X-Lite SettingsRegistry.load() was not found")
                    val registerCount = loadMethod.implementation?.registerCount ?: 0
                    if (registerCount < REGISTRATION_REGISTER_COUNT) {
                        val expandedMethod =
                            loadMethod.cloneMutable(
                                additionalRegisters = REGISTRATION_REGISTER_COUNT - registerCount,
                            )
                        registryClass.methods.remove(loadMethod)
                        registryClass.methods.add(expandedMethod)
                        loadMethod = expandedMethod
                    }
                    loadMethod.addInstructions(0, contribution.registrationInstructions())
                }
            }
        }

private const val REGISTRATION_REGISTER_COUNT = 5

internal fun ToggleSettingDefinition.injectBooleanRead(
    method: MutableMethod,
    index: Int,
    destinationRegister: Int,
): Int {
    method.addInstructions(index, valueReadInstructions("getBoolean", "Z", destinationRegister))
    return 3
}

internal fun MultiChoiceSettingDefinition.injectStringSetRead(
    method: MutableMethod,
    index: Int,
    destinationRegister: Int,
): Int {
    method.addInstructions(
        index,
        valueReadInstructions("getStringSet", "Ljava/util/Set;", destinationRegister),
    )
    return 3
}

private fun SettingItemDefinition.valueReadInstructions(
    methodName: String,
    returnType: String,
    destinationRegister: Int,
): String {
    require(destinationRegister in 0..255) { "Invalid setting destination register: v$destinationRegister" }
    val moveResult = if (returnType.startsWith("L")) "move-result-object" else "move-result"
    return """
        const-string v$destinationRegister, "${smaliString(id)}"
        invoke-static {v$destinationRegister}, $SETTINGS_REGISTRY_DESCRIPTOR->$methodName(Ljava/lang/String;)$returnType
        $moveResult v$destinationRegister
    """.trimIndent()
}

private fun SettingsContributionCatalog.registrationInstructions(): String =
    buildString {
        categories.forEach { category ->
            appendGroupRegistration("registerCategory", null, category)
            category.children.forEach { child -> appendNodeRegistration(category.id, child) }
        }
    }.trim()

private fun StringBuilder.appendNodeRegistration(
    parentId: String,
    node: SettingsNodeDefinition,
) {
    when (node) {
        is SettingsGroupDefinition -> {
            appendGroupRegistration("registerGroup", parentId, node)
            node.children.forEach { child -> appendNodeRegistration(node.id, child) }
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
                    ),
                method = "configureTextInput(Ljava/lang/String;Ljava/lang/String;IZ)V",
            )
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
    }
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
                intValue(group.order),
            )
        } else {
            listOf(
                stringValue(parentId),
                stringValue(group.id),
                stringValue(group.titleResourceName),
                nullableStringValue(group.summaryResourceName),
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
    require(values.size <= 5) { "X-Lite registry call uses too many registers: $method" }
    values.forEachIndexed { index, value -> appendLine(value.instruction(index)) }
    val registers = values.indices.joinToString(", ") { "v$it" }
    appendLine("invoke-static {$registers}, $SETTINGS_REGISTRY_DESCRIPTOR->$method")
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
