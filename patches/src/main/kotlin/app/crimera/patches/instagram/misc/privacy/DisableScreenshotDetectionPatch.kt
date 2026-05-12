/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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
                val strIndex = stringMatches[0].index

                method.apply {
                    val observerStartInvokeInstruction =
                        instructions.last {
                            it.location.index < strIndex &&
                                it.opcode == Opcode.INVOKE_VIRTUAL
                        }

                    val index = observerStartInvokeInstruction.location.index

                    val nextConstInstruction = getInstruction(index + 1)
                    val freeRegister = nextConstInstruction.registersUsed[0]

                    addInstructionsWithLabels(
                        index,
                        """
                        invoke-static {}, $PREF_DESCRIPTOR->disableScreenshotDetection()Z
                        move-result v$freeRegister
                        if-nez v$freeRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", nextConstInstruction),
                    )

                    enableSettings("disableScreenshotDetection")
                }
            }
        }
    }
