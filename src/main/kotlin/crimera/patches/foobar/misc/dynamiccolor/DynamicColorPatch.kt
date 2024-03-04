package crimera.patches.foobar.misc.dynamiccolor

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import java.io.FileWriter
import java.nio.file.Files

@Patch(
    name = "Dynamic color",
    description = "Adds material you theming",
    compatiblePackages = [CompatiblePackage("com.foobar2000.foobar2000")]
)
object DynamicColorPatch : ResourcePatch() {
    override fun execute(context: ResourceContext) {
        val valuesV31Directory = context["res"].resolve("values-v31")
        if (!valuesV31Directory.isDirectory) Files.createDirectories(valuesV31Directory.toPath())

        val stylesXml = valuesV31Directory.resolve("styles.xml")

        if (!stylesXml.exists()) {
            FileWriter(stylesXml).use {
                it.write(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                        <style name="AppThemeBlack" parent="@android:style/Theme.Material">
                        </style>
                    </resources>
                """.trimIndent()
                )
            }
        }

        context.xmlEditor["res/values-v31/styles.xml"].use { editor ->
            val document = editor.file

            mapOf(
                "android:windowNoTitle" to "true",
                "android:windowActionBar" to "false",
                "android:colorPrimaryDark" to "@android:color/system_neutral1_900",
                "android:windowBackground" to "@android:color/system_neutral1_900",
                "android:colorAccent" to "@android:color/system_accent1_200",
                "android:textColorPrimary" to "@android:color/system_accent1_10",
                "android:textColorSecondary" to "@android:color/system_accent1_10",
                "android:textColorTertiary" to "@android:color/system_accent1_10"
            ).forEach { (k, v) ->
                val itemElement = document.createElement("item")

                itemElement.setAttribute("name", k)
                itemElement.textContent = v

                document.getElementsByTagName("style").item(0).appendChild(itemElement)
            }
        }
    }
}