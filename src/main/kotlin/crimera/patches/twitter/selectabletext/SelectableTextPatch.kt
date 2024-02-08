package crimera.patches.twitter.selectabletext

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.Patch
import org.w3c.dom.Element

@Patch(
    name = "SelectableTextPatch",
    description = "Makes bio and username selectable",
)
@Suppress("unused")
object SelectableTextPatch: ResourcePatch() {
    override fun execute(context: ResourceContext) {
        val profileDetailsXml = context["res/layout/profile_details.xml"]
        if (!profileDetailsXml.exists()) throw PatchException("profile_details.xml not found")

        val ids = listOf(
            "user_name",
            "user_bio",
        ).map { "@id/$it" }

        context.xmlEditor["res/layout/profile_details.xml"].use { editor ->
            val texts = editor.file.getElementsByTagName("com.twitter.ui.components.text.legacy.TypefacesTextView")
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