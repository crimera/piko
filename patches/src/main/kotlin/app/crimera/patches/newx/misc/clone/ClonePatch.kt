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
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.string
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.util.asSequence
import app.morphe.util.findElementByAttributeValue
import app.morphe.util.findElementByAttributeValueOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.w3c.dom.Element
import org.w3c.dom.NodeList

private const val ORIGINAL_PACKAGE_NAME = "com.twitter.android"

/**
 * Use this only for `getElementsByTagName` (A [NodeList] in which every Node is an instance of [Element]).
 */
private fun NodeList.elementsForEach(action: (Element) -> Unit) = asSequence().map { it as Element }.forEach(action)

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
val clonePatch = resourcePatch(
    name = "NewX: Clone",
    description = "Changes the package name and the app name. "
            + "This allows you to install the patched X alongside the original X or other patched X.",
    default = false,
) {
    unsupportedOnMount(default = false)
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

    var bytecodePatchContext: BytecodePatchContext? = null

    dependsOn(
        // Expose the bytecode context to use it within a ResourcePatch
        bytecodePatch {
            execute {
                bytecodePatchContext = this
            }
        }
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

            application.getElementsByTagName("meta-data")
                .findElementByAttributeValue("android:name", "android.telephony.PROPERTY_SATELLITE_DATA_OPTIMIZED")
                ?.setAttribute("android:value", newPackageName)
        }

        // Rename account type
        document("res/values/strings.xml").use { document ->
            val element = document.documentElement.childNodes.findElementByAttributeValueOrThrow("name", "account_type")
            element.textContent = element.textContent.replace(ORIGINAL_PACKAGE_NAME, newPackageName)
        }

        // Change package names for launcher icon shortcuts
        document("res/xml/shortcuts.xml").use { document ->
            document.getElementsByTagName("intent").elementsForEach {
                if (it.getAttribute("android:targetPackage") == ORIGINAL_PACKAGE_NAME) {
                    it.setAttribute("android:targetPackage", newPackageName)
                }
            }
        }

        // Change some of the occurrences of hard-coded package name in bytecode
        context(bytecodePatchContext!!) {
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
        bytecodePatchContext = null
    }
}
