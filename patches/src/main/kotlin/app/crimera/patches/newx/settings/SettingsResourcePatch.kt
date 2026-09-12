package app.crimera.patches.newx.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import org.w3c.dom.Element

internal val newXSettingsResourcePatch =
    resourcePatch {
        dependsOn(resourceMappingPatch)

        execute {
            document("AndroidManifest.xml").use { document ->
                val application =
                    document.getElementsByTagName("application").item(0) as? Element
                        ?: error("NewX settings could not find the manifest application element")
                val activity =
                    document.createElement("activity").apply {
                        setAttribute(
                            "android:name",
                            "app.morphe.extension.newx.settings.NewXSettingsActivity",
                        )
                        setAttribute("android:excludeFromRecents", "true")
                        setAttribute("android:exported", "false")
                        appendChild(
                            document.createElement("meta-data").apply {
                                setAttribute("android:name", "appFamilies")
                                // This is X's internal app-family ID, not the patch name.
                                setAttribute("android:value", "x-lite")
                            },
                        )
                    }
                application.appendChild(activity)
            }
        }
    }
