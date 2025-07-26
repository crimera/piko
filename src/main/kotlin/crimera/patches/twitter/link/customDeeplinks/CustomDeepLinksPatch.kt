package crimera.patches.twitter.link.customDeeplinks

import app.revanced.patcher.data.ResourceContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.patch.options.PatchOption
import app.revanced.patcher.patch.options.PatchOption.PatchExtensions.stringArrayPatchOption
import app.revanced.util.asSequence

@Patch(
    name = "Handle custom twitter links",
    description = "Adds support for opening custom twitter links such as vxtwitter, fxtwitter, fixupx, etc. within the app" +
            "These will have to be manually enabled under the \"Verified Links\" section of the patched app's system settings!",
    use = true,
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [HandleCustomDeepLinksPatch::class],
)
@Suppress("unused")
object CustomDeepLinksPatch : ResourcePatch() {
    init {
        stringArrayPatchOption(
            key = "customLinkHosts",
            title = "Custom sharing domains",
            description = "The custom sharing domains to register to be opened in Twitter",
            required = true,
            default = arrayOf( // @formatter:off
                // Some of these are absolutely unhinged, but it's all the more reason to skip opening it in a browser
                "vxtwitter.com", "m.vxtwitter.com", "g.vxtwitter.com", "t.vxtwitter.com", "o.vxtwitter.com", "mobile.vxtwitter.com",
                "fixvx.com", "m.fixvx.com", "g.fixvx.com", "t.fixvx.com", "o.fixvx.com", "mobile.fixvx.com",
                "fxtwitter.com", "m.fxtwitter.com", "g.fxtwitter.com", "t.fxtwitter.com", "o.fxtwitter.com",
                "fixupx.com", "m.fxtwitter.com", "g.fxtwitter.com", "t.fxtwitter.com", "o.fixupx.com",
                "twittpr.com", "m.twittpr.com", "g.twittpr.com", "t.twittpr.com",
                "hitlerx.com", "m.hitlerx.com", "g.hitlerx.com", "t.hitlerx.com", "o.hitlerx.com",
                "girlcockx.com", "m.girlcockx.com", "g.girlcockx.com", "t.girlcockx.com",
                "stupidpenisx.com",
                "cunnyx.com",
                "autistic.kids",
            ), // @formatter:on
        )
    }

    override fun execute(context: ResourceContext) {
        val customLinkHosts: PatchOption<Array<String>> by options

        context.xmlEditor["AndroidManifest.xml"].use { editor ->
            val document = editor.file
            val activities = document.getElementsByTagName("activity")

            val deeplinkActivity = activities.asSequence()
                .find {
                    val name = it.attributes.getNamedItem("android:name").nodeValue
                    name == "com.twitter.deeplink.implementation.UrlInterpreterActivity"
                }
                ?: throw PatchException("Unable to find UrlInterpreterActivity in AndroidManifest")

            val newIntentFilter = document.createElement("intent-filter").apply {
                document.createElement("action")
                    .apply { setAttribute("android:name", "android.intent.action.VIEW") }
                    .let(::appendChild)
                document.createElement("category")
                    .apply { setAttribute("android:name", "android.intent.category.DEFAULT") }
                    .let(::appendChild)
                document.createElement("category")
                    .apply { setAttribute("android:name", "android.intent.category.BROWSABLE") }
                    .let(::appendChild)
                document.createElement("data")
                    .apply { setAttribute("android:scheme", "http") }
                    .let(::appendChild)
                document.createElement("data")
                    .apply { setAttribute("android:scheme", "https") }
                    .let(::appendChild)
                document.createElement("data")
                    .apply { setAttribute("android:pathPattern", "/.*") }
                    .let(::appendChild)

                for (customHost in customLinkHosts.value!!) {
                    document.createElement("data")
                        .apply { setAttribute("android:host", customHost) }
                        .let(::appendChild)
                }
            }

            deeplinkActivity.appendChild(newIntentFilter)
        }

        context.xmlEditor["res/values/arrays.xml"].use { editor ->
            val document = editor.file
            val resources = document.getElementsByTagName("resources").asSequence().single()

            val array = document.createElement("string-array").apply {
                setAttribute("name", "piko_custom_deeplink_hosts")
                for (customHost in customLinkHosts.value!!) {
                    document.createElement("item")
                        .apply { textContent = customHost }
                        .let(::appendChild)
                }
            }

            resources.appendChild(array)
        }
    }
}
