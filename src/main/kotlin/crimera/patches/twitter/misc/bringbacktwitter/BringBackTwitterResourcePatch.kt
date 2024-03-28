package crimera.patches.twitter.misc.bringbacktwitter

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.ResourceGroup
import app.revanced.util.asSequence
import app.revanced.util.copyResources
import org.w3c.dom.Element


@Patch(
    name = "Bring back twitter",
    description = "Bring back old twitter logo and name",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object BringBackTwitterResourcePatch: ResourcePatch() {
    val icons = arrayOf(
        "ic_launcher_twitter",
        "ic_launcher_twitter_round",
        "ic_launcher_twitter_foreground",
    )

    val mipmapDirectories = arrayOf(
        "xxxhdpi",
        "xxhdpi",
        "xhdpi",
        "hdpi",
        "mdpi",
        "anydpi",
    ).map { "mipmap-$it" }

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

        // Change app icon
        mipmapDirectories.map { directory ->
            if (directory.contains("anydpi")) {
                ResourceGroup(
                    directory,
                    *icons.map { "$it.xml" }.filter { !it.contains("foreground") }.toTypedArray()
                )
            } else {
                ResourceGroup(
                    directory,
                    *icons.map { "$it.webp" }.toTypedArray()
                )
            }
        }.forEach {
            context.copyResources("twitter", it)
        }

        // copy vector icons for topbar and splash
        val drawables = arrayOf("ic_vector_twitter", "splash_screen_icon").map { "$it.xml" }.toTypedArray()
        context.copyResources("twitter", ResourceGroup("drawable", *drawables))

        context.xmlEditor["res/layout/ocf_twitter_logo.xml"].use {
            val imageView = it.file.getElementsByTagName("ImageView").item(0) as Element
            imageView.setAttribute("app:tint", "@color/twitter_blue")
        }

        context.xmlEditor["res/layout/channels_toolbar_main.xml"].use {
            val imageView = it.file.getElementsByTagName("ImageView").item(0) as Element
            imageView.setAttribute("app:tint", "@color/twitter_blue")
        }

        // bring back twitter blue
        context.xmlEditor["res/values/colors.xml"].use {
            it.file.getElementsByTagName("color").asSequence().find { color ->
                (color as Element).getAttribute("name") == "ic_launcher_background"
            }?.textContent = "@color/twitter_blue"
        }
    }
}
