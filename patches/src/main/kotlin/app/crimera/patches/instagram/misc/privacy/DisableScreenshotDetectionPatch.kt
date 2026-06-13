/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.privacy

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object ScreenshotDetectorFingerprint : Fingerprint(
    strings = listOf("ig_android_story_screenshot_directory", "screenshot_detector"),
)

// Thanks to MyInsta
@Suppress("unused")
val disableScreenshotDetection =
    bytecodePatch(
        name = "Disable screenshot detection",
        description = "Disables screenshots detection in DM",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // Thanks to MyInsta.
            ScreenshotDetectorFingerprint.apply {
                if (stringMatches.isEmpty()) throw PatchException("Failed to find string matches in ScreenshotDetectorFingerprint")
                val strIndex = stringMatches[0].index

                method.apply {
                    val observerStartInvokeInstruction =
                        instructions.lastOrNull {
                            it.location.index < strIndex &&
                                it.opcode == Opcode.INVOKE_VIRTUAL
                        } ?: throw PatchException("Failed to find INVOKE_VIRTUAL before string in ScreenshotDetectorFingerprint")

                    val index = observerStartInvokeInstruction.location.index

                    val nextInstruction = getInstruction(index + 1)
                    val freeRegister = nextInstruction.registersUsed[0]

                    addInstructionsWithLabels(
                        index,
                        """
                        invoke-static {}, $PREF_DESCRIPTOR->disableScreenshotDetection()Z
                        move-result v$freeRegister
                        if-nez v$freeRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", nextInstruction),
                    )

                    enableSettings("disableScreenshotDetection")
                }
            }
        }
    }
