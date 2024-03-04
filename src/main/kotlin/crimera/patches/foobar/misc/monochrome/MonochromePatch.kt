package crimera.patches.foobar.misc.monochrome

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources

@Patch(
    name = "Monochrome",
    description = "Makes icon adaptive",
    compatiblePackages = [CompatiblePackage("com.foobar2000.foobar2000")]
)
object MonochromePatch : ResourcePatch() {
    override fun execute(context: ResourceContext) {
        context.xmlEditor["res/mipmap-anydpi-v26/ic_launcher.xml"].use {
            val document = it.file

            val monochrome = document.createElement("monochrome")

            monochrome.setAttribute("android:drawable", "@mipmap/ic_launcher_foreground")

            document.getElementsByTagName("adaptive-icon").item(0).appendChild(monochrome)
        }

        context.copyResources(
            "foobar",
            ResourceGroup("mipmap-hdpi", "ic_launcher_foreground.png"),
            ResourceGroup("mipmap-mdpi", "ic_launcher_foreground.png"),
            ResourceGroup("mipmap-xhdpi", "ic_launcher_foreground.png"),
            ResourceGroup("mipmap-xxhdpi", "ic_launcher_foreground.png"),
            ResourceGroup("mipmap-xxxhdpi", "ic_launcher_foreground.png"),
        )
    }
}