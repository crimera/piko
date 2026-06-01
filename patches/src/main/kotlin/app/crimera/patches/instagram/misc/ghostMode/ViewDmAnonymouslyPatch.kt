/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.misc.actionBar.dmActionBarButton.dmActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider

@Suppress("unused")
val viewDmAnonymouslyPatch =
    bytecodePatch(
        name = "View DMs anonymously",
    ) {
        dependsOn(settingsPatch, dmActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            DMSeenFingerprint.method.apply {
                val shouldDisableRegister =
                    getFreeRegisterProvider(
                        index = 1,
                        numberOfFreeRegistersNeeded = 1,
                    ).getFreeRegister()

                addInstructionsWithLabels(
                    1,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR->viewDmAnonymously()Z
                    move-result v$shouldDisableRegister
                    if-eqz v$shouldDisableRegister, :piko_continue
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_continue", getInstruction(1)),
                )
            }
            enableSettings("viewDmAnonymously")
        }
    }
