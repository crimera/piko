package crimera.patches.twitter.misc.dynamiccolor

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.resourcePatch
import java.io.FileWriter
import java.nio.file.Files

@Suppress("unused")
val dynamicColorPatch = resourcePatch (
    name = "Dynamic color",
    description = "Replaces the default Blue accent with the user's Material You palette and Dim, Light Theme with Full Material Design.",
) {
    compatibleWith("com.twitter.android")

    execute { context ->
        val resDirectory = context["res"]
        if (!resDirectory.isDirectory) throw PatchException("The res folder can not be found.")

        val valuesV31Directory = resDirectory.resolve("values-v31")
        if (!valuesV31Directory.isDirectory) Files.createDirectories(valuesV31Directory.toPath())

        val valuesNightV31Directory = resDirectory.resolve("values-night-v31")
        if (!valuesNightV31Directory.isDirectory) Files.createDirectories(valuesNightV31Directory.toPath())

        listOf(valuesV31Directory, valuesNightV31Directory).forEach { it ->
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

        context.document["res/values-v31/colors.xml"].use { document ->
            mapOf(
                "ps__twitter_blue" to "@color/twitter_blue",
                "ps__twitter_blue_pressed" to "@color/twitter_blue_fill_pressed",
                "twitter_blue" to "@android:color/system_accent1_500",
                "twitter_blue_fill_pressed" to "@android:color/system_accent1_300",
                "twitter_blue_opacity_30" to "@android:color/system_accent1_100",
                "twitter_blue_opacity_50" to "@android:color/system_accent1_200",
                "twitter_blue_opacity_58" to "@android:color/system_accent1_300",
                "deep_transparent_twitter_blue" to "@android:color/system_accent1_200",
            ).forEach { (k, v) ->
                val colorElement = document.createElement("color")

                colorElement.setAttribute("name", k)
                colorElement.textContent = v

                document.getElementsByTagName("resources").item(0).appendChild(colorElement)
            }
        }

        context.document["res/values-night-v31/colors.xml"].use { document ->
            mapOf(
                "twitter_blue" to "@android:color/system_accent1_200",
                "twitter_blue_fill_pressed" to "@android:color/system_accent1_300",
                "twitter_blue_opacity_30" to "@android:color/system_accent1_50",
                "twitter_blue_opacity_50" to "@android:color/system_accent1_100",
                "twitter_blue_opacity_58" to "@android:color/system_accent1_200",
                "deep_transparent_twitter_blue" to "@android:color/system_accent1_200",
            ).forEach { (k, v) ->
                val colorElement = document.createElement("color")

                colorElement.setAttribute("name", k)
                colorElement.textContent = v

                document.getElementsByTagName("resources").item(0).appendChild(colorElement)
            }
        }

        /** fun fullMaterialDesign() { **/
        // backward compatible, creates style into v31 res dir (A12+)
        // replace parts of DimTheme with user's material 3 neutral palette
        context.document["res/values-night-v31/styles.xml"].use { editor ->
            val newStyle = editor.createElement("style")
            newStyle.setAttribute("name", "PaletteDim")
            newStyle.setAttribute("parent", "@style/HorizonColorPaletteDark")

            val styleItems = mapOf(
                "abstractColorCellBackground" to "@color/material_dynamic_neutral10",
                "abstractColorCellBackgroundTranslucent" to "@color/material_dynamic_neutral10",
                "abstractColorDeepGray" to "#ff8899a6",
                "abstractColorDivider" to "#ff38444d",
                "abstractColorFadedGray" to "@color/material_dynamic_neutral10",
                "abstractColorFaintGray" to "@color/material_dynamic_neutral10",
                "abstractColorHighlightBackground" to "@color/material_dynamic_neutral20",
                "abstractColorLightGray" to "#ff3d5466",
                "abstractColorLink" to "@color/twitter_blue",
                "abstractColorMediumGray" to "#ff6b7d8c",
                "abstractColorText" to "@color/white",
                "abstractColorUnread" to "#ff163043",
                "abstractElevatedBackground" to "#ff1c2c3c",
                "abstractElevatedBackgroundShadow" to "#1a15202b"
            )

            styleItems.forEach { (k, v) ->
                val styleElement = editor.createElement("item")

                styleElement.setAttribute("name", k)
                styleElement.textContent = v
                newStyle.appendChild(styleElement)
            }

            editor.getElementsByTagName("resources").item(0).appendChild(newStyle)
        }

        // monet theme in light mode
        context.document["res/values-v31/styles.xml"].use { editor ->
            val newStyle = editor.createElement("style")
            newStyle.setAttribute("name", "PaletteStandard")
            newStyle.setAttribute("parent", "@style/HorizonColorPaletteLight")

            val styleItems = mapOf(
                "abstractColorCellBackground" to "@android:color/system_neutral2_50",
                "abstractColorCellBackgroundTranslucent" to "@android:color/system_neutral2_50",
                "abstractColorDeepGray" to "@color/gray_1000",
                "abstractColorDivider" to "@android:color/system_accent1_400",
                "abstractColorFadedGray" to "@color/material_dynamic_primary99",
                "abstractColorFaintGray" to "@color/material_dynamic_primary99",
                "abstractColorHighlightBackground" to "@android:color/system_accent2_100",
                "abstractColorLightGray" to "@color/gray_1100",
                "abstractColorLink" to "@color/twitter_blue",
                "abstractColorMediumGray" to "@color/gray_1100",
                "abstractColorText" to "#0d1115",
                "abstractColorUnread" to "@color/blue_0",
                "abstractElevatedBackground" to "@color/white",
                "abstractElevatedBackgroundShadow" to "@color/black_opacity_10"
            )

            styleItems.forEach { (k, v) ->
                val styleElement = editor.createElement("item")

                styleElement.setAttribute("name", k)
                styleElement.textContent = v
                newStyle.appendChild(styleElement)
            }

            editor.getElementsByTagName("resources").item(0).appendChild(newStyle)
        }
    }
}