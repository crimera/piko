/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.dynamiccolor

import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.patch.resourcePatch
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
        }
    }
