package app.crimera.patches.twitter.misc.bringbacktwitter

import app.crimera.utils.replaceStringsInFile
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.locales
import app.morphe.util.*
import org.w3c.dom.Element

@Suppress("unused")
val bringBackTwitterPatch =
    resourcePatch(
        name = "Bring back twitter",
        description = "Bring back old twitter logo and name",
        use = false,
    ) {
        compatibleWith("com.twitter.android")

        execute {
            // region Change app name

            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as Element
                application.setAttribute("android:label", "Twitter")
            }

            // endregion

            // region Change app icons

            val mipmapIcons =
                arrayOf(
                    "ic_launcher_twitter.webp",
                    "ic_launcher_twitter_round.webp",
                    "ic_launcher_twitter_foreground.webp",
                )

            val drawableIcons =
                arrayOf(
                    "ic_vector_twitter.xml",
                    "ic_vector_home.xml",
                    "ic_vector_twitter_white.xml",
                    "ic_vector_home_stroke.xml",
                    "splash_screen_icon.xml",
                )

            val sizes =
                arrayOf(
                    "xxxhdpi",
                    "xxhdpi",
                    "xhdpi",
                    "hdpi",
                    "mdpi",
                )

            // drawable icons
            sizes
                .map { "drawable-$it" }
                .plus("drawable")
                .map {
                    if (it == "drawable") {
                        ResourceGroup(it, *drawableIcons)
                    } else {
                        ResourceGroup(it, "ic_stat_twitter.webp")
                    }
                }.forEach {
                    copyResources("twitter/bringbacktwitter", it)
                }

            // mipmap icons
            sizes
                .map { "mipmap-$it" }
                .map {
                    if (it == "mipmap-xxhdpi") {
                        ResourceGroup(it, *mipmapIcons.plus("fg_launcher_twitter.webp"))
                    } else {
                        ResourceGroup(it, *mipmapIcons)
                    }
                }.forEach {
                    copyResources("twitter/bringbacktwitter", it)
                }

            // endregion

            // region Bring back twitter blue
            val twitterBlueColor = "@color/twitter_blue"
            document("res/layout/ocf_twitter_logo.xml").use {
                val imageView = it.getElementsByTagName("ImageView").item(0) as Element
                imageView.setAttribute("app:tint", twitterBlueColor)
            }

            document("res/layout/channels_toolbar_main.xml").use {
                val imageView = it.getElementsByTagName("ImageView").item(0) as Element
                imageView.setAttribute("app:tint", twitterBlueColor)
            }

            document("res/values/colors.xml").use {
                it
                    .getElementsByTagName("color")
                    .asSequence()
                    .find { color ->
                        (color as Element).getAttribute("name") == "ic_launcher_background"
                    }?.textContent = twitterBlueColor
            }

            // Splashscreen to blue
            document("res/values/styles.xml").use { document ->
                val styleElement =
                    document.childNodes.findElementByAttributeValueOrThrow(
                        "name",
                        "Theme.LaunchScreen",
                    )

                val itemElement =
                    styleElement.childNodes.findElementByAttributeValueOrThrow(
                        "name",
                        "windowSplashScreenBackground",
                    )
                itemElement.textContent = twitterBlueColor
            }
            // endregion

            // region Change strings

            locales.forEach { locale ->
                val srcSubPath = "${locale.getSrcLocaleFolderName()}/strings.xml"
                val destPath = "res/${locale.getDestLocaleFolderName()}/strings.xml"
                if (!get(destPath).exists()) {
                    // Split apk for this language is missing, ignoring.
                    return@forEach
                }

                // Load replacement values from the source resource, and construct a replacement map.
                val replacementMap = HashMap<String, String>()
                val srcStream = inputStreamFromBundledResource("twitter/bringbacktwitter/strings", srcSubPath)
                    ?: throw PatchException("Could not find $srcSubPath")
                document(srcStream).use { srcDoc ->
                    srcDoc.documentElement.forEachChildElement {
                        replacementMap[it.getAttribute("name")] = it.textContent
                    }
                }
                if (replacementMap.isEmpty()) return@forEach

                // Apply replacements to the destination resource.
                document(destPath).use { destDoc ->
                    destDoc.documentElement.forEachChildElement {
                        it.textContent = replacementMap[it.getAttribute("name")] ?: return@forEachChildElement
                    }
                }
            }

            /*
             * Special handling for Japanese.
             * Instead of defining strings in the map, replaces texts directly.
             * Reason: https://t.me/pikopatches/1/17339
             */
            replaceStringsInFile(
                ResourceGroup("values-ja", "strings.xml", "arrays.xml"),
                replacements =
                    mapOf(
                        "X" to "Twitter",
                        "ポスト" to "ツイート",
                    ),
            )

            // endregion
        }
    }
