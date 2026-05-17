/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.getNode
import org.w3c.dom.Element

val addSettingsActivityPatch =
    resourcePatch(
        description = "Adds SettingsActivity to the Android manifest.",
    ) {
        finalize {
            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as Element

                var activity = document.createElement("activity")
                activity.setAttribute("android:name", "app.morphe.extension.instagram.settings.SettingsActivity")
                activity.setAttribute("android:label", "Settings")
                activity.setAttribute("android:theme", "@android:style/Theme.DeviceDefault.NoActionBar")
                activity.setAttribute("android:exported", "false")
                application.appendChild(activity)

                activity = document.createElement("activity")
                activity.setAttribute("android:name", "app.morphe.extension.instagram.settings.preference.fragments.BackupPrefActivity")
                activity.setAttribute("android:exported", "false")
                application.appendChild(activity)

                activity = document.createElement("activity")
                activity.setAttribute("android:name", "app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity")
                activity.setAttribute("android:exported", "false")
                application.appendChild(activity)
            }
        }
    }
