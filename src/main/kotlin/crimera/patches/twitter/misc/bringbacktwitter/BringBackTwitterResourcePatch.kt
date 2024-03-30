package crimera.patches.twitter.misc.bringbacktwitter

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.ResourceGroup
import app.revanced.util.asSequence
import app.revanced.util.copyResources
import org.w3c.dom.Element
import java.io.File

@Patch(
    name = "Bring back twitter",
    description = "Bring back old twitter logo and name",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
object BringBackTwitterResourcePatch : ResourcePatch() {
    val mipmapIcons = arrayOf(
        "ic_launcher_twitter",
        "ic_launcher_twitter_round",
        "ic_launcher_twitter_foreground",
    ).map { "$it.webp" }.toTypedArray()

    val drawableIcons = arrayOf(
        "ic_vector_twitter",
        "splash_screen_icon"
    ).map { "$it.xml" }.toTypedArray()

    val sizes = arrayOf(
        "xxxhdpi",
        "xxhdpi",
        "xhdpi",
        "hdpi",
        "mdpi",
    )

    override fun execute(context: ResourceContext) {

        // Change app name
        context.xmlEditor["AndroidManifest.xml"].use {
            val application = it.file.getElementsByTagName("application").item(0) as Element
            application.setAttribute("android:label", "Twitter")
        }

        // app icons
        // drawable icons
        sizes.map { "drawable-$it" }.plus("drawable").map {
            if (it == "drawable") {
                ResourceGroup(it, *drawableIcons)
            } else {
                ResourceGroup(it, "ic_stat_twitter.webp")
            }
        }.forEach {
            context.copyResources("twitter", it)
        }

        // mipmap icons
        sizes.map { "mipmap-$it" }.map {
            if (it == "mipmap-xxhdpi") {
                ResourceGroup(it, *mipmapIcons.plus("fg_launcher_twitter.webp"))
            } else {
                ResourceGroup(it, *mipmapIcons)
            }
        }.forEach {
            context.copyResources("twitter", it)
        }

        // bring back twitter blue
        context.xmlEditor["res/layout/ocf_twitter_logo.xml"].use {
            val imageView = it.file.getElementsByTagName("ImageView").item(0) as Element
            imageView.setAttribute("app:tint", "@color/twitter_blue")
        }

        context.xmlEditor["res/layout/channels_toolbar_main.xml"].use {
            val imageView = it.file.getElementsByTagName("ImageView").item(0) as Element
            imageView.setAttribute("app:tint", "@color/twitter_blue")
        }

        context.xmlEditor["res/values/colors.xml"].use {
            it.file.getElementsByTagName("color").asSequence().find { color ->
                (color as Element).getAttribute("name") == "ic_launcher_background"
            }?.textContent = "@color/twitter_blue"
        }

        // update strings to old ones
        updateStrings(context)
    }

    private fun updateStrings(context: ResourceContext) {
        val stringsFile = context["res/values/strings.xml"]
        val stringsUK = context["res/values-en-rGB/strings.xml"]

        when {
            !stringsUK.isFile -> throw PatchException("$stringsUK file not found.")
            !stringsFile.isFile -> throw PatchException("$stringsFile file not found.")
        }

        // Update strings.xml
        updateStringsFile(stringsFile, context)
        // Update strings-en-rGB.xml (British English)
        updateStringsFile(stringsUK, context)
    }

    private fun updateStringsFile(stringsFile: File, context: ResourceContext) {
        context.xmlEditor[stringsFile.toString()].use { editor ->
            val document = editor.file

            val stringsMap = StringsMap.replacementMap

            for ((key, value) in stringsMap) {
                val nodes = document.getElementsByTagName("string")
                var keyReplaced = false

                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    if (node.attributes.getNamedItem("name")?.nodeValue == key) {
                        node.textContent = value
                        keyReplaced = true
                        break
                    }
                }

                // log which keys were not found or failed
                if (!keyReplaced) {
                    println("Key not found: $key")
                }
            }
        }
    }
}
