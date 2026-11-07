/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.improveImageViewing

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.registersUsed

@Suppress("unused")
val improveImageViewingPatch =
    bytecodePatch(
        name = "Improve image viewing",
        description = "Fetches max resolution images from server.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {
            // p2 is the target width Instagram picks the closest image variant to.
            ReturnExtendedImageUrlFingerprint.method.addInstructions(
                0,
                """
                invoke-static {p2}, $PREF_DESCRIPTOR->imageVariantTargetWidth(I)I
                move-result p2
                """,
            )

            // The array holds the DPI, then the screen width and height.
            val screenSizeArray = SetDPIMetricsFingerprint.instructionMatches.last()
            SetDPIMetricsFingerprint.method.addInstructions(
                screenSizeArray.index,
                screenSizeArray.instruction.registersUsed.drop(1).joinToString("\n") {
                    """
                    invoke-static {v$it}, $PREF_DESCRIPTOR->userAgentScreenSize(Ljava/lang/Integer;)Ljava/lang/Integer;
                    move-result-object v$it
                    """
                },
            )

            enableSettings("improveImageViewing")
        }
    }
