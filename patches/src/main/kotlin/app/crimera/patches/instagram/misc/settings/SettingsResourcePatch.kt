/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import org.w3c.dom.Element

val settingsResourcePatch =
    resourcePatch(
        description = "Settings resource patch.",
    ) {
        execute {
            // Add piko settings to the launcher icon long-press shortcuts
            document("res/xml/shortcuts.xml").use {
                val existing = it.getElementsByTagName("shortcut")
                for (index in 0 until existing.length) {
                    val sc = existing.item(index) as? Element ?: continue
                    if (sc.getAttribute("android:shortcutId") == "piko_settings") return@use
                }

                val pikoExtra =
                    it.createElement("extra").apply {
                        setAttribute("android:name", "piko")
                        setAttribute("android:value", "true")
                    }
                val launchTypeExtra =
                    it.createElement("extra").apply {
                        setAttribute("android:name", "piko_launch_type")
                        setAttribute("android:value", "settings")
                    }
                val shortcutExtra =
                    it.createElement("extra").apply {
                        setAttribute("android:name", "shortcut")
                        setAttribute("android:value", "settings")
                    }
                val intentElement =
                    it.createElement("intent").apply {
                        setAttribute("android:targetPackage", packageMetadata.packageName)
                        setAttribute("android:action", "android.intent.action.VIEW")
                        setAttribute("android:targetClass", "com.instagram.modal.ModalActivity")
                        appendChild(pikoExtra)
                        appendChild(launchTypeExtra)
                        appendChild(shortcutExtra)
                    }
                val shortcutElement =
                    it.createElement("shortcut").apply {
                        setAttribute("android:icon", "@drawable/piko_settings_shortcut_icon")
                        setAttribute("android:enabled", "true")
                        setAttribute("android:shortcutId", "piko_settings")
                        setAttribute("android:shortcutShortLabel", "@string/piko_title_settings")
                        setAttribute("android:shortcutLongLabel", "@string/piko_title_settings")
                        appendChild(intentElement)
                    }
                it.documentElement.insertBefore(shortcutElement, it.documentElement.firstChild)
            }

            copyResources(
                "instagram/settings",
                ResourceGroup(
                    "drawable",
                    "piko_settings_shortcut_icon.xml",
                    "piko_settings_shortcut_foreground.xml",
                    "piko_ghost_icon.xml",
                ),
                ResourceGroup(
                    "raw",
                    "piko_tabler_icons_license.txt",
                ),
            )
        }
    }
