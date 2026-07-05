/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.unlockPlusBenefits

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

internal object ActiveBenefitCheckerFingerprint : Fingerprint(
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Z",
    strings = listOf("is_benefit_active"),
)

@Suppress("unused")
val unlockPlusBenefitsPatch =
    bytecodePatch(
        name = "Unlock Plus benefits",
        description = "Unlocks 'Plus' subscription benefits that are checked locally. USE IT AT YOUR OWN RISK",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            ActiveBenefitCheckerFingerprint.method.apply {

                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR->unlockPlusBenefits()Z
                    move-result v0
                    if-eqz v0, :piko_continue
                    return v0
                    """.trimIndent(),
                    ExternalLabel("piko_continue", getInstruction(0)),
                )

                enableSettings("unlockPlusBenefits")
            }
        }
    }
