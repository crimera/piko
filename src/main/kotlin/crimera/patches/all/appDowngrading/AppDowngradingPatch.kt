package crimera.patches.all.appDowngrading

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.Patch
import org.w3c.dom.Element


@Patch(
    name = "Enable app downgrading",
    description = "Sets app version to a default value making installation of different versions possible",
    use = false
)
object AppDowngradingPatch: ResourcePatch() {
    override fun execute(context: ResourceContext) {
        context.xmlEditor["AndroidManifest.xml"].use {
            val manifestElement = it.file.getElementsByTagName("manifest").item(0) as Element
            manifestElement.setAttribute("android:versionCode", "999999999")
        }
    }
}
