package app.crimera.patches.twitter.link.customDeeplinks

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringsOption
import app.morphe.util.asSequence

@Suppress("unused")
val customDeepLinksPatch = resourcePatch(
    name = "Handle custom twitter links",
    description = "Adds support for opening custom twitter links such as vxtwitter, fxtwitter, and fixupx within the app. " +
            "These will have to be manually enabled under the \"Open by default\" section in the app info!"
) {
    val customLinkHosts by stringsOption(
        key = "customLinkHosts",
        title = "Custom sharing domains",
        description = "The custom sharing domains to register to be opened in Twitter",
        required = true,
        default = listOf(
            // Some of these are absolutely unhinged, but it's all the more reason to skip opening it in a browser
            "vxtwitter.com",
            "fixvx.com",
            "fxtwitter.com",
            "fixupx.com",
            "twittpr.com",
            "hitlerx.com",
            "girlcockx.com",
            "stupidpenisx.com",
            "cunnyx.com",
            "autistic.kids",
        ),
    )

    compatibleWith("com.twitter.android")

    dependsOn(handleCustomDeepLinksPatch)

    execute {
        document("AndroidManifest.xml").use { document ->
            val deeplinkActivity = document.getElementsByTagName("activity").asSequence()
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
                    .apply { setAttribute("android:pathPattern", "/..*") }
                    .let(::appendChild)

                for (customHost in customLinkHosts!!) {
                    document.createElement("data")
                        .apply { setAttribute("android:host", customHost) }
                        .let(::appendChild)
                    document.createElement("data")
                        .apply { setAttribute("android:host", "*.$customHost") }
                        .let(::appendChild)
                }
            }

            deeplinkActivity.appendChild(newIntentFilter)
        }

        document("res/values/arrays.xml").use { document ->
            val array = document.createElement("string-array").apply {
                setAttribute("name", "piko_custom_deeplink_hosts")
                for (customHost in customLinkHosts!!) {
                    document.createElement("item")
                        .apply { textContent = customHost }
                        .let(::appendChild)
                }
            }

            document.documentElement.appendChild(array)
        }
    }
}
