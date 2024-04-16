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
            prefMod.setAttribute("android:title", "@string/piko_title_settings")
            prefMod.setAttribute("android:key", "pref_mod")
            prefMod.setAttribute("android:order", "110")

            parent.appendChild(prefMod)
        }

        context.xmlEditor["AndroidManifest.xml"].use {
            val applicationNode = it.file.getElementsByTagName("application").item(0)

            val modActivity = it.file.createElement("activity").apply {
                setAttribute("android:label", "@strings/piko_title_settings")
                setAttribute("android:name", "app.revanced.integrations.twitter.settings.SettingsActivity")
                setAttribute("android:excludeFromRecents", "true")
            }
            applicationNode.appendChild(modActivity)

            val bkActivity = it.file.createElement("activity").apply {
                setAttribute("android:label", "@strings/piko_pref_export")
                setAttribute("android:name", "app.revanced.integrations.twitter.settings.BackupPrefActivity")
                setAttribute("android:excludeFromRecents", "true")
            }
            applicationNode.appendChild(bkActivity)

            val resActivity = it.file.createElement("activity").apply {
                setAttribute("android:label", "@strings/piko_pref_import")
                setAttribute("android:name", "app.revanced.integrations.twitter.settings.RestorePrefActivity")
                setAttribute("android:excludeFromRecents", "true")
            }
            applicationNode.appendChild(resActivity)
        }

        //credits @inotia00
        context.copyXmlNode("twitter/settings", "values/strings.xml", "resources")

        /**
         * create directory for the untranslated language resources
         */
        val languages = arrayOf(
            "ar",
            "jp",
            "hi",
            "in",
            "zh-rCN",
            "ru",
            "pl",
            "pt-rBR",
            "v21"
            "tr"
        ).map { "values-$it" }

        languages.forEach {
            if (context["res/$it"].exists()) {
                context.copyXmlNode("twitter/settings", "$it/strings.xml", "resources")
            } else {
                context["res/$it"].mkdirs()
                context.copyResources("twitter/settings", ResourceGroup(it, "strings.xml"))
            }
        }
    }
}
