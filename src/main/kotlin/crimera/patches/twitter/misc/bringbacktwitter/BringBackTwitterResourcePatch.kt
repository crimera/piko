package crimera.patches.twitter.misc.bringbacktwitter

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.ResourceGroup
import app.revanced.util.asSequence
import app.revanced.util.copyResources
import crimera.patches.twitter.misc.bringbacktwitter.custromstringsupdater.ja
import crimera.patches.twitter.misc.bringbacktwitter.custromstringsupdater.pt_rBR
import crimera.patches.twitter.misc.bringbacktwitter.strings.StringsMap
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
        "ic_vector_twitter", "ic_vector_home", "ic_vector_twitter_white", "ic_vector_home_stroke", "splash_screen_icon"
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
            val folderName = context["res/${it.resourceDirectoryName}"]
            if (folderName.exists()) {
                context.copyResources("twitter/bringbacktwitter", it)
            }
        }

        // mipmap icons
        sizes.map { "mipmap-$it" }.map {
            if (it == "mipmap-xxhdpi") {
                ResourceGroup(it, *mipmapIcons.plus("fg_launcher_twitter.webp"))
            } else {
                ResourceGroup(it, *mipmapIcons)
            }
        }.forEach {
            val folderName = context["res/${it.resourceDirectoryName}"]
            if (folderName.exists()) {
                context.copyResources("twitter/bringbacktwitter", it)
            }
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
        ja.updateStrings(context)
        pt_rBR.updateStrings(context)
    }

    private fun updateStrings(context: ResourceContext) {
        val langs = StringsMap.replacementMap
        for ((key, value) in langs) {
            val stringsFile = context["res/$key/strings.xml"]
            if (stringsFile.exists()) {
                updateStringsFile(stringsFile, value, context)
            }
        }
    }

    private fun String.startsWithSpecialByte() = encodeToByteArray()[0] == (-16).toByte()


    private fun updateStringsFile(stringsFile: File, stringsMap: Map<String, String>, context: ResourceContext) {
        context.xmlEditor[stringsFile.toString()].use { editor ->
            val document = editor.file

            val nodes = document.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes.getNamedItem("name")?.nodeValue

                if (name == "conference_default_title") {
                    /*
                     * Parsing XML causes the string which contains the
                     * character "𝕏" to be corrupted, so we change it to "Twitter"
                     */
                    node.textContent = stringsMap[name] ?: run {
                        val content = node.textContent
                        val delimiter = if (content.contains("-")) '-' else ' '
                        content.split(delimiter).joinToString(delimiter.toString()) {
                            if (it.startsWithSpecialByte()) "Twitter" else it
                        }
                    }
                    continue
                }

                node.textContent = stringsMap[name] ?: continue
            }
        }
    }
}
