package app.revanced.patches.twitter.misc.dynamiccolor

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import java.io.FileWriter
import java.nio.file.Files

@Patch(
    name = "Dynamic color - Monet",
    description = "Replaces the default Blue accent with the user's Material You palette and Dim Theme with Full Material Design.",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
@Suppress("unused")
object DynamicColorPatch : ResourcePatch() {
    override fun execute(context: ResourceContext) {
        // directories
        val resDirectory = context["res"]
        val valuesV31Directory = resDirectory.resolve("values-v31")
        val valuesNightV31Directory = resDirectory.resolve("values-night-v31")
        val colorsXml = resDirectory.resolve("colors.xml")
        val colorsDirectory = "res/values-v31/colors.xml"
        val colorsNightV31Directory = "res/values-night-v31/colors.xml"
        val stylesXml = resDirectory.resolve("styles.xml")
        val stylesNightV31Directory = "res/values-night-v31/styles.xml"

        when {
            !resDirectory.isDirectory -> throw PatchException("The res folder can not be found.")
            !valuesV31Directory.isDirectory -> Files.createDirectories(valuesV31Directory.toPath())
            !valuesNightV31Directory.isDirectory -> Files.createDirectories(
                valuesNightV31Directory.toPath()
            )
            !stylesXml.exists() -> FileWriter(stylesXml).write(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>"
            )
            !colorsXml.exists() -> listOf(valuesV31Directory, valuesNightV31Directory).forEach { _ ->
                FileWriter(colorsXml).write(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><resources></resources>"
                )
            }
        }

        context.xmlEditor[colorsDirectory].use { editor ->
            val document = editor.file

            mapOf(
                "ps__twitter_blue" to "@color/twitter_blue",
                "ps__twitter_blue_pressed" to "@color/twitter_blue_fill_pressed",
                "twitter_blue" to "@android:color/system_accent1_400",
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

        context.xmlEditor[colorsNightV31Directory].use { editor ->
            val document = editor.file

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
        context.xmlEditor[stylesNightV31Directory].use { editor ->
            val document = editor.file

            val newStyle = document.createElement("style")
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
                val styleElement = document.createElement("item")

                styleElement.setAttribute("name", k)
                styleElement.textContent = v
                newStyle.appendChild(styleElement)
            }

            document.getElementsByTagName("resources").item(0).appendChild(newStyle)
        }
    }
}
