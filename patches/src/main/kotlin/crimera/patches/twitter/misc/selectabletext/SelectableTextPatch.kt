package crimera.patches.twitter.misc.selectabletext

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.exception
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import org.w3c.dom.Element

@Suppress("unused")
val selectableTextPatch = resourcePatch(
    name = "Selectable Text",
    description = "Makes bio and username selectable",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    execute { context ->
        val profileDetailsXml = context["res/layout/profile_details.xml"]
        if (!profileDetailsXml.exists()) throw PatchException("profile_details.xml not found")

        val ids = listOf(
            "user_name",
            "user_bio",
        ).map { "@id/$it" }

        context.document["res/layout/profile_details.xml"].use { editor ->
            val texts = editor.getElementsByTagName("com.twitter.ui.components.text.legacy.TypefacesTextView")
            for (i in 0 until  texts.length) {
                val text = texts.item(i) as Element
                val id = text.getAttribute("android:id")

                if (ids.contains(id)) {
                    text.setAttribute("android:textIsSelectable", "true")
                }
            }
        }
    }
}