package app.crimera.patches.twitter.misc.settings

import app.revanced.patcher.patch.resourcePatch
import app.revanced.patches.shared.misc.mapping.resourceMappingPatch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources
import org.w3c.dom.Element
import java.nio.file.Files

internal val settingsResourcePatch =
    resourcePatch {
        dependsOn(resourceMappingPatch)
        execute {
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
                    editor.createElement("app.revanced.extension.twitter.settings.widgets.PikoSettingsButton")
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

            val basePath = "twitter/settings/strings"

            copyResources(basePath, ResourceGroup("values", "strings.xml", "arrays.xml"), appendPiko = true)

            /**
             * create directory for the untranslated language resources
             */
            val languages =
                arrayOf(
                    "ar",
                    "es",
                    "fr",
                    "hi",
                    "in",
                    "ja",
                    "ko",
                    "it",
                    "pl",
                    "pt-rBR",
                    "ru",
                    "tr",
                    "uk",
                    "v21",
                    "vi",
                    "zh-rCN",
                    "zh-rTW",
                    "zh-rHK",
                ).map { "values-$it" }

            languages.forEach {
                val vDirectory = get("res").resolve(it)
                if (!vDirectory.isDirectory) {
                    Files.createDirectories(vDirectory.toPath())
                    if (it.contains("v21")) {
                        copyResources(basePath, ResourceGroup(it, "arrays.xml"), appendPiko = true)
                    }
                }
                copyResources(basePath, ResourceGroup(it, "strings.xml"), appendPiko = true)
            }

            // execute end
        }
    }
