/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.flagsecure

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

internal object FlagSecureManagerFingerprint : Fingerprint(
    strings = listOf("Inconsistency in window FLAG_SECURE state detected!"),
)

@Suppress("unused")
val disableFlagSecurePatch =
    bytecodePatch(
        name = "Disable FLAG_SECURE",
        description = "Allows screenshots and screen recording on protected screens like DMs and view-once media.",
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val bypassSmali = """
                ${Constants.PREF_CALL_DESCRIPTOR}->disableFlagSecure()Z
                move-result v0
                if-eqz v0, :original
                return-void
            """.trimIndent()

            // Neuter the central FLAG_SECURE manager's consistency-check method.
            // This class uses reference counting via WeakHashMap to track which components
            // requested FLAG_SECURE. It re-applies the flag if it detects a mismatch.
            FlagSecureManagerFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    bypassSmali,
                    ExternalLabel("original", getInstruction(0)),
                )
            }

            // Neuter the set and remove methods in the same manager class.
            // These are the (Window, String) methods that add/remove FLAG_SECURE
            // references for the reference-counting system.
            FlagSecureManagerFingerprint.classDef.methods
                .filter { method ->
                    method != FlagSecureManagerFingerprint.method &&
                        method.returnType == "V" &&
                        method.parameterTypes.isNotEmpty() &&
                        method.parameterTypes[0] == "Landroid/view/Window;" &&
                        method.implementation != null
                }
                .forEach { method ->
                    method.apply {
                        addInstructionsWithLabels(
                            0,
                            bypassSmali,
                            ExternalLabel("original", getInstruction(0)),
                        )
                    }
                }

            enableSettings("disableFlagSecure")
        }
    }
