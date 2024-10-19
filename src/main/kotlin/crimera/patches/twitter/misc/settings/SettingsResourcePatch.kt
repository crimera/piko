package crimera.patches.twitter.misc.settings

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources
import app.revanced.util.copyXmlNode
import org.w3c.dom.Element
import java.io.File

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object SettingsResourcePatch: ResourcePatch() {
    private inline fun measureExecutionTime(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        val end = System.currentTimeMillis()
        return end - start
    }

    private fun fixStrings(file: File) {
        val regex = Regex("""(<string\s+name="conference_default_title">)([^<]*)(<\/string>)""")
        val defaultValue = """"&#120143; Conference"""

        val content = file.readText()
        val matchResult = regex.find(content)
        matchResult?.let {
            val value = it.groups[2]?.value
            println("Matched value in ${file.name}: $value")
        } ?: run {
            println("No match found in ${file.name}.")
        }

        file.writeText(
            content.replace(regex) {
                "${it.groupValues[1]}$defaultValue${it.groupValues[3]}"
            }
        )
    }

    override fun execute(context: ResourceContext) {
        val settingsRoot = context["res/xml/settings_root.xml"]
        if (!settingsRoot.exists()) throw PatchException("settings_root not found")

        context.xmlEditor["res/xml/settings_root.xml"].use { editor ->
            val parent = editor.file.getElementsByTagName("PreferenceScreen").item(0) as Element

            val prefMod = editor.file.createElement("Preference")
            prefMod.setAttribute("android:icon", "@drawable/ic_vector_settings_stroke")
            prefMod.setAttribute("android:title", "@string/piko_title_settings")
            prefMod.setAttribute("android:key", "pref_mod")
            prefMod.setAttribute("android:order", "110")

            parent.appendChild(prefMod)
        }

        //credits aero
        val sideBarLayout = context["res/layout/main_activity_app_bar.xml"]
        if (!sideBarLayout.exists()) throw PatchException("settings_root not found")

        context.xmlEditor["res/layout/main_activity_app_bar.xml"].use { editor ->
            val parent = editor.file.getElementsByTagName("FrameLayout").item(1) as Element

            val sideBtn = editor.file.createElement("app.revanced.integrations.twitter.settings.widgets.PikoSettingsButton")
            sideBtn.setAttribute("android:text", "Piko")
            sideBtn.setAttribute("android:textAllCaps", "false")
            sideBtn.setAttribute("android:background", "?android:attr/selectableItemBackground")
            sideBtn.setAttribute("android:drawableLeft", "@drawable/ic_vector_settings_stroke")
            sideBtn.setAttribute("android:drawableTint", "?attr/coreColorPrimaryText")
            sideBtn.setAttribute("android:layout_height", "@dimen/docker_height")
            sideBtn.setAttribute("android:layout_width", "wrap_content")
            sideBtn.setAttribute("android:padding", "10dp")
            sideBtn.setAttribute("android:layout_marginBottom", "2.3dp")
            sideBtn.setAttribute("android:layout_gravity", "bottom|right")

            parent.appendChild(sideBtn)
        }

        //credits @inotia00
        context.copyXmlNode("twitter/settings", "values/strings.xml", "resources")
        context.copyXmlNode("twitter/settings", "values/arrays.xml", "resources")

        /**
         * create directory for the untranslated language resources
         */
        //Strings
        val languages = arrayOf(
            "es",
            "ar",
            "ja",
            "hi",
            "in",
            "zh-rCN",
            "ru",
            "pl",
            "pt-rBR",
            "v21",
            "tr",
            "zh-rTW"
        ).map { "values-$it" }

        languages.forEach {
            if (context["res/$it"].exists()) {
                context.copyXmlNode("twitter/settings", "$it/strings.xml", "resources")
            } else {
                context["res/$it"].mkdirs()
                context.copyResources("twitter/settings", ResourceGroup(it, "strings.xml"))
            }
        }

        val locales = listOf("values", "values-en-rGB")

        val duration = measureExecutionTime {
            locales.forEach { locale ->
                val stringsFile = context["res/$locale/strings.xml"]
                if (stringsFile.exists()) {
                    println("Processing $locale strings file")
                    fixStrings(stringsFile)
                } else {
                    println("Strings file for $locale not found")
                }
            }
        }

        println(duration)
    }
}
