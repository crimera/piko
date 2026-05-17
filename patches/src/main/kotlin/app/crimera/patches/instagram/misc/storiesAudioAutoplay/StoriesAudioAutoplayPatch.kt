/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.storiesAudioAutoplay

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

internal object ToggleStorySoundFromVolumeButtonMethodFingerprint : Fingerprint(
    parameters = listOf("Z", "I"),
    strings = listOf("audio state did not match"),
    returnType = "V",
)

// Credits to MyInsta
@Suppress("unused")
val storiesAudioAutoplayPatch =
    bytecodePatch(
        name = "Stories audio autoplay",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val desiredMethods =
                ToggleStorySoundFromVolumeButtonMethodFingerprint.classDef.methods.filter {
                    it.parameters.isEmpty() && it.returnType == "Z"
                }

            desiredMethods[2].apply {
                addInstructionsWithLabels(
                    0,
                    """
                    $PREF_CALL_DESCRIPTOR->storiesAudioAutoplay()Z
                    move-result v0
                    if-eqz v0, :piko
                    return v0
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
                enableSettings("storiesAudioAutoplay")
            }
        }
    }
