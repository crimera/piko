package app.crimera.patches.xlite.settings

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
    order: Int = 0,
    block: SettingsGroupBuilder.() -> T,
): T =
    group(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
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
): TextInputSettingDefinition =
    input(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        defaultValue = defaultValue,
        rebootApp = rebootApp,
        inputKind = inputKind,
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
): CustomScreenSettingDefinition =
    customScreen(
        id = id,
        titleResourceName = strings.titleResourceName,
        summaryResourceName = strings.summaryResourceName,
        order = order,
        fragmentClassDescriptor = fragmentClassDescriptor,
    )

internal fun <T> BytecodePatchBuilder.xLiteSettings(
    block: SettingsContributionBuilder.() -> T,
): T {
    val builder = SettingsContributionBuilder()
    val result = builder.block()
    val catalog = builder.build()
    SettingsContributionIndex.register(catalog)
    dependsOn(xLiteSettingsContributionPatch(catalog))
    return result
}

internal fun BytecodePatchBuilder.xLiteToggle(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Boolean,
    rebootApp: Boolean = false,
): ToggleSettingDefinition =
    xLiteSettings {
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

internal fun BytecodePatchBuilder.xLiteTextInput(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    inputKind: InputKind = InputKind.TEXT,
): TextInputSettingDefinition =
    xLiteSettings {
        category(category) {
            input(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                defaultValue = defaultValue,
                rebootApp = rebootApp,
                inputKind = inputKind,
            )
        }
    }

internal fun BytecodePatchBuilder.xLiteMultiChoice(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Set<String>,
    rebootApp: Boolean = false,
    options: List<ChoiceOption>,
): MultiChoiceSettingDefinition =
    xLiteSettings {
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

internal fun BytecodePatchBuilder.xLiteCustomScreen(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    fragmentClassDescriptor: String,
): CustomScreenSettingDefinition =
    xLiteSettings {
        category(category) {
            customScreen(
                id = id,
                titleResourceName = strings.titleResourceName,
                summaryResourceName = strings.summaryResourceName,
                order = order,
                fragmentClassDescriptor = fragmentClassDescriptor,
            )
        }
    }

internal fun BytecodePatchBuilder.xLiteAction(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    handlerClassDescriptor: String,
): ActionSettingDefinition =
    xLiteSettings {
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
