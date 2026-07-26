package app.crimera.patches.xlite.settings

import app.morphe.extension.xlite.api.SettingsCategory
import app.morphe.extension.xlite.api.SettingKey

private val STABLE_ID_PATTERN = Regex("xlite\\.[a-z0-9._-]+")
private val OPTION_ID_PATTERN = Regex("[a-zA-Z0-9._-]+")
private val RESOURCE_NAME_PATTERN = Regex("piko_xlite_[a-z0-9_]+")
private val HANDLER_DESCRIPTOR_PATTERN = Regex("L[a-zA-Z0-9_$/]+;")

// ── Settings model definitions ───────────────────────────────────────────

internal sealed interface SettingsNodeDefinition {
    val id: String
    val titleResourceName: String
    val summaryResourceName: String?
    val order: Int
}

internal data class SettingsGroupDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    val children: List<SettingsNodeDefinition>,
) : SettingsNodeDefinition

internal sealed interface SettingItemDefinition : SettingsNodeDefinition {
    val key: SettingKey<*>
    override val id: String
        get() = key.id
}

internal sealed interface ValueSettingDefinition<T> : SettingItemDefinition {
    override val key: SettingKey<T>
    val defaultValue: T
    val rebootApp: Boolean
}

internal data class ToggleSettingDefinition(
    override val key: SettingKey<Boolean>,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: Boolean,
    override val rebootApp: Boolean = false,
) : ValueSettingDefinition<Boolean>

internal enum class InputKind {
    TEXT,
    MULTILINE,
}

internal data class TextInputSettingDefinition(
    override val key: SettingKey<String>,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: String,
    override val rebootApp: Boolean = false,
    val inputKind: InputKind = InputKind.TEXT,
) : ValueSettingDefinition<String>

internal data class ChoiceOption(
    val id: String,
    val titleResourceName: String,
)

internal data class MultiChoiceSettingDefinition(
    override val key: SettingKey<Set<String>>,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: Set<String>,
    override val rebootApp: Boolean = false,
    val options: List<ChoiceOption>,
) : ValueSettingDefinition<Set<String>>

internal data class ActionSettingDefinition(
    override val key: SettingKey<*>,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    val handlerClassDescriptor: String,
) : SettingItemDefinition

internal data class SettingsContributionCatalog(
    val categories: List<SettingsGroupDefinition>,
)

// ── Builder ──────────────────────────────────────────────────────────────

internal class SettingsContributionBuilder {
    private val categoryBuilders = linkedMapOf<SettingsCategory, SettingsGroupBuilder>()

    fun category(
        category: SettingsCategory,
        block: SettingsGroupBuilder.() -> Unit,
    ) {
        categoryBuilders
            .getOrPut(category) {
                SettingsGroupBuilder(
                    id = category.id,
                    titleResourceName = category.titleResourceName,
                    summaryResourceName = category.summaryResourceName,
                    order = category.order,
                )
            }.apply(block)
    }

    fun build(): SettingsContributionCatalog {
        val catalog =
            SettingsContributionCatalog(
                categories =
                    categoryBuilders
                        .values
                        .map(SettingsGroupBuilder::build)
                        .sortedWith(nodeComparator),
            )
        validateSettingsContribution(catalog)
        return catalog
    }
}

internal class SettingsGroupBuilder(
    private val id: String,
    private val titleResourceName: String,
    private val summaryResourceName: String?,
    private val order: Int,
) {
    private val children = mutableListOf<SettingsNodeDefinition>()

    fun add(definition: SettingItemDefinition) {
        children += definition
    }

    fun group(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        block: SettingsGroupBuilder.() -> Unit,
    ) {
        children +=
            SettingsGroupBuilder(id, titleResourceName, summaryResourceName, order)
                .apply(block)
                .build()
    }

    fun toggle(
        key: SettingKey<Boolean>,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: Boolean,
        rebootApp: Boolean = false,
    ) = add(ToggleSettingDefinition(key, titleResourceName, summaryResourceName, order, defaultValue, rebootApp))

    fun input(
        key: SettingKey<String>,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: String,
        rebootApp: Boolean = false,
        inputKind: InputKind = InputKind.TEXT,
    ) = add(
        TextInputSettingDefinition(
            key,
            titleResourceName,
            summaryResourceName,
            order,
            defaultValue,
            rebootApp,
            inputKind,
        ),
    )

    fun multiChoice(
        key: SettingKey<Set<String>>,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: Set<String>,
        rebootApp: Boolean = false,
        options: List<ChoiceOption>,
    ) = add(
        MultiChoiceSettingDefinition(
            key,
            titleResourceName,
            summaryResourceName,
            order,
            defaultValue,
            rebootApp,
            options,
        ),
    )

    fun action(
        key: SettingKey<*>,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        handlerClassDescriptor: String,
    ) = add(
        ActionSettingDefinition(
            key,
            titleResourceName,
            summaryResourceName,
            order,
            handlerClassDescriptor,
        ),
    )

    internal fun build() =
        SettingsGroupDefinition(
            id = id,
            titleResourceName = titleResourceName,
            summaryResourceName = summaryResourceName,
            order = order,
            children = children.sortedWith(nodeComparator),
        )
}

