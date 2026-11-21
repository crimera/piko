/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * Copyright 2026 Hushfeed contributors <https://github.com/SysAdminDoc/hushfeed>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.applock

import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.EXTENSION_PACKAGE
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.settings.choice
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.settings.singleChoice
import app.crimera.patches.settings.toggle
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.registersUsed
import app.morphe.util.asSequence
import app.morphe.util.getNode
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import org.w3c.dom.Element

private const val APP_LOCK_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/AppLock;"
private const val USE_BIOMETRIC = "android.permission.USE_BIOMETRIC"

// BiometricPrompt throws without this permission, and nothing guarantees a build declares it.
private val newXAppLockResourcePatch =
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
val newXAppLockPatch =
    bytecodePatch(
        name = "NewX: App lock",
        description = "Adds an option to ask for your fingerprint, face or screen lock when the app opens and when you return to it.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")
        dependsOn(newXExtensionPatch, newXAppLockResourcePatch)

        newXSettings {
            category(Categories.ADVANCED) {
                toggle(
                    id = "newx.advanced.app_lock",
                    strings = settingStrings("piko_newx_app_lock"),
                    order = 50,
                    defaultValue = false,
                )
                singleChoice(
                    id = "newx.advanced.app_lock_timeout",
                    strings = settingStrings("piko_newx_app_lock_timeout", summary = false),
                    order = 60,
                    defaultValue = "0",
                    options =
                        listOf(
                            choice("0", "piko_newx_app_lock_timeout_immediately"),
                            choice("1", "piko_newx_app_lock_timeout_1"),
                            choice("5", "piko_newx_app_lock_timeout_5"),
                            choice("15", "piko_newx_app_lock_timeout_15"),
                        ),
                )
            }
        }

        execute {
            newXInitHook.fingerprint.method.apply {
                val superIndex = indexOfFirstInstruction(Opcode.INVOKE_SUPER)
                val applicationRegister = getInstruction(superIndex).registersUsed[0]

                insertHook(
                    index = superIndex + 1,
                    relocateBranchTargets = false,
                ) {
                    invokeStatic(
                        methodReference("$APP_LOCK_DESCRIPTOR->install(Landroid/app/Application;)V"),
                        applicationRegister,
                    )
                }
            }
        }
    }
