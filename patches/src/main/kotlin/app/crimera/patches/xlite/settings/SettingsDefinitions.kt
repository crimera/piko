package app.crimera.patches.xlite.settings

private val STABLE_ID_PATTERN = Regex("xlite\\.[a-z0-9._-]+")
private val OPTION_ID_PATTERN = Regex("[a-zA-Z0-9._-]+")
private val RESOURCE_NAME_PATTERN = Regex("piko_xlite_[a-z0-9_]+")
private val DRAWABLE_RESOURCE_NAME_PATTERN = Regex("[a-z][a-z0-9_]+")
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
    val iconResourceName: String?,
    override val order: Int,
    val children: List<SettingsNodeDefinition>,
) : SettingsNodeDefinition

internal sealed interface SettingItemDefinition : SettingsNodeDefinition

internal sealed interface ValueSettingDefinition<T> : SettingItemDefinition {
    val defaultValue: T
    val rebootApp: Boolean
}

internal data class ToggleSettingDefinition(
    override val id: String,
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
    override val id: String,
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

internal data class SingleChoiceSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: String,
    override val rebootApp: Boolean = false,
    val options: List<ChoiceOption>,
) : ValueSettingDefinition<String>

internal data class MultiChoiceSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    override val defaultValue: Set<String>,
    override val rebootApp: Boolean = false,
    val options: List<ChoiceOption>,
) : ValueSettingDefinition<Set<String>>

internal data class ActionSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    val handlerClassDescriptor: String,
) : SettingItemDefinition

internal data class CustomScreenSettingDefinition(
    override val id: String,
    override val titleResourceName: String,
    override val summaryResourceName: String?,
    override val order: Int,
    val fragmentClassDescriptor: String,
) : SettingItemDefinition

internal data class SettingsContributionCatalog(
    val categories: List<SettingsGroupDefinition>,
)

// ── Builder ──────────────────────────────────────────────────────────────

internal class SettingsContributionBuilder {
    private val categoryBuilders = linkedMapOf<SettingsCategory, SettingsGroupBuilder>()

    fun <T> category(
        category: SettingsCategory,
        block: SettingsGroupBuilder.() -> T,
    ): T {
        val builder =
            categoryBuilders.getOrPut(category) {
                SettingsGroupBuilder(
                    id = category.id,
                    titleResourceName = category.titleResourceName,
                    summaryResourceName = category.summaryResourceName,
                    iconResourceName = category.iconResourceName,
                    order = category.order,
                )
            }
        return builder.block()
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
    private val iconResourceName: String?,
    private val order: Int,
) {
    private val children = mutableListOf<SettingsNodeDefinition>()

    fun <T : SettingItemDefinition> add(definition: T): T {
        children += definition
        return definition
    }

    fun <T> group(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        iconResourceName: String? = null,
        order: Int = 0,
        block: SettingsGroupBuilder.() -> T,
    ): T {
        val builder =
            SettingsGroupBuilder(
                id,
                titleResourceName,
                summaryResourceName,
                iconResourceName,
                order,
            )
        val result = builder.block()
        children += builder.build()
        return result
    }

    fun toggle(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: Boolean,
        rebootApp: Boolean = false,
    ): ToggleSettingDefinition =
        add(
            ToggleSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                defaultValue,
                rebootApp,
            ),
        )

    fun input(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: String,
        rebootApp: Boolean = false,
        inputKind: InputKind = InputKind.TEXT,
    ): TextInputSettingDefinition =
        add(
            TextInputSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                defaultValue,
                rebootApp,
                inputKind,
            ),
        )

    fun singleChoice(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: String,
        rebootApp: Boolean = false,
        options: List<ChoiceOption>,
    ): SingleChoiceSettingDefinition =
        add(
            SingleChoiceSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                defaultValue,
                rebootApp,
                options,
            ),
        )

    fun multiChoice(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        defaultValue: Set<String>,
        rebootApp: Boolean = false,
        options: List<ChoiceOption>,
    ): MultiChoiceSettingDefinition =
        add(
            MultiChoiceSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                defaultValue,
                rebootApp,
                options,
            ),
        )

    fun action(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        handlerClassDescriptor: String,
    ): ActionSettingDefinition =
        add(
            ActionSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                handlerClassDescriptor,
            ),
        )

    fun customScreen(
        id: String,
        titleResourceName: String,
        summaryResourceName: String? = null,
        order: Int = 0,
        fragmentClassDescriptor: String,
    ): CustomScreenSettingDefinition =
        add(
            CustomScreenSettingDefinition(
                id,
                titleResourceName,
                summaryResourceName,
                order,
                fragmentClassDescriptor,
            ),
        )

    internal fun build() =
        SettingsGroupDefinition(
            id = id,
            titleResourceName = titleResourceName,
            summaryResourceName = summaryResourceName,
            iconResourceName = iconResourceName,
            order = order,
            children = children.sortedWith(nodeComparator),
        )
}

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
                node.iconResourceName?.let { iconResourceName ->
                    require(DRAWABLE_RESOURCE_NAME_PATTERN.matches(iconResourceName)) {
                        "Invalid X-Lite group icon resource: $iconResourceName"
                    }
                }
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
        is SingleChoiceSettingDefinition -> {
            require(setting.options.isNotEmpty()) { "Single-choice setting has no options: ${setting.id}" }
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
            require(setting.defaultValue in optionIds) {
                "Unknown default choice for ${setting.id}: ${setting.defaultValue}"
            }
        }

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

        is CustomScreenSettingDefinition ->
            require(HANDLER_DESCRIPTOR_PATTERN.matches(setting.fragmentClassDescriptor)) {
                "Invalid custom screen fragment descriptor for ${setting.id}: ${setting.fragmentClassDescriptor}"
            }

        is TextInputSettingDefinition,
        is ToggleSettingDefinition,
        -> Unit
    }
}
