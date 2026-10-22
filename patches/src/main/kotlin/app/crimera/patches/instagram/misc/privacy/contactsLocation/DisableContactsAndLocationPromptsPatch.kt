/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.privacy.contactsLocation

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Manifest patch to strip contacts and location permissions and disable background location/contact services.
 */
val disableContactsAndLocationManifestPatch =
    resourcePatch(
        name = "Disable contacts and location components",
        description = "Removes contacts and location permissions and disables background location and contact services.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            document("AndroidManifest.xml").use { document ->
                val root = document.documentElement
                val appNode = root.getElementsByTagName("application").item(0) as? Element ?: return@use

                val blockedPermissions =
                    setOf(
                        "android.permission.READ_CONTACTS",
                        "android.permission.WRITE_CONTACTS",
                        "android.permission.GET_ACCOUNTS",
                        "android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.ACCESS_COARSE_LOCATION",
                        "android.permission.ACCESS_BACKGROUND_LOCATION",
                        "android.permission.ACCESS_MEDIA_LOCATION",
                        "android.permission.FOREGROUND_SERVICE_LOCATION",
                    )

                val usesPermissions = root.getElementsByTagName("uses-permission")
                for (i in usesPermissions.length - 1 downTo 0) {
                    val elem = usesPermissions.item(i) as Element
                    val name = elem.getAttribute("android:name")
                    if (name in blockedPermissions) {
                        root.removeChild(elem)
                    }
                }

                val blockedComponents =
                    listOf(
                        "DirectLiveLocationService",
                        "NearbyVenuesService",
                        "LocationChangeReceiver",
                        "CCUJobService",
                        "ContactImportSettingsExternalUrlHandlerActivity",
                        "LocationUrlHandlerActivity",
                    )

                for (tag in listOf("service", "receiver", "activity")) {
                    val nodes = appNode.getElementsByTagName(tag)
                    for (i in nodes.length - 1 downTo 0) {
                        val elem = nodes.item(i) as Element
                        val name = elem.getAttribute("android:name")
                        if (blockedComponents.any { name.contains(it, ignoreCase = true) }) {
                            elem.setAttribute("android:enabled", "false")
                            elem.setAttribute("android:exported", "false")
                            if (elem.hasAttribute("android:process")) {
                                elem.removeAttribute("android:process")
                            }
                            val intentFilters = elem.getElementsByTagName("intent-filter")
                            for (j in intentFilters.length - 1 downTo 0) {
                                elem.removeChild(intentFilters.item(j))
                            }
                        }
                    }
                }
            }
        }
    }

/**
 * Bytecode patch to suppress Contact Sync (CCU) prompts and Location dialogs/upsells:
 * 1. Neutralizes CCU Continuous Contacts Uploader: CCUServiceImpl.onStart -> returns false.
 * 2. Neutralizes CCUPluginImpl.initScheduler -> returns void.
 * 3. Short-circuits isContactsSyncAllowed check: LX/7od;->A00 -> returns false.
 * 4. Short-circuits location permission check: LX/4AJ;->A00 -> returns false.
 * 5. Suppresses NUX and in-app upsell dialog fragments for contacts and location (LX/DOZ;, LX/DPJ;, LX/Dxb;).
 */
@Suppress("unused")
val disableContactsAndLocationPromptsPatch =
    bytecodePatch(
        name = "Disable contacts and location prompts",
        description = "Removes contacts and location permissions, stops CCU contacts syncing, and suppresses popups asking for contact book or location.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(disableContactsAndLocationManifestPatch)

        execute {
            // 1. Suppress CCU start (CCUServiceImpl)
            mutableClassDefByOrNull("Lcom/instagram/contacts/ccu/impl/CCUServiceImpl;")?.let { cls ->
                cls.methods.find { it.implementation != null && it.name == "onStart" }?.addInstructions(
                    0,
                    """
                    const/4 v0, 0x0
                    return v0
                    """.trimIndent(),
                )
            }

            // 2. Suppress CCU scheduler initialization (CCUPluginImpl)
            mutableClassDefByOrNull("Lcom/instagram/contacts/ccu/impl/CCUPluginImpl;")?.let { cls ->
                cls.methods.filter { it.implementation != null && it.name == "initScheduler" }.forEach { method ->
                    method.addInstructions(0, "return-void")
                }
            }

            // 3. Short-circuit isContactsSyncAllowed check (LX/7od)
            mutableClassDefByOrNull("LX/7od;")?.let { cls ->
                cls.methods.find { it.implementation != null && it.name == "A00" }?.addInstructions(
                    0,
                    """
                    const/4 v0, 0x0
                    return v0
                    """.trimIndent(),
                )
            }

            // 4. Short-circuit location permission check (LX/4AJ)
            mutableClassDefByOrNull("LX/4AJ;")?.let { cls ->
                cls.methods.find { it.implementation != null && it.name == "A00" }?.addInstructions(
                    0,
                    """
                    const/4 v0, 0x0
                    return v0
                    """.trimIndent(),
                )
            }

            // 5. Suppress device contacts updater callbacks (DeviceContactsUpdaterAsync)
            mutableClassDefByOrNull("Lcom/facebook/xccu/businesslogic/DeviceContactsUpdaterAsync;")?.let { cls ->
                cls.methods.filter { it.implementation != null && it.name.startsWith("A0") }.forEach { method ->
                    if (method.returnType == "V") {
                        method.addInstructions(0, "return-void")
                    }
                }
            }

            // 6. Suppress contact/location permission prompt fragments (quick_permissions / sac_quick_permissions / location_services)
            listOf("LX/DOZ;", "LX/DPJ;", "LX/Dxb;").forEach { className ->
                mutableClassDefByOrNull(className)?.let { cls ->
                    cls.methods.find { it.implementation != null && it.name == "onResume" }?.addInstructions(
                        0,
                        "return-void",
                    )
                }
            }
        }
    }
