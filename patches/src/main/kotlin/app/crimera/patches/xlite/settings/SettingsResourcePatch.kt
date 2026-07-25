package app.crimera.patches.xlite.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import org.w3c.dom.Element

internal val xLiteSettingsResourcePatch =
    resourcePatch {
        dependsOn(resourceMappingPatch)

        execute {
            document("AndroidManifest.xml").use { document ->
                val application =
                    document.getElementsByTagName("application").item(0) as? Element
                        ?: error("X-Lite settings could not find the manifest application element")
                val activity =
                    document.createElement("activity").apply {
                        setAttribute(
                            "android:name",
                            "app.morphe.extension.xlite.settings.XLiteSettingsActivity",
                        )
                        setAttribute("android:excludeFromRecents", "true")
                        setAttribute("android:exported", "false")
                        appendChild(
                            document.createElement("meta-data").apply {
                                setAttribute("android:name", "appFamilies")
                                setAttribute("android:value", "x-lite")
                            },
                        )
                    }
                application.appendChild(activity)
            }
        }
    }
