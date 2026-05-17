/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.getNode
import org.w3c.dom.Element

internal val settingsResourcePatch =
    resourcePatch {
        dependsOn(resourceMappingPatch)
        execute {
            // replace the keyword `ripped` from version name back to original format.
            val versionName = packageMetadata.versionName
            val rippedKeyword = "-ripped"
            if (rippedKeyword in versionName) {
                document("AndroidManifest.xml").use { document ->
                    val manifestElement = document.getNode("manifest") as Element
                    manifestElement.setAttribute("android:versionName", versionName.replace(rippedKeyword, ""))
                }
            }

            document("res/xml/settings_root.xml").use { editor ->
                val parent = editor.getElementsByTagName("PreferenceScreen").item(0) as Element

                val prefMod = editor.createElement("Preference")
                prefMod.setAttribute("android:icon", "@drawable/ic_vector_settings_stroke")
                prefMod.setAttribute("android:title", "@string/piko_title_settings")
                prefMod.setAttribute("android:key", "pref_mod")
                prefMod.setAttribute("android:order", "110")

                parent.appendChild(prefMod)
            }

            // credits aero
            document("res/layout/main_activity_app_bar.xml").use { editor ->
                val parent = editor.getElementsByTagName("FrameLayout").item(1) as Element

                val sideBtn =
                    editor.createElement("app.morphe.extension.twitter.settings.widgets.PikoSettingsButton")
                sideBtn.setAttribute("android:text", "Piko")
                sideBtn.setAttribute("android:textAllCaps", "false")
                sideBtn.setAttribute("android:background", "?android:attr/selectableItemBackground")
                sideBtn.setAttribute("android:drawableLeft", "@drawable/ic_vector_settings_stroke")
                sideBtn.setAttribute("android:drawableTint", "?attr/coreColorPrimaryText")
                sideBtn.setAttribute("android:layout_height", "@dimen/docker_height")
                sideBtn.setAttribute("android:layout_width", "wrap_content")
                sideBtn.setAttribute("android:padding", "10dp")
                sideBtn.setAttribute("android:layout_marginBottom", "2.3dp")
                sideBtn.setAttribute("android:layout_gravity", "bottom|right")

                parent.appendChild(sideBtn)
            }
        }
    }
