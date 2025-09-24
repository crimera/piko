package app.revanced.patches.all.misc.versioncode

import app.revanced.patcher.patch.intOption
import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.getNode
import org.w3c.dom.Element

@Suppress("unused")
val changeVersionCodePatch =
    resourcePatch(
        name = "Change version code",
        description =
            "Changes the version code of the app. This will turn off app store updates " +
                "and allows downgrading an existing app install to an older app version.",
    ) {
        val versionCode by intOption(
            key = "versionCode",
            default = Int.MAX_VALUE,
            values =
                mapOf(
                    "Lowest" to 1,
                    "Highest" to Int.MAX_VALUE,
                ),
            title = "Version code",
            description =
                "The version code to use. Using the highest value turns off app store " +
                    "updates and allows downgrading an existing app install to an older app version.",
            required = true,
        ) { versionCode -> versionCode!! >= 1 }

        execute {
            document("AndroidManifest.xml").use { document ->
                val manifestElement = document.getNode("manifest") as Element
                manifestElement.setAttribute("android:versionCode", versionCode.toString())
            }
        }
    }
