package app.crimera.patches.newx.settings

import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources

internal val newXSettingsResourcePatch =
    patch {

        execute {
            resourceXml("AndroidManifest.xml").use { manifest ->
                val application =
                    manifest.elements("application").firstOrNull()
                        ?: error("NewX settings could not find the manifest application element")
                application.appendChild("activity") {
                    setAttribute(
                        "android:name",
                        "app.morphe.extension.newx.settings.NewXSettingsActivity",
                    )
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:exported", "false")
                    appendChild("meta-data") {
                        setAttribute("android:name", "appFamilies")
                        // This is X's internal app-family ID, not the patch name.
                        setAttribute("android:value", "x-lite")
                    }
                }

                application.appendChild("activity") {
                    setAttribute(
                        "android:name",
                        "app.morphe.extension.crimera.downloader.FolderPickerActivity",
                    )
                    setAttribute("android:excludeFromRecents", "true")
                    setAttribute("android:exported", "false")
                    // The picker has no UI of its own; it only hosts the SAF folder chooser.
                    setAttribute("android:theme", "@android:style/Theme.Translucent.NoTitleBar")
                }
            }

            copyResources(
                "newx/settings",
                ResourceGroup("drawable", "$PIKO_SETTINGS_ICON_DRAWABLE.xml"),
            )
        }
    }
