package crimera.patches.twitter.misc.settings

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.ResourceGroup
import app.revanced.util.copyResources
import org.w3c.dom.Element
import app.revanced.util.copyXmlNode

@Patch(
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object SettingsResourcePatch: ResourcePatch() {
    override fun execute(context: ResourceContext) {
        val settingsRoot = context["res/xml/settings_root.xml"]
        if (!settingsRoot.exists()) throw PatchException("settings_root not found")

        context.xmlEditor["res/xml/settings_root.xml"].use { editor ->
            val parent = editor.file.getElementsByTagName("PreferenceScreen").item(0) as Element

            val prefMod = editor.file.createElement("Preference")
            prefMod.setAttribute("android:icon", "@drawable/ic_vector_settings_stroke")
            prefMod.setAttribute("android:title", "@string/piko_settings_title")
            prefMod.setAttribute("android:key", "pref_mod")
            prefMod.setAttribute("android:order", "110")

            parent.appendChild(prefMod)
        }

        context.xmlEditor["AndroidManifest.xml"].use {
            val applicationNode = it.file.getElementsByTagName("application").item(0)

            val modActivity = it.file.createElement("activity").apply {
                setAttribute("android:label", "@strings/piko_settings_title")
                setAttribute("android:name", "app.revanced.integrations.twitter.settings.SettingsActivity")
                setAttribute("android:excludeFromRecents", "true")
            }

            applicationNode.appendChild(modActivity)
        }

        //credits @inotia00
        context.copyXmlNode("twitter/settings", "values/strings.xml", "resources")

        /**
         * create directory for the untranslated language resources
         */
        context["res/values-v21"].mkdirs()
        arrayOf(
            ResourceGroup(
                "values-v21",
                "strings.xml"
            )
        ).forEach { resourceGroup ->
            context.copyResources("twitter/settings", resourceGroup)
        }


    }
}