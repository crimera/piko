/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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
import app.morphe.util.indexOfFirstLiteralInstructionOrThrow
import app.morphe.util.registersUsed

internal const val HUNDRED = 100.0f

internal object StoryRingBuilderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            literal(66.0f),
            literal(HUNDRED),
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
            StoryRingBuilderFingerprint.method.apply {
                val ringSizeLiteralIndex = indexOfFirstLiteralInstructionOrThrow(HUNDRED)
                val register = getInstruction(ringSizeLiteralIndex).registersUsed[0]

                addInstructions(
                    ringSizeLiteralIndex + 1,
                    """
                    $PREF_CALL_DESCRIPTOR->customiseStoryRingSize()F
                    move-result v$register
                    """.trimIndent(),
                )
                enableSettings("customiseStoryRingSize")
            }
        }
    }
