package app.revanced.patches.shared.misc.settings.preference

import app.revanced.patches.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import java.io.Closeable

abstract class BasePreferenceScreen(
    private val root: MutableSet<Screen> = mutableSetOf(),
) : Closeable {

    override fun close() {
        if (root.isEmpty()) return

        root.forEach { preference ->
            commit(preference.transform())
        }
    }

    /**
     * Finalize and insert root preference into resource patch
     */
    abstract fun commit(screen: PreferenceScreenPreference)

    open inner class Screen(
        key: String? = null,
        titleKey: String = "${key}_title",
        private val summaryKey: String? = "${key}_summary",
        icon: String? = null,
        layout: String? = null,
        preferences: MutableSet<BasePreference> = mutableSetOf(),
        val categories: MutableSet<Category> = mutableSetOf(),
        private val sorting: Sorting = Sorting.BY_TITLE,
    ) : BasePreferenceCollection(key, titleKey, icon, layout, preferences) {

        override fun transform(): PreferenceScreenPreference {
            return PreferenceScreenPreference(
                key,
                titleKey,
                summaryKey,
                icon,
                layout,
                sorting,
                // Screens and preferences are sorted at runtime by extension code,
                // so title sorting uses the localized language in use.
                preferences = preferences + categories.map { it.transform() },
            )
        }

        private fun ensureScreenInserted() {
            // Add to screens if not yet done
            if (!root.contains(this)) {
                root.add(this)
            }
        }

        fun addPreferences(vararg preferences: BasePreference) {
            ensureScreenInserted()
            this.preferences.addAll(preferences)
        }

        open inner class Category(
            key: String? = null,
            titleKey: String = "${key}_title",
            icon: String? = null,
            layout: String? = null,
            preferences: MutableSet<BasePreference> = mutableSetOf(),
        ) : BasePreferenceCollection(key, titleKey, icon, layout, preferences) {
            override fun transform(): PreferenceCategory {
                return PreferenceCategory(
                    key,
                    titleKey,
                    icon,
                    layout,
                    sorting,
                    preferences = preferences,
                )
            }

            fun addPreferences(vararg preferences: BasePreference) {
                ensureScreenInserted()

                // Add to the categories if not done yet.
                if (!categories.contains(this)) {
                    categories.add(this)
                }

                this.preferences.addAll(preferences)
            }
        }
    }

    abstract class BasePreferenceCollection(
        val key: String? = null,
        val titleKey: String = "${key}_title",
        val icon: String? = null,
        val layout: String? = null,
        val preferences: MutableSet<BasePreference> = mutableSetOf(),
    ) {
        abstract fun transform(): BasePreference
    }
}
