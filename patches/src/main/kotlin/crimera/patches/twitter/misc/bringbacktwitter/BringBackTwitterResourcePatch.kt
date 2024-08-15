package crimera.patches.twitter.misc.bringbacktwitter

import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.ResourceGroup
import app.revanced.util.asSequence
import app.revanced.util.copyResources
import crimera.patches.twitter.misc.bringbacktwitter.strings.StringsMap
import org.w3c.dom.Element
import java.io.File
import java.io.FileWriter

@Suppress("unused")
val bringBackTwitterResourcePatch = resourcePatch(
    name = "Bring back twitter",
    description = "Bring back old twitter logo and name",
    use = false
) {
    compatibleWith("com.twitter.android")

    val mipmapIcons = arrayOf(
        "ic_launcher_twitter",
        "ic_launcher_twitter_round",
        "ic_launcher_twitter_foreground",
    ).map { "$it.webp" }.toTypedArray()

    val drawableIcons = arrayOf(
        "ic_vector_twitter",
        "ic_vector_home",
        "ic_vector_twitter_white",
        "ic_vector_home_stroke",
        "splash_screen_icon"
    ).map { "$it.xml" }.toTypedArray()

    val sizes = arrayOf(
        "xxxhdpi",
        "xxhdpi",
        "xhdpi",
        "hdpi",
        "mdpi",
    )

    execute { context ->
        // Change app name
        context.document["AndroidManifest.xml"].use {
            val application = it.getElementsByTagName("application").item(0) as Element
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
        context.document["res/layout/ocf_twitter_logo.xml"].use {
            val imageView = it.getElementsByTagName("ImageView").item(0) as Element
            imageView.setAttribute("app:tint", "@color/twitter_blue")
        }

        context.document["res/layout/channels_toolbar_main.xml"].use {
            val imageView = it.getElementsByTagName("ImageView").item(0) as Element
            imageView.setAttribute("app:tint", "@color/twitter_blue")
        }

        context.document["res/values/colors.xml"].use {
            it.getElementsByTagName("color").asSequence().find { color ->
                (color as Element).getAttribute("name") == "ic_launcher_background"
            }?.textContent = "@color/twitter_blue"
        }

        // update strings to old ones
        updateStrings(context)
    }
}

private fun updateStringsFile(stringsFile: File, stringsMap: Map<String, String>, context: ResourcePatchContext) {
    context.document[stringsFile.toString()].use { editor ->
        val document = editor

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
                val colorElement = document.createElement("string")

                colorElement.setAttribute("name", key)
                colorElement.textContent = value

                document.getElementsByTagName("resources").item(0).appendChild(colorElement)
            }
        }
    }
}

private fun updateStrings(context: ResourcePatchContext) {
    val langs = StringsMap.replacementMap
    for ((key, value) in langs) {
        val stringsFile = context["res/$key/strings.xml"]
        if (!stringsFile.isFile) {
//                println("$key/strings.xml not found")

            context["res/$key"].mkdirs()
            FileWriter(stringsFile).use {
                it.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
            }
        }
        updateStringsFile(stringsFile, value, context)
    }
}
