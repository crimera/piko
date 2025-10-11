package app.crimera.patches.twitter.misc.bringbacktwitter

import app.crimera.utils.replaceStringsInFile
import app.crimera.utils.replaceXmlResources
import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.ResourceGroup
import app.revanced.util.asSequence
import app.revanced.util.copyResources
import app.revanced.util.findElementByAttributeValueOrThrow
import org.w3c.dom.Element
import java.nio.file.Files

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
                val styleElement = document.childNodes.findElementByAttributeValueOrThrow(
                    "name",
                    "Theme.LaunchScreen"
                )

                val itemElement = styleElement.childNodes.findElementByAttributeValueOrThrow(
                    "name",
                    "windowSplashScreenBackground"
                )
                itemElement.textContent = twitterBlueColor
            }
                // endregion

            // region Change strings

            val isRunningOnManager = System.getProperty("java.runtime.name") == "Android Runtime"
            val basePath = "twitter/bringbacktwitter/strings"

            replaceXmlResources(basePath, ResourceGroup("values", "strings.xml"))

            /**
             * create directory for the untranslated language resources
             */
            val languages =
                arrayOf(
                    "en-rGB",
                    "hi",
                    "ru",
                    "tr",
                    "zh-rCN",
                    "zh-rTW",
                    "pl",
                    "",
                ).map { "values-$it" }

            languages.forEach {
                var folderName = it
                if (folderName.endsWith("-")) {
                    folderName = it.replace("-", "")
                }
                val vDirectory = get("res").resolve(folderName)
                if (!vDirectory.isDirectory) {
                    Files.createDirectories(vDirectory.toPath())
                }
                val resGroup = ResourceGroup(folderName, "strings.xml")
                replaceXmlResources(basePath, resGroup)

                /*
                 * The Java XML API on Android has a bug that converts surrogate pair characters
                 * to invalid numeric character references.
                 * It prevents resource compilation.
                 * Fix the text directly after closing the xmlEditor.
                 */
                if (isRunningOnManager) {
                    replaceStringsInFile(
                        resGroup,
                        replacements =
                            mapOf(
                                "&#55349;&#56655;" to "Twitter",
                                "&#55357;&#56613;" to "🔥",
                                "&#55356;&#57217;" to "🎁",
                            ),
                    )
                }
            }

            /*
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
