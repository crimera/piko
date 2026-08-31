/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.instants

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/** Registers the "Saved Instants" viewer Activity for the Save Instants feature. Kept out of the
 *  shared settings resource patch so it only ships when the feature is applied. Uses a plain
 *  platform theme — the app's Theme.Instagram.Splash breaks plain widget construction. */
val instantsDownloadResourcePatch =
    resourcePatch {
        finalize {
            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as Element

                val activity = document.createElement("activity")
                activity.setAttribute("android:name", "app.morphe.extension.instagram.patches.instants.InstantsVaultActivity")
                activity.setAttribute("android:theme", "@android:style/Theme.DeviceDefault.NoActionBar")
                activity.setAttribute("android:exported", "false")
                application.appendChild(activity)
            }
        }
    }
