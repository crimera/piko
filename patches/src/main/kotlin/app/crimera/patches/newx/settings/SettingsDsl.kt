package app.crimera.patches.newx.settings

import app.morphe.patcher.patch.BytecodePatchBuilder

internal data class SettingStrings(
    val titleResourceName: String,
    val summaryResourceName: String?,
)

internal fun settingStrings(
    baseName: String,
    summary: Boolean = true,
) =
    SettingStrings(
        titleResourceName = "${baseName}_title",
        summaryResourceName = if (summary) "${baseName}_summary" else null,
    )

internal fun settingStrings(
    titleResourceName: String,
    summaryResourceName: String?,
) = SettingStrings(titleResourceName, summaryResourceName)

internal fun choice(
    id: String,
    titleResourceName: String,
) = ChoiceOption(id, titleResourceName)

internal fun <T> SettingsGroupBuilder.group(
    id: String,
    strings: SettingStrings,
    iconResourceName: String? = null,
    order: Int = 0,
    block: SettingsGroupBuilder.() -> T,
): T =
    group(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        iconResourceName = iconResourceName,
        order = order,
        block = block,
    )

internal fun <T> SettingsGroupBuilder.group(
    group: SettingsGroupMetadata,
    block: SettingsGroupBuilder.() -> T,
): T =
    group(
        id = group.id,
        titleResourceName = group.titleResourceName,
        summaryResourceName = group.summaryResourceName,
        iconResourceName = group.iconResourceName,
        order = group.order,
        block = block,
    )

internal fun SettingsGroupBuilder.toggle(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Boolean,
    rebootApp: Boolean = false,
): ToggleSettingDefinition =
    toggle(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
    )

internal fun SettingsGroupBuilder.input(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    inputKind: InputKind = InputKind.TEXT,
    validatorClassDescriptor: String? = null,
): TextInputSettingDefinition =
    input(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
        inputKind = inputKind,
        validatorClassDescriptor = validatorClassDescriptor,
    )

internal fun SettingsGroupBuilder.singleChoice(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    options: List<ChoiceOption>,
): SingleChoiceSettingDefinition =
    singleChoice(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
        options = options,
    )

internal fun SettingsGroupBuilder.multiChoice(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Set<String>,
    rebootApp: Boolean = false,
    options: List<ChoiceOption>,
): MultiChoiceSettingDefinition =
    multiChoice(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
        options = options,
    )

internal fun SettingsGroupBuilder.action(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    handlerClassDescriptor: String,
): ActionSettingDefinition =
    action(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        handlerClassDescriptor = handlerClassDescriptor,
    )

internal fun SettingsGroupBuilder.customScreen(
    id: String,
    strings: SettingStrings,
    order: Int = 0,
    fragmentClassDescriptor: String,
    iconResourceName: String? = null,
): CustomScreenSettingDefinition =
    customScreen(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        fragmentClassDescriptor = fragmentClassDescriptor,
        iconResourceName = iconResourceName,
    )

internal fun <T> BytecodePatchBuilder.newXSettings(
    block: SettingsContributionBuilder.() -> T,
): T {
    val builder = SettingsContributionBuilder()
    val result = builder.block()
    val catalog = builder.build()
    SettingsContributionIndex.register(catalog)
    dependsOn(newXSettingsContributionPatch(catalog))
    return result
}

internal fun BytecodePatchBuilder.newXToggle(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Boolean,
    rebootApp: Boolean = false,
): ToggleSettingDefinition =
    newXSettings {
        category(category) {
            toggle(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
            )
        }
    }

internal fun BytecodePatchBuilder.newXTextInput(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    inputKind: InputKind = InputKind.TEXT,
    validatorClassDescriptor: String? = null,
): TextInputSettingDefinition =
    newXSettings {
        category(category) {
            input(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
                inputKind = inputKind,
                validatorClassDescriptor = validatorClassDescriptor,
            )
        }
    }

internal fun BytecodePatchBuilder.newXSingleChoice(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    options: List<ChoiceOption>,
): SingleChoiceSettingDefinition =
    newXSettings {
        category(category) {
            singleChoice(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
                options = options,
            )
        }
    }

internal fun BytecodePatchBuilder.newXMultiChoice(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Set<String>,
    rebootApp: Boolean = false,
    options: List<ChoiceOption>,
): MultiChoiceSettingDefinition =
    newXSettings {
        category(category) {
            multiChoice(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
                options = options,
            )
        }
    }

internal fun BytecodePatchBuilder.newXCustomScreen(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    fragmentClassDescriptor: String,
    iconResourceName: String? = null,
): CustomScreenSettingDefinition =
    newXSettings {
        category(category) {
            customScreen(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                fragmentClassDescriptor = fragmentClassDescriptor,
                iconResourceName = iconResourceName,
            )
        }
    }

internal fun BytecodePatchBuilder.newXAction(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    handlerClassDescriptor: String,
): ActionSettingDefinition =
    newXSettings {
        category(category) {
            action(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                handlerClassDescriptor = handlerClassDescriptor,
            )
        }
    }
