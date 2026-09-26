/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.history

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

val historyResourcePatch =
    resourcePatch(
        description = "Adds the view-history viewer activity to the Android manifest.",
    ) {
        finalize {
            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as Element

                val activity = document.createElement("activity")
                activity.setAttribute("android:name", "app.morphe.extension.instagram.patches.history.HistoryActivity")
                activity.setAttribute("android:theme", "@android:style/Theme.DeviceDefault.NoActionBar")
                activity.setAttribute("android:exported", "false")
                application.appendChild(activity)
            }
        }
    }
