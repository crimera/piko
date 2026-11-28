/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.crimera.patches.newx.misc.clone

import app.crimera.patches.common.scopedMatchAll
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.string
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val ORIGINAL_PACKAGE_NAME = "com.twitter.android"

/**
 * A fingerprint for intent.setPackage("com.twitter.android") occurrences.
 * Use matchAll().
 */
private object NavigationIntentSetPackageFingerprint : Fingerprint(
    definingClass = "Lcom/x/navigation/",
    filters = listOf(string(ORIGINAL_PACKAGE_NAME)),
)

/**
 * This method checks for billing availability, such as whether the package name is "com.twitter.android".
 * If this method returns false, X Premium and the subscriptions will be disabled.
 * That sounds desirable, but it's outside the scope of this patch, so we are fixing this "issue".
 */
private object BillingAvailabilityFingerprint : Fingerprint(
    definingClass = "Lcom/x/subscriptions/",
    returnType = "Z",
    filters = listOf(string(ORIGINAL_PACKAGE_NAME)),
)

@Suppress("unused")
val clonePatch = patch(
    name = "NewX: Clone",
    description = "Changes the package name and the app name. "
            + "This allows you to install the patched X alongside the original X or other patched X.",
    default = false,
) {
    unsupportedOnMount(default = false)
    compatibleWith(COMPATIBILITY_NEW_X)
    category("NewX patches")

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

        resourceXml("AndroidManifest.xml").use { document ->
            val manifest = document.root
            val application = manifest.elements("application").first()

            // Change package name
            manifest.setAttribute("package", newPackageName)

            // Change app name
            if (!appName.isNullOrEmpty() && appName != "X") {
                application.setAttribute("android:label", appName!!)
            }

            // Rename provider authorities
            application.elements("provider").forEach {
                it.setAttribute(
                    "android:authorities",
                    it.attribute("android:authorities").orEmpty().replace(ORIGINAL_PACKAGE_NAME, newPackageName)
                )
            }

            // Rename custom permissions
            val permissions = manifest.elements("permission").toList()
            val usesPermissions = manifest.elements("uses-permission").toList()
            val services = application.elements("service").toList()

            permissions.forEach {
                val oldName = it.attribute("android:name").orEmpty()
                val newName = oldName.replace(ORIGINAL_PACKAGE_NAME, newPackageName)
                it.setAttribute("android:name", newName)

                // Rename the corresponding uses-permission
                usesPermissions.firstOrNull { usesPermission -> usesPermission.attribute("android:name") == oldName }
                    ?.setAttribute("android:name", newName)

                // Rename the corresponding android:permission attribute for components
                // As of now, custom permissions apply to only one service.
                // (TwitterAuthenticationService has "com.twitter.android.auth.login.ACCESS" permission)
                // Therefore, we will only check the services.
                services.forEach { service ->
                    if (service.attribute("android:permission") == oldName) {
                        service.setAttribute("android:permission", newName)
                    }
                }
            }

            application.elements("meta-data")
                .firstOrNull { it.attribute("android:name") == "android.telephony.PROPERTY_SATELLITE_DATA_OPTIMIZED" }
                ?.setAttribute("android:value", newPackageName)
        }

        // Rename account type
        resourceXml("res/values/strings.xml").use { strings ->
            val element = strings.resource("account_type")
            element.text = element.text.replace(ORIGINAL_PACKAGE_NAME, newPackageName)
        }

        // Change package names for launcher icon shortcuts
        resourceXml("res/xml/shortcuts.xml").use { shortcuts ->
            shortcuts.elements("intent").forEach {
                if (it.attribute("android:targetPackage") == ORIGINAL_PACKAGE_NAME) {
                    it.setAttribute("android:targetPackage", newPackageName)
                }
            }
        }

        // Change some of the occurrences of hard-coded package name in bytecode
        // Fix navigation and X Premium and subscriptions
        (NavigationIntentSetPackageFingerprint.scopedMatchAll() + BillingAvailabilityFingerprint.match()).forEach { match ->
            match.method.apply {
                val index = match.instructionMatches.first().index
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                replaceInstruction(
                    index,
                    """
                        const-string v$register, "$newPackageName"
                    """,
                )
            }
        }

        // Fix issues such as [Settings > Accessibility, display, and languages > App language] doesn't open
        string("package:$ORIGINAL_PACKAGE_NAME").matchAllMethodIndicesForEach { index ->
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            replaceInstruction(
                index,
                """
                    const-string v$register, "package:$newPackageName"
                """,
            )
        }
    }
}
