package crimera.patches.twitter.misc.bringbacktwitter

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.*
import org.w3c.dom.Element
import java.nio.file.Files


@Patch(
    name = "Bring back twitter",
    description = "Bring back old twitter logo and name",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
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

    val languages = arrayOf(
        "fa", "de", "sr", "ko", "pt", "ro", "bg",
        "zh-rCN", "tl", "gu", "ar-rEH", "cs", "hr", "hu",
        "bn", "fr", "ja", "uk", "sk", "it", "iw",
        "in", "pl", "hi", "ru", "ms", "th", "nl",
        "en-rGB", "ca", "zh-rTW", "zh-rHK", "el", "ta", "es",
        "kn", "fi", "vi", "ar", "mr", "da", "sv",
        "tr", "nb", ""
    ).map {
        if (it == "") {
            "res/values/strings.xml"
        } else {
            "res/values-$it/strings.xml"
        }
    }

    override fun execute(context: ResourceContext) {

        // Change app name
        languages.forEach { file ->
            context.xmlEditor[file].use {
                val strings = it.file.getElementsByTagName("string")
                for (i in 0 until strings.length) {
                    val string = strings.item(i) as Element

                    if (!string.getAttribute("name").contains("api_key")) {
                        string.textContent = string.textContent.replace("X", "Twitter")
                    }
                }
            }
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
    }
}
