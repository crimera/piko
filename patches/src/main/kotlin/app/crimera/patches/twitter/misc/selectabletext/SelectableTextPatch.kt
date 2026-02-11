package app.crimera.patches.twitter.misc.selectabletext

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val selectableTextResourcePatch =
    resourcePatch {
        execute {
            val ids =
                listOf(
                    "user_name",
                    "user_bio",
                ).map { "@id/$it" }

            document("res/layout/profile_details.xml").use { editor ->
                val texts = editor.getElementsByTagName("com.twitter.ui.components.text.legacy.TypefacesTextView")
                for (i in 0 until texts.length) {
                    val text = texts.item(i) as Element
                    val id = text.getAttribute("android:id")

                    if (ids.contains(id)) {
                        text.setAttribute("android:textIsSelectable", "true")
                    }
                }
            }
        }
    }

@Suppress("unused")
val selectableTextPatch =
    bytecodePatch(
        name = "Selectable Text",
        description = "Makes bio and username selectable",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, selectableTextResourcePatch)

        execute {
            settingsStatusLoadFingerprint.enableSettings("selectableText")
        }
    }
