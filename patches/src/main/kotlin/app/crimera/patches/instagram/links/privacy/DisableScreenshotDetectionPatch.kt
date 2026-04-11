/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

internal object ScreenshotDetectorStartFingerprint : Fingerprint(
    strings = listOf("ig_android_story_screenshot_directory", "screenshot_detector"),
)

@Suppress("unused")
val disableScreenshotDetectionPatch =
    bytecodePatch(
        name = "Disable screenshot detection",
        description = "Prevents Instagram from detecting screenshots in DMs and stories, so other users won't be notified.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            ScreenshotDetectorStartFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->disableScreenshotDetection()Z
                    move-result v0
                    if-eqz v0, :original
                    return-void
                    """.trimIndent(),
                    ExternalLabel("original", getInstruction(0)),
                )
            }

            enableSettings("disableScreenshotDetection")
        }
    }
