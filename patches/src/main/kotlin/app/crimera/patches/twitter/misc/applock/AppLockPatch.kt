/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * Copyright 2026 Hushfeed contributors <https://github.com/SysAdminDoc/hushfeed>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.applock

import app.crimera.patches.twitter.misc.extension.twitterInitHook
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.asSequence
import app.morphe.util.getNode
import org.w3c.dom.Element

private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/AppLock;"
private const val USE_BIOMETRIC = "android.permission.USE_BIOMETRIC"

// BiometricPrompt throws without this permission, and nothing guarantees a build declares it.
private val appLockResourcePatch =
    resourcePatch {
        execute {
            document("AndroidManifest.xml").use { document ->
                val declared =
                    document.getElementsByTagName("uses-permission").asSequence().any {
                        (it as Element).getAttribute("android:name") == USE_BIOMETRIC
                    }
                if (!declared) {
                    val permission = document.createElement("uses-permission")
                    permission.setAttribute("android:name", USE_BIOMETRIC)
                    document.getNode("manifest").appendChild(permission)
                }
            }
        }
    }

@Suppress("unused")
val appLockPatch =
    bytecodePatch(
        name = "App lock",
        description = "Adds an option to ask for your fingerprint, face or screen lock when the app opens and when you return to it",
    ) {
        compatibleWith(COMPATIBILITY_X)

        dependsOn(settingsPatch, appLockResourcePatch)

        execute {
            // A range invoke, so the parameter register's number never has to fit a 4-bit operand.
            twitterInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->install(Landroid/app/Application;)V",
            )

            enableSettings("appLock")
        }
    }
