/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.bringbacktwitter

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_BRING_BACK_TWITTER
import app.crimera.utils.replaceStringsInFile
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.ResourceGroup
import app.morphe.util.asSequence
import app.morphe.util.copyResources
import app.morphe.util.findElementByAttributeValue
import org.w3c.dom.Element

@Suppress("unused")
val bringBackTwitterPatch =
    resourcePatch(
        name = "Bring back twitter",
        description = "Bring back old twitter logo and name",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_BRING_BACK_TWITTER)

        dependsOn(addResourcesPatch)

        execute {
            addAppResources("twitter-bring-back")

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
                    "ic_vector_x.xml",
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

            listOf("mipmap-anydpi", "mipmap-anydpi-v26").forEach { dir ->
                listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { file ->
                    val launcherXml = get("res").resolve("$dir/$file")
                    if (launcherXml.exists()) {
                        document("res/$dir/$file").use { doc ->
                            doc.getElementsByTagName("foreground").item(0)?.let {
                                (it as Element).setAttribute("android:drawable", "@mipmap/ic_launcher_twitter_foreground")
                            }
                            doc.getElementsByTagName("monochrome").item(0)?.let {
                                (it as Element).setAttribute("android:drawable", "@mipmap/ic_launcher_twitter_foreground")
                            }
                        }
                    }
                }
            }

            sizes.forEach { size ->
                val mipmapDir = get("res").resolve("mipmap-$size")
                if (mipmapDir.exists()) {
                    val twitterIcon = mipmapDir.resolve("ic_launcher_twitter.webp")
                    val twitterRoundIcon = mipmapDir.resolve("ic_launcher_twitter_round.webp")
                    if (twitterIcon.exists()) {
                        twitterIcon.copyTo(mipmapDir.resolve("ic_launcher.webp"), overwrite = true)
                    }
                    if (twitterRoundIcon.exists()) {
                        twitterRoundIcon.copyTo(mipmapDir.resolve("ic_launcher_round.webp"), overwrite = true)
                    }
                }
            }

            // region Bring back twitter blue
            val twitterBlueColor = "@color/twitter_blue"

            val ocfLogo = get("res").resolve("layout/ocf_twitter_logo.xml")
            if (ocfLogo.exists()) {
                document("res/layout/ocf_twitter_logo.xml").use {
                    val imageView = it.getElementsByTagName("ImageView").item(0) as Element
                    imageView.setAttribute("app:tint", twitterBlueColor)
                }
            }

            val channelsToolbar = get("res").resolve("layout/channels_toolbar_main.xml")
            if (channelsToolbar.exists()) {
                document("res/layout/channels_toolbar_main.xml").use {
                    val imageView = it.getElementsByTagName("ImageView").item(0) as Element
                    imageView.setAttribute("app:tint", twitterBlueColor)
                }
            }

            val colorsXml = get("res").resolve("values/colors.xml")
            if (colorsXml.exists()) {
                document("res/values/colors.xml").use {
                    it
                        .getElementsByTagName("color")
                        .asSequence()
                        .find { color ->
                            (color as Element).getAttribute("name") == "ic_launcher_background"
                        }?.textContent = twitterBlueColor
                }
            }

            val animatedSplashVector = get("res").resolve("drawable/\$splash_screen_icon_animated__0.xml")
            if (animatedSplashVector.exists()) {
                val birdPath =
                    document("res/drawable/splash_screen_icon.xml").use { doc ->
                        doc.getElementsByTagName("path").item(0)?.let { (it as Element).getAttribute("android:pathData") }
                    }
                if (birdPath != null) {
                    document("res/drawable/\$splash_screen_icon_animated__0.xml").use { doc ->
                        doc.getElementsByTagName("path").item(0)?.let {
                            (it as Element).setAttribute("android:pathData", birdPath)
                        }
                    }
                }
            }

            // Keep splash colors; replace only the icon.
            val stylesXml = get("res").resolve("values/styles.xml")
            if (stylesXml.exists()) {
                document("res/values/styles.xml").use { document ->
                    val styleElement =
                        document.childNodes.findElementByAttributeValue("name", "Theme.LaunchScreen")
                            ?: document.childNodes.findElementByAttributeValue("name", "Theme.X.SplashScreen")

                    styleElement?.let { style ->
                        style.childNodes.findElementByAttributeValue("name", "windowSplashScreenAnimatedIcon")?.textContent = "@drawable/splash_screen_icon_animated"
                    }
                }
            }

            // Android 13+ may suppress the splash icon unless icon display is preferred.
            val v31StylesXml = get("res").resolve("values-v31/styles.xml")
            if (v31StylesXml.exists()) {
                document("res/values-v31/styles.xml").use { document ->
                    val splashStyle = document.childNodes.findElementByAttributeValue("name", "Theme.SplashScreen")
                    splashStyle?.let { style ->
                        val behavior =
                            style.childNodes.findElementByAttributeValue(
                                "name",
                                "android:windowSplashScreenBehavior",
                            ) ?: document.createElement("item").also { item ->
                                item.setAttribute("name", "android:windowSplashScreenBehavior")
                                style.appendChild(item)
                            }
                        behavior.textContent = "icon_preferred"
                    }
                }
            }
            // endregion

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
