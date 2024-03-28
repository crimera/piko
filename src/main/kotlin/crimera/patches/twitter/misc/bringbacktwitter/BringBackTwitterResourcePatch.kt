package crimera.patches.twitter.misc.bringbacktwitter

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import org.w3c.dom.Element


@Patch(
    name = "Bring back twitter",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object BringBackTwitterResourcePatch: ResourcePatch() {
    override fun execute(context: ResourceContext) {
        // Change app name
        context.xmlEditor["res/values/strings.xml"].use {
            val strings = it.file.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val string = strings.item(i) as Element

                if (!string.getAttribute("name").contains("api_key")) {
                    string.textContent = string.textContent.replace("X", "Twitter")
                }
            }
        }
    }

}
