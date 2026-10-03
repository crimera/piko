/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.crimera.patches.newx.misc.clone

import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.util.asSequence
import app.morphe.util.findElementByAttributeValue
import app.morphe.util.findElementByAttributeValueOrThrow
import org.w3c.dom.Element
import org.w3c.dom.NodeList

private const val ORIGINAL_PACKAGE_NAME = "com.twitter.android"

/**
 * Use this only for `getElementsByTagName` (A [NodeList] in which every Node is an instance of [Element]).
 */
private fun NodeList.elementsForEach(action: (Element) -> Unit) = asSequence().map { it as Element }.forEach(action)

@Suppress("unused")
val clonePatch = resourcePatch(
    name = "NewX: Clone",
    description = "Changes the package name and the app name. "
            + "This allows you to install the patched X alongside the original X or other patched X.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_NEW_X)

    val packageName by stringOption(
        key = "packageName",
        default = "com.twitter.android.piko",
        title = "Package name",
        description = "A new package name for the patched app.",
        required = true,
    ) {
        it?.matches(Regex("^[a-z]\\w*(\\.[a-z]\\w*)+$")) == true
    }

    val appName by stringOption(
        key = "appName",
        default = "X Piko",
        title = "App name",
        description = "A new app name (label). Entering \"X\" will skip changing the app name.",
        required = true,
    )

    execute {
        val newPackageName = packageName!!

        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val application = manifest.getElementsByTagName("application").item(0) as Element

            // Change package name
            manifest.setAttribute("package", newPackageName)

            // Change app name
            if (!appName.isNullOrEmpty() && appName != "X") {
                application.setAttribute("android:label", appName)
            }

            // Rename provider authorities
            application.getElementsByTagName("provider").elementsForEach {
                it.setAttribute(
                    "android:authorities",
                    it.getAttribute("android:authorities").replace(ORIGINAL_PACKAGE_NAME, newPackageName)
                )
            }

            // Rename custom permissions
            val permissions = manifest.getElementsByTagName("permission")
            val usesPermissions = manifest.getElementsByTagName("uses-permission")
            val services = application.getElementsByTagName("service")

            permissions.elementsForEach {
                val oldName = it.getAttribute("android:name")
                val newName = oldName.replace(ORIGINAL_PACKAGE_NAME, newPackageName)
                it.setAttribute("android:name", newName)

                // Rename the corresponding uses-permission
                usesPermissions.findElementByAttributeValue("android:name", oldName)
                    ?.setAttribute("android:name", newName)

                // Rename the corresponding android:permission attribute for components
                // As of now, custom permissions apply to only one service.
                // (TwitterAuthenticationService has "com.twitter.android.auth.login.ACCESS" permission)
                // Therefore, we will only check the services.
                services.elementsForEach { service ->
                    if (service.getAttribute("android:permission") == oldName) {
                        service.setAttribute("android:permission", newName)
                    }
                }
            }
        }

        // Rename account type
        document("res/values/strings.xml").use { document ->
            val element = document.documentElement.childNodes.findElementByAttributeValueOrThrow("name", "account_type")
            element.textContent = element.textContent.replace(ORIGINAL_PACKAGE_NAME, newPackageName)
        }
    }
}
