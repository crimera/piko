package app.crimera.patches.newx.settings

import app.crimera.patches.settings.ChoiceOption
import app.crimera.patches.settings.CustomScreenSettingDefinition
import app.crimera.patches.settings.ActionSettingDefinition
import app.crimera.patches.settings.InputKind
import app.crimera.patches.settings.MultiChoiceSettingDefinition
import app.crimera.patches.settings.SettingStrings
import app.crimera.patches.settings.SettingsCategory
import app.crimera.patches.settings.SettingsContributionBuilder
import app.crimera.patches.settings.SettingsPatchConfig
import app.crimera.patches.settings.SingleChoiceSettingDefinition
import app.crimera.patches.settings.TextInputSettingDefinition
import app.crimera.patches.settings.ToggleSettingDefinition
import app.crimera.patches.settings.contributeSettings
import app.crimera.patches.settings.settingsAction
import app.crimera.patches.settings.settingsCustomScreen
import app.crimera.patches.settings.settingsMultiChoice
import app.crimera.patches.settings.settingsSingleChoice
import app.crimera.patches.settings.settingsTextInput
import app.crimera.patches.settings.settingsToggle
import app.morphe.patcher.patch.BytecodePatchBuilder

/**
 * NewX's binding of the shared settings DSL (`piko-patches-library`): its base patch, ID and string
 * naming rules, and error label. The `newX*` functions keep the call sites of every NewX patch
 * unchanged.
 */
internal val NEWX_SETTINGS_CONFIG: SettingsPatchConfig by lazy {
    SettingsPatchConfig(
        basePatch = newXSettingsPatch,
        idPattern = Regex("newx\\.[a-z0-9._-]+"),
        resourceNamePattern = Regex("piko_newx_[a-z0-9_]+"),
        label = "NewX",
    )
}

internal fun <T> BytecodePatchBuilder.newXSettings(
    block: SettingsContributionBuilder.() -> T,
): T = contributeSettings(NEWX_SETTINGS_CONFIG, block)

internal fun BytecodePatchBuilder.newXToggle(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Boolean,
    rebootApp: Boolean = false,
    visible: Boolean = true,
): ToggleSettingDefinition =
    settingsToggle(NEWX_SETTINGS_CONFIG, id, category, strings, order, defaultValue, rebootApp, visible)

internal fun BytecodePatchBuilder.newXTextInput(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    inputKind: InputKind = InputKind.TEXT,
    validatorClassDescriptor: String? = null,
): TextInputSettingDefinition =
    settingsTextInput(
        NEWX_SETTINGS_CONFIG,
        id,
        category,
        strings,
        order,
        defaultValue,
        rebootApp,
        visible,
        inputKind,
        validatorClassDescriptor,
    )

internal fun BytecodePatchBuilder.newXSingleChoice(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: String,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    options: List<ChoiceOption>,
): SingleChoiceSettingDefinition =
    settingsSingleChoice(NEWX_SETTINGS_CONFIG, id, category, strings, order, defaultValue, rebootApp, visible, options)

internal fun BytecodePatchBuilder.newXMultiChoice(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    defaultValue: Set<String>,
    rebootApp: Boolean = false,
    visible: Boolean = true,
    options: List<ChoiceOption>,
): MultiChoiceSettingDefinition =
    settingsMultiChoice(NEWX_SETTINGS_CONFIG, id, category, strings, order, defaultValue, rebootApp, visible, options)

internal fun BytecodePatchBuilder.newXCustomScreen(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    fragmentClassDescriptor: String,
    iconResourceName: String? = null,
): CustomScreenSettingDefinition =
    settingsCustomScreen(NEWX_SETTINGS_CONFIG, id, category, strings, order, fragmentClassDescriptor, iconResourceName)

internal fun BytecodePatchBuilder.newXAction(
    id: String,
    category: SettingsCategory,
    strings: SettingStrings,
    order: Int = 0,
    handlerClassDescriptor: String,
    visible: Boolean = true,
): ActionSettingDefinition =
    settingsAction(NEWX_SETTINGS_CONFIG, id, category, strings, order, handlerClassDescriptor, visible)
