package app.crimera.patches.twitter.misc.dynamiccolor

import app.crimera.patches.twitter.shared.Constants.COMPATIBILITY_X
import app.morphe.patcher.patch.resourcePatch
import java.io.FileWriter
import java.nio.file.Files

@Suppress("unused")
val dynamicColorPatch = resourcePatch(
    name = "Dynamic color",
    description = "Replaces the default Twitter Blue with the user's Material You palette.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_X)

    execute {
        // For backward compatibility, add colors and styles into v31 res dir (A12+).
        val valuesV31Directory = get("res/values-v31")
        val valuesNightV31Directory = get("res/values-night-v31")

        listOf(valuesV31Directory, valuesNightV31Directory).forEach { it ->
            if (!it.isDirectory) Files.createDirectory(it.toPath())

            val colorsXml = it.resolve("colors.xml")
            val stylesXml = it.resolve("styles.xml")

            if (!colorsXml.exists()) {
                FileWriter(colorsXml).use {
                    it.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
                }
            }
            if (!stylesXml.exists()) {
                FileWriter(stylesXml).use {
                    it.write("<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>")
                }
            }
        }

        document("res/values-v31/colors.xml").use { document ->
            val resourcesElement = document.documentElement
            mapOf(
                "ps__twitter_blue" to "@color/twitter_blue",
                "ps__twitter_blue_pressed" to "@color/twitter_blue_fill_pressed",
                "twitter_blue" to "@color/m3_sys_color_dynamic_light_primary",
                "twitter_blue_fill_pressed" to "@color/m3_sys_color_dynamic_light_primary_container",
                "twitter_blue_opacity_30" to "@color/material_dynamic_primary95",
                "twitter_blue_opacity_50" to "@color/material_dynamic_primary90",
                "twitter_blue_opacity_58" to "@color/material_dynamic_primary80",
                "deep_transparent_twitter_blue" to "@color/material_dynamic_primary90",
            ).forEach { (k, v) ->
                val colorElement = document.createElement("color")

                colorElement.setAttribute("name", k)
                colorElement.textContent = v

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
            ).forEach { (k, v) ->
                val colorElement = document.createElement("color")

                colorElement.setAttribute("name", k)
                colorElement.textContent = v

                resourcesElement.appendChild(colorElement)
            }
        }

        // replace dark palettes with user's material 3 colors
        document("res/values-night-v31/styles.xml").use { document ->
            val dimStyle = document.createElement("style")
            dimStyle.setAttribute("name", "PaletteDim")
            dimStyle.setAttribute("parent", "@style/HorizonColorPaletteDark")

            val dimStyleItems = mapOf(
                "abstractColorCellBackground" to "@color/m3_sys_color_dynamic_dark_background",
                "abstractColorCellBackgroundTranslucent" to "@color/m3_sys_color_dynamic_dark_surface_container_low",
                "abstractColorDeepGray" to "@color/m3_sys_color_dynamic_dark_on_surface_variant",
                "abstractColorDivider" to "@color/m3_sys_color_dynamic_dark_outline_variant",
                "abstractColorFadedGray" to "@color/m3_sys_color_dynamic_dark_surface_container_lowest",
                "abstractColorFaintGray" to "@color/m3_sys_color_dynamic_dark_surface_container_low",
                "abstractColorHighlightBackground" to "@color/m3_sys_color_dynamic_dark_surface_container",
                "abstractColorLightGray" to "@color/m3_sys_color_dynamic_dark_outline_variant",
                "abstractColorLink" to "@color/twitter_blue",
                "abstractColorMediumGray" to "@color/m3_sys_color_dynamic_dark_outline",
                "abstractColorText" to "@color/m3_sys_color_dynamic_dark_on_surface",
                "abstractColorUnread" to "@color/m3_sys_color_dynamic_dark_primary_container",
                "abstractElevatedBackground" to "@color/m3_sys_color_dynamic_dark_surface_container_high",
                "abstractElevatedBackgroundShadow" to "@color/black_opacity_10"
            )

            dimStyleItems.forEach { (k, v) ->
                val styleElement = document.createElement("item")

                styleElement.setAttribute("name", k)
                styleElement.textContent = v
                dimStyle.appendChild(styleElement)
            }

            document.documentElement.appendChild(dimStyle)

            val lightsOutStyle = document.createElement("style")
            lightsOutStyle.setAttribute("name", "PaletteLightsOut")
            lightsOutStyle.setAttribute("parent", "@style/HorizonColorPaletteDark")

            val lightsOutStyleItems = mapOf(
                "abstractColorCellBackground" to "@color/black",
                "abstractColorCellBackgroundTranslucent" to "@color/black_opacity_50",
                "abstractColorDeepGray" to "@color/m3_sys_color_dynamic_dark_on_surface_variant",
                "abstractColorDivider" to "@color/m3_sys_color_dynamic_dark_outline_variant",
                "abstractColorFadedGray" to "@color/m3_sys_color_dynamic_dark_surface_container_low",
                "abstractColorFaintGray" to "@color/m3_sys_color_dynamic_dark_surface_container_lowest",
                "abstractColorHighlightBackground" to "@color/m3_sys_color_dynamic_dark_surface_container_low",
                "abstractColorLightGray" to "@color/m3_sys_color_dynamic_dark_outline_variant",
                "abstractColorLink" to "@color/twitter_blue",
                "abstractColorMediumGray" to "@color/m3_sys_color_dynamic_dark_outline",
                "abstractColorText" to "@color/m3_sys_color_dynamic_dark_on_surface",
                "abstractColorUnread" to "@color/m3_sys_color_dynamic_dark_primary_container",
                "abstractElevatedBackground" to "@color/m3_sys_color_dynamic_dark_surface_container",
                "abstractElevatedBackgroundShadow" to "@color/black_opacity_10"
            )

            lightsOutStyleItems.forEach { (k, v) ->
                val styleElement = document.createElement("item")

                styleElement.setAttribute("name", k)
                styleElement.textContent = v
                lightsOutStyle.appendChild(styleElement)
            }

            document.documentElement.appendChild(lightsOutStyle)
        }

        // monet theme in light mode
        document("res/values-v31/styles.xml").use { document ->
            val newStyle = document.createElement("style")
            newStyle.setAttribute("name", "PaletteStandard")
            newStyle.setAttribute("parent", "@style/HorizonColorPaletteLight")

            val styleItems = mapOf(
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
                "abstractElevatedBackgroundShadow" to "@color/black_opacity_10"
            )

            styleItems.forEach { (k, v) ->
                val styleElement = document.createElement("item")

                styleElement.setAttribute("name", k)
                styleElement.textContent = v
                newStyle.appendChild(styleElement)
            }

            document.documentElement.appendChild(newStyle)
        }
    }
}