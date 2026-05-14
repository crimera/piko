/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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
