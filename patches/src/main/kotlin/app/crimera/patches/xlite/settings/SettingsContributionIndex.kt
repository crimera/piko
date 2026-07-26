package app.crimera.patches.xlite.settings

import java.util.Collections

internal object SettingsContributionIndex {
    private val lock = Any()
    private val catalogs = mutableListOf<SettingsContributionCatalog>()

    fun register(catalog: SettingsContributionCatalog) {
        val snapshot = catalog.immutableSnapshot()
        synchronized(lock) {
            catalogs += snapshot
        }
    }

    fun snapshot(): List<SettingsContributionCatalog> =
        synchronized(lock) {
            immutableList(catalogs)
        }

    fun resetForTests() {
        synchronized(lock) {
            catalogs.clear()
        }
    }
}

private fun SettingsContributionCatalog.immutableSnapshot() =
    SettingsContributionCatalog(
        categories = immutableList(categories.map(SettingsGroupDefinition::immutableSnapshot)),
    )

private fun SettingsGroupDefinition.immutableSnapshot(): SettingsGroupDefinition =
    copy(children = immutableList(children.map(SettingsNodeDefinition::immutableSnapshot)))

private fun SettingsNodeDefinition.immutableSnapshot(): SettingsNodeDefinition =
    when (this) {
        is SettingsGroupDefinition -> immutableSnapshot()
        is MultiChoiceSettingDefinition ->
            copy(
                defaultValue = immutableSet(defaultValue),
                options = immutableList(options),
            )
        is ActionSettingDefinition,
        is TextInputSettingDefinition,
        is ToggleSettingDefinition,
        -> this
    }

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private fun <T> immutableSet(values: Collection<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