// ── Top-level factory functions ──────────────────────────────────────────

internal fun toggleSetting(
    key: SettingKey<Boolean>,
    titleResourceName: String,
    summaryResourceName: String? = null,
    order: Int = 0,
    defaultValue: Boolean,
    rebootApp: Boolean = false,
) = ToggleSettingDefinition(key, titleResourceName, summaryResourceName, order, defaultValue, rebootApp)

private val nodeComparator =
    compareBy<SettingsNodeDefinition>(SettingsNodeDefinition::order, SettingsNodeDefinition::id)

// ── Validation ───────────────────────────────────────────────────────────

internal fun validateSettingsContribution(catalog: SettingsContributionCatalog) {
    require(catalog.categories.isNotEmpty()) { "An X-Lite settings contribution cannot be empty" }

    val groupIds = mutableSetOf<String>()
    val settingIds = mutableSetOf<String>()

    fun validateNode(node: SettingsNodeDefinition) {
        validateCommonMetadata(node)
        when (node) {
            is SettingsGroupDefinition -> {
                require(groupIds.add(node.id)) { "Duplicate X-Lite settings group ID: ${node.id}" }
                require(node.children.isNotEmpty()) { "X-Lite settings group is empty: ${node.id}" }
                node.children.forEach(::validateNode)
            }

            is SettingItemDefinition -> {
                require(settingIds.add(node.id)) { "Duplicate X-Lite setting ID: ${node.id}" }
                validateSetting(node)
            }
        }
    }

    catalog.categories.forEach(::validateNode)
}

private fun validateCommonMetadata(node: SettingsNodeDefinition) {
    require(STABLE_ID_PATTERN.matches(node.id)) { "Invalid X-Lite settings ID: ${node.id}" }
    require(RESOURCE_NAME_PATTERN.matches(node.titleResourceName)) {
        "Invalid X-Lite title resource: ${node.titleResourceName}"
    }
    node.summaryResourceName?.let { summary ->
        require(RESOURCE_NAME_PATTERN.matches(summary)) {
            "Invalid X-Lite summary resource: $summary"
        }
    }
    require(node.order >= 0) { "X-Lite settings order cannot be negative: ${node.id}" }
}

private fun validateSetting(setting: SettingItemDefinition) {
    when (setting) {
        is MultiChoiceSettingDefinition -> {
            require(setting.options.isNotEmpty()) { "Multi-choice setting has no options: ${setting.id}" }
            val optionIds = mutableSetOf<String>()
            setting.options.forEach { option ->
                require(OPTION_ID_PATTERN.matches(option.id)) {
                    "Invalid choice option ID for ${setting.id}: ${option.id}"
                }
                require(optionIds.add(option.id)) {
                    "Duplicate choice option ID for ${setting.id}: ${option.id}"
                }
                require(RESOURCE_NAME_PATTERN.matches(option.titleResourceName)) {
                    "Invalid choice title resource for ${setting.id}: ${option.titleResourceName}"
                }
            }
            require(optionIds.containsAll(setting.defaultValue)) {
                "Unknown default choice for ${setting.id}: ${setting.defaultValue - optionIds}"
            }
        }

        is ActionSettingDefinition ->
            require(HANDLER_DESCRIPTOR_PATTERN.matches(setting.handlerClassDescriptor)) {
                "Invalid action handler descriptor for ${setting.id}: ${setting.handlerClassDescriptor}"
            }

        is TextInputSettingDefinition,
        is ToggleSettingDefinition,
        -> Unit
    }
}
