package app.revanced.patches.shared.misc.settings

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patches.all.misc.resources.addResource
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.shared.misc.settings.preference.BasePreference
import app.revanced.patches.shared.misc.settings.preference.IntentPreference
import app.revanced.patches.shared.misc.settings.preference.PreferenceCategory
import app.revanced.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources
import app.revanced.util.getNode
import app.revanced.util.insertFirst
import app.revanced.util.returnEarly
import org.w3c.dom.Node

// TODO: Delete this on next major version bump.
@Deprecated("Use non deprecated settings patch function")
fun settingsPatch(
    rootPreference: Pair<IntentPreference, String>,
    preferences: Set<BasePreference>,
) = settingsPatch(listOf(rootPreference), preferences)

private var themeForegroundColor: String? = null
private var themeBackgroundColor: String? = null

/**
 * Sets the default theme colors used in various ReVanced specific settings menus.
 * By default these colors are white and black, but instead can be set to the
 * same color the target app uses for it's own settings.
 */
fun overrideThemeColors(
    foregroundColor: String,
    backgroundColor: String,
) {
    themeForegroundColor = foregroundColor
    themeBackgroundColor = backgroundColor
}

private val settingsColorPatch =
    bytecodePatch {
        finalize {
            if (themeForegroundColor != null) {
                themeLightColorResourceNameFingerprint.method.returnEarly(themeForegroundColor!!)
            }
            if (themeBackgroundColor != null) {
                themeDarkColorResourceNameFingerprint.method.returnEarly(themeBackgroundColor!!)
            }
        }
    }

/**
 * A resource patch that adds settings to a settings fragment.
 *
 * @param rootPreferences List of intent preferences and the name of the fragment file to add it to.
 *                        File names that do not exist are ignored and not processed.
 * @param preferences A set of preferences to add to the ReVanced fragment.
 */
fun settingsPatch(
    rootPreferences: List<Pair<BasePreference, String>>? = null,
    preferences: Set<BasePreference>,
) = resourcePatch {
    dependsOn(addResourcesPatch, settingsColorPatch)

    execute {
        copyResources(
            "settings",
            ResourceGroup("xml", "revanced_prefs.xml", "revanced_prefs_icons.xml"),
            ResourceGroup(
                "drawable",
                // CustomListPreference resources.
                "revanced_ic_dialog_alert.xml",
                "revanced_settings_arrow_time.xml",
                "revanced_settings_circle_background.xml",
                "revanced_settings_cursor.xml",
                "revanced_settings_custom_checkmark.xml",
                "revanced_settings_search_icon.xml",
                "revanced_settings_toolbar_arrow_left.xml",
            ),
            ResourceGroup(
                "layout",
                "revanced_custom_list_item_checked.xml",
                // Color picker.
                "revanced_color_dot_widget.xml",
                "revanced_color_picker.xml",
            ),
        )

        addResources("shared", "misc.settings.settingsResourcePatch")
    }

    finalize {
        fun Node.addPreference(preference: BasePreference) {
            preference
                .serialize(ownerDocument) { resource ->
                    // TODO: Currently, resources can only be added to "values", which may not be the correct place.
                    //  It may be necessary to ask for the desired resourceValue in the future.
                    addResource("values", resource)
                }.let { preferenceNode ->
                    insertFirst(preferenceNode)
                }
        }

        // Add the root preference to an existing fragment if needed.
        rootPreferences?.let {
            var modified = false

            it.forEach { (intent, fileName) ->
                val preferenceFileName = "res/xml/$fileName.xml"
                if (get(preferenceFileName).exists()) {
                    document(preferenceFileName).use { document ->
                        document.getNode("PreferenceScreen").addPreference(intent)
                    }
                    modified = true
                }
            }

            if (!modified) throw PatchException("No declared preference files exists: $rootPreferences")
        }

        // Add all preferences to the ReVanced fragment.
        document("res/xml/revanced_prefs_icons.xml").use { document ->
            val revancedPreferenceScreenNode = document.getNode("PreferenceScreen")
            preferences.forEach { revancedPreferenceScreenNode.addPreference(it) }
        }

        // Because the icon preferences require declaring a layout resource,
        // there is no easy way to change to the Android default preference layout
        // after the preference is inflated.
        // Using two different preference files is the simplest and most robust solution.
        fun removeIconsAndLayout(preferences: Collection<BasePreference>) {
            preferences.forEach { preference ->
                preference.icon = null
                preference.layout = null

                if (preference is PreferenceCategory) {
                    removeIconsAndLayout(preference.preferences)
                }
                if (preference is PreferenceScreenPreference) {
                    removeIconsAndLayout(preference.preferences)
                }
            }
        }
        removeIconsAndLayout(preferences)

        document("res/xml/revanced_prefs.xml").use { document ->
            val revancedPreferenceScreenNode = document.getNode("PreferenceScreen")
            preferences.forEach { revancedPreferenceScreenNode.addPreference(it) }
        }
    }
}
