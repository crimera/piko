/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.customiseStoryRingSize

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed

internal object StoryRingBuilderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            literal(66.0f),
            literal(100.0f),
            literal(3.75),
        ),
)

// Credits InstaPro/MyInsta
@Suppress("unused")
val customiseStoryRingSizePatch =
    bytecodePatch(
        name = "Customise story ring size",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            StoryRingBuilderFingerprint.let {
                it.method.apply {
                    val ringSizeLiteralIndex = it.instructionMatches[1].index
                    val register = getInstruction(ringSizeLiteralIndex).registersUsed[0]

                    addInstructions(
                        ringSizeLiteralIndex + 1,
                        """
                            $PREF_CALL_DESCRIPTOR->customiseStoryRingSize()F
                            move-result v$register
                        """
                    )
                    enableSettings("customiseStoryRingSize")
                }
            }
        }
    }
