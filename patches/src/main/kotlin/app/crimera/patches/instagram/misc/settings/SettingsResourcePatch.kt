/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val SETTINGS_SHORTCUT_ID = "piko_settings"
private const val SETTINGS_SHORTCUT_ACTIVITY =
    "app.morphe.extension.instagram.settings.SettingsShortcutActivity"
private const val SETTINGS_SHORTCUT_ACTION =
    "app.morphe.extension.instagram.action.OPEN_PIKO_SETTINGS"

private fun appendPikoSettingsShortcut(document: Document, targetPackage: String) {
    require(targetPackage.isNotBlank()) { "Final Instagram package name is empty" }
    val existing = document.getElementsByTagName("shortcut")
    for (index in 0 until existing.length) {
        val shortcut = existing.item(index) as? Element ?: continue
        if (shortcut.getAttribute("android:shortcutId") == SETTINGS_SHORTCUT_ID) return
    }

    val intent = document.createElement("intent").apply {
        setAttribute("android:action", SETTINGS_SHORTCUT_ACTION)
        setAttribute("android:targetClass", SETTINGS_SHORTCUT_ACTIVITY)
        setAttribute("android:targetPackage", targetPackage)
    }
    val shortcut = document.createElement("shortcut").apply {
        setAttribute("android:enabled", "true")
        setAttribute("android:icon", "@drawable/piko_settings_shortcut_icon")
        setAttribute("android:shortcutDisabledMessage", "@string/piko_title_settings")
        setAttribute("android:shortcutId", SETTINGS_SHORTCUT_ID)
        setAttribute("android:shortcutLongLabel", "@string/piko_title_settings")
        setAttribute("android:shortcutShortLabel", "@string/piko_title_settings")
        appendChild(intent)
    }
    document.documentElement.insertBefore(shortcut, document.documentElement.firstChild)
}

private fun appendSettingsShortcutActivity(document: Document) {
    val activities = document.getElementsByTagName("activity")
    for (index in 0 until activities.length) {
        val activity = activities.item(index) as? Element ?: continue
        if (activity.getAttribute("android:name") == SETTINGS_SHORTCUT_ACTIVITY) return
    }

    val application = document.getElementsByTagName("application").item(0) as Element
    val activity = document.createElement("activity").apply {
        setAttribute("android:name", SETTINGS_SHORTCUT_ACTIVITY)
        setAttribute("android:excludeFromRecents", "true")
        setAttribute("android:exported", "true")
        setAttribute("android:noHistory", "true")
        setAttribute("android:theme", "@android:style/Theme.NoDisplay")
    }
    application.appendChild(activity)
}

val addSettingsActivityPatch =
    resourcePatch(
        description = "Adds SettingsActivity to the Android manifest.",
    ) {
        execute {
            copyResources(
                "instagram/settings",
                ResourceGroup(
                    "drawable",
                    "piko_settings_shortcut_icon.xml",
                ),
            )
        }

        finalize {
            var targetPackage = ""
            document("AndroidManifest.xml").use { document ->
                targetPackage = document.documentElement.getAttribute("package")

                val application = document.getElementsByTagName("application").item(0) as Element

                var activity = document.createElement("activity")
                activity.setAttribute("android:name", "app.morphe.extension.instagram.settings.SettingsActivity")
                activity.setAttribute("android:label", "Settings")
                activity.setAttribute("android:theme", "@android:style/Theme.DeviceDefault.NoActionBar")
                activity.setAttribute("android:exported", "false")
                application.appendChild(activity)

                val service = document.createElement("service")
                service.setAttribute(
                    "android:name",
                    "app.morphe.extension.instagram.settings.SettingsTaskService",
                )
                service.setAttribute("android:exported", "false")
                service.setAttribute("android:stopWithTask", "false")
                application.appendChild(service)

                listOf(
                    "app.morphe.extension.instagram.settings.preference.fragments.BackupPrefActivity",
                    "app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity",
                    "app.morphe.extension.crimera.downloader.FolderPickerActivity",
                ).forEach { activityName ->
                    activity = document.createElement("activity")
                    activity.setAttribute("android:name", activityName)
                    activity.setAttribute("android:exported", "false")
                    application.appendChild(activity)
                }

                appendSettingsShortcutActivity(document)
            }

            document("res/xml/shortcuts.xml").use { document ->
                appendPikoSettingsShortcut(document, targetPackage)
            }
        }
    }
