/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.developerOption

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val unlockDeveloperOptionPatch =
    bytecodePatch(
        name = "Unlock developer options",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            fun checkAndHookDeveloperOptions(developerOptionCallInstruction: BuilderInstruction): Boolean {
                val methodRef = developerOptionCallInstruction.getReference<MethodReference>()
                    ?: return false

                // In v423+, the employee-check method moved into a multi-method utility class
                // (LX/5dV). The old heuristic required the class to have exactly 1 method, which
                // no longer holds. Instead, validate the method signature: it must return boolean
                // and accept a UserSession parameter, which is the stable pattern for the
                // developer-options gate across Instagram versions.
                if (methodRef.returnType != "Z") return false
                if (methodRef.parameterTypes.none { it == "Lcom/instagram/common/session/UserSession;" }) return false

                val devOptionsClass = try {
                    mutableClassDefBy(methodRef.definingClass)
                } catch (_: Exception) {
                    return false
                }

                val devOptionsMethod = devOptionsClass.methods
                    .firstOrNull { it.name == methodRef.name }
                    ?: return false

                devOptionsMethod.addInstructions(
                    0,
                    """
                    ${PREF_CALL_DESCRIPTOR}->enableDevOptions()Z
                    move-result v0
                    return v0
                    """.trimIndent(),
                )

                return true
            }

            var isDeveloperOptionUnlocked = false

            val fingerprints =
                listOf(
                    AREffectsDebugViewRelatedFingerprint,
                    ChromeTraceRelatedFingerprint,
                    MessageInputMethodRelatedFingerprint,
                )

            for (fingerprint in fingerprints) {
                val strIndex = fingerprint.stringMatches[0].index
                val method = fingerprint.method
                val invokeStatic =
                    method.instructions.lastOrNull {
                        it.opcode == Opcode.INVOKE_STATIC && it.location.index < strIndex
                    } ?: continue

                isDeveloperOptionUnlocked = checkAndHookDeveloperOptions(invokeStatic)

                if (isDeveloperOptionUnlocked) {
                    break
                }
            }

            if (isDeveloperOptionUnlocked) {
                enableSettings("enableDeveloperOptions")
            } else {
                throw PatchException(
                    "Failed to match fingerprints for Enable developer options. " +
                        "The employee-check method signature may have changed in this Instagram version.",
                )
            }
        }
    }
