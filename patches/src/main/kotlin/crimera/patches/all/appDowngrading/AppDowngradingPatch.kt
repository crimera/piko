package crimera.patches.all.appDowngrading

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

@Suppress("unused")
val appDowngradingPatch = resourcePatch(
    name = "Enable app downgrading",
    description = "Sets app version to a default value making installation of different versions possible",
    use = false
) {
    compatibleWith("com.twitter.android")

    execute { context ->
        context.document["AndroidManifest.xml"].use {
            val manifestElement = it.getElementsByTagName("manifest").item(0) as Element
            manifestElement.setAttribute("android:versionCode", "999999999")
        }
    }
}
