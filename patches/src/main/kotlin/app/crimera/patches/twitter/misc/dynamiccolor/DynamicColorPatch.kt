/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.dynamiccolor

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.asSequence
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.FileWriter
import java.nio.file.Files

@Suppress("unused")
val dynamicColorPatch =
    resourcePatch(
        name = "Dynamic color",
        description = "Replaces the default Twitter Blue with the user's Material You palette.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X)

        execute {
            // For backward compatibility, add colors and styles into v31 res dir (A12+).
            val valuesV31Directory = get("res/values-v31")
            val valuesNightV31Directory = get("res/values-night-v31")

            listOf(valuesV31Directory, valuesNightV31Directory).forEach { directory ->
                if (!directory.isDirectory) Files.createDirectories(directory.toPath())

                val colorsXml = directory.resolve("colors.xml")

                if (!colorsXml.exists()) {
                    FileWriter(colorsXml).use {
                        it.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
                    }
                }
            }

            val stylesXml = valuesV31Directory.resolve("styles.xml")
            if (!stylesXml.exists()) {
                FileWriter(stylesXml).use {
                    it.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
                }
            }

            document("res/values-v31/colors.xml").use { document ->
                val resourcesElement = document.documentElement
                mapOf(
                    "ps__twitter_blue" to "@color/twitter_blue",
                    "twitter_blue" to "@color/m3_sys_color_dynamic_light_primary",
                    "twitter_blue_fill_pressed" to "@color/m3_sys_color_dynamic_light_primary_container",
                    "twitter_blue_opacity_30" to "@color/material_dynamic_primary95",
                    "twitter_blue_opacity_50" to "@color/material_dynamic_primary90",
                    "twitter_blue_opacity_58" to "@color/material_dynamic_primary80",
                    "deep_transparent_twitter_blue" to "@color/material_dynamic_primary90",
                ).forEach { (name, value) ->
                    val colorElement = document.createElement("color")
                    colorElement.setAttribute("name", name)
                    colorElement.textContent = value
                    resourcesElement.appendChild(colorElement)
                }
            }

            document("res/values-night-v31/colors.xml").use { document ->
                val resourcesElement = document.documentElement
                mapOf(
                    "twitter_blue" to "@color/m3_sys_color_dynamic_dark_primary",
                    "twitter_blue_fill_pressed" to "@color/m3_sys_color_dynamic_dark_primary_container",
                    "twitter_blue_opacity_30" to "@color/material_dynamic_primary30",
                    "twitter_blue_opacity_50" to "@color/material_dynamic_primary40",
                    "twitter_blue_opacity_58" to "@color/material_dynamic_primary50",
                    "deep_transparent_twitter_blue" to "@color/m3_sys_color_dynamic_dark_primary_container",
                ).forEach { (name, value) ->
                    val colorElement = document.createElement("color")
                    colorElement.setAttribute("name", name)
                    colorElement.textContent = value
                    resourcesElement.appendChild(colorElement)
                }
            }

            document("res/values-v31/styles.xml").use { document ->
                val standardStyle = document.createElement("style")
                standardStyle.setAttribute("name", "PaletteStandard")
                standardStyle.setAttribute("parent", "@style/HorizonColorPaletteLight")

                mapOf(
                    "abstractColorCellBackground" to "@color/m3_sys_color_dynamic_light_surface",
                    "abstractColorCellBackgroundTranslucent" to "@color/m3_sys_color_dynamic_light_surface_container_low",
                    "abstractColorDeepGray" to "@color/m3_sys_color_dynamic_light_on_surface_variant",
                    "abstractColorDivider" to "@color/m3_sys_color_dynamic_light_outline_variant",
                    "abstractColorFadedGray" to "@color/m3_sys_color_dynamic_light_surface_container",
                    "abstractColorFaintGray" to "@color/m3_sys_color_dynamic_light_surface_container_low",
                    "abstractColorHighlightBackground" to "@color/m3_sys_color_dynamic_light_surface_container_high",
                    "abstractColorLightGray" to "@color/m3_sys_color_dynamic_light_outline_variant",
                    "abstractColorLink" to "@color/twitter_blue",
                    "abstractColorMediumGray" to "@color/m3_sys_color_dynamic_light_outline",
                    "abstractColorText" to "@color/m3_sys_color_dynamic_light_on_surface",
                    "abstractColorUnread" to "@color/m3_sys_color_dynamic_light_primary_container",
                    "abstractElevatedBackground" to "@color/m3_sys_color_dynamic_light_surface_container_low",
                    "abstractElevatedBackgroundShadow" to "@color/black_opacity_10",
                ).forEach { (name, value) ->
                    val styleElement = document.createElement("item")
                    styleElement.setAttribute("name", name)
                    styleElement.textContent = value
                    standardStyle.appendChild(styleElement)
                }

                document.documentElement.appendChild(standardStyle)
            }

            // Dim and Lights out: without these overrides they keep their hard-coded navy
            // (#15202B) and gray backgrounds, so only the accent follows the palette.
            document("res/values-night-v31/colors.xml").use { document ->
                val resourcesElement = document.documentElement
                mapOf(
                    // Window and status bar background in dark mode.
                    "app_background" to "@color/m3_sys_color_dynamic_dark_surface_container",
                    "border_color" to "@color/m3_sys_color_dynamic_dark_outline_variant",
                ).forEach { (name, value) ->
                    val colorElement = document.createElement("color")
                    colorElement.setAttribute("name", name)
                    colorElement.textContent = value
                    resourcesElement.appendChild(colorElement)
                }
            }

            val dark = "@color/m3_sys_color_dynamic_dark_"

            // Dim's translucent cell background is its cell background at 75% alpha (#bf15202b).
            // Resource references can't carry alpha, so wrap the dynamic color in a color state
            // list that applies the same alpha.
            val translucentDimBackground = "piko_dynamic_dark_surface_container_translucent"
            val colorV31Directory = get("res/color-v31")
            if (!colorV31Directory.isDirectory) Files.createDirectories(colorV31Directory.toPath())
            FileWriter(colorV31Directory.resolve("$translucentDimBackground.xml")).use {
                it.write(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                        "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">" +
                        "<item android:color=\"${dark}surface_container\" android:alpha=\"0.75\"/>" +
                        "</selector>",
                )
            }

            val sharedDarkPalette =
                mapOf(
                    "abstractColorDeepGray" to "${dark}on_surface_variant",
                    "abstractColorDivider" to "${dark}outline_variant",
                    "abstractColorLightGray" to "${dark}outline_variant",
                    "abstractColorLink" to "@color/twitter_blue",
                    "abstractColorMediumGray" to "${dark}outline",
                    "abstractColorText" to "${dark}on_surface",
                    "abstractColorUnread" to "${dark}primary_container",
                    "abstractElevatedBackgroundShadow" to "@color/black_opacity_10",
                )

            document("res/values/styles.xml").use { source ->
                document("res/values-v31/styles.xml").use { target ->
                    // Each style is copied whole so items this patch does not override keep
                    // the app's own values, then the background items are re-pointed.
                    target.overrideStyle(
                        source,
                        "PaletteDim",
                        sharedDarkPalette +
                            mapOf(
                                "abstractColorCellBackground" to "${dark}surface_container",
                                "abstractColorCellBackgroundTranslucent" to "@color/$translucentDimBackground",
                                "abstractColorFadedGray" to "${dark}surface",
                                "abstractColorFaintGray" to "${dark}surface_container_high",
                                "abstractColorHighlightBackground" to "${dark}surface_container_low",
                                "abstractElevatedBackground" to "${dark}surface_container_high",
                            ),
                    )
                    // Lights out stays black behind the timeline; everything drawn on it
                    // takes the palette.
                    target.overrideStyle(
                        source,
                        "PaletteLightsOut",
                        sharedDarkPalette +
                            mapOf(
                                "abstractColorFadedGray" to "${dark}surface_container",
                                "abstractColorFaintGray" to "${dark}surface_container_low",
                                "abstractColorHighlightBackground" to "${dark}surface_container_lowest",
                                "abstractElevatedBackground" to "${dark}surface_container",
                            ),
                    )
                    target.overrideStyle(
                        source,
                        "TwitterBase.Dim",
                        mapOf(
                            "coreColorButtonNeutralFill" to "${dark}surface_container_highest",
                            "coreColorExclusiveBenefitsBackground" to "${dark}surface_container",
                            "coreColorPlaceholderBg" to "${dark}surface_container_high",
                            "coreColorPopupBackground" to "${dark}surface_container_high",
                            "coreTweetReactionHighlightColor" to "${dark}outline_variant",
                        ),
                    )
                    target.overrideStyle(
                        source,
                        "TwitterBase.LightsOut",
                        mapOf(
                            "coreColorPopupBackground" to "${dark}surface_container",
                            "coreTweetReactionHighlightColor" to "${dark}outline_variant",
                        ),
                    )
                }
            }
        }
    }

/**
 * Copies the style [name] from [source] (res/values) into this document (res/values-v31), so it
 * only applies on Android 12+, and sets the given items. Items not in [items] keep the app's own
 * values; items the app does not define are added.
 */
private fun Document.overrideStyle(
    source: Document,
    name: String,
    items: Map<String, String>,
) {
    val original =
        source
            .getElementsByTagName("style")
            .asSequence()
            .map { it as Element }
            .firstOrNull { it.getAttribute("name") == name }
            ?: throw PatchException("Style $name not found")

    getElementsByTagName("style")
        .asSequence()
        .map { it as Element }
        .filter { it.getAttribute("name") == name }
        .toList()
        .forEach { it.parentNode.removeChild(it) }

    val style = importNode(original, true) as Element
    val existing =
        style.getElementsByTagName("item").asSequence().map { it as Element }
            .associateBy { it.getAttribute("name") }
    items.forEach { (item, value) ->
        val element =
            existing[item] ?: createElement("item").also {
                it.setAttribute("name", item)
                style.appendChild(it)
            }
        element.textContent = value
    }
    documentElement.appendChild(style)
}
