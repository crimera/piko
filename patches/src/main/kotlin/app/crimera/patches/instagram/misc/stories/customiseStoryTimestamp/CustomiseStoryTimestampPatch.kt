/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.stories.customiseStoryTimestamp

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object ReelItemTimestampFormatMethodFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "Lcom/instagram/model/reels/ReelItem;",
    parameters = listOf("Landroid/content/Context;"),
)

@Suppress("unused")
val customiseStoryTimestampPatch =
    bytecodePatch(
        name = "Customise story timestamp",
        description = "Customise the timestamp that shows when the story was posted",
    ) {
        dependsOn(settingsPatch)

        compatibleWith("com.instagram.android")

        execute {
            ReelItemTimestampFormatMethodFingerprint.method.apply {
                val longToDoubleIndex = indexOfFirstInstruction(Opcode.LONG_TO_DOUBLE)
                val longToDoubleInstruction = getInstruction(longToDoubleIndex)
                val longToDoubleRegisters = longToDoubleInstruction.registersUsed
                val dummyRegister = longToDoubleRegisters[0]
                val postedTimestampRegister = longToDoubleRegisters[1]

                addInstructionsWithLabels(
                    longToDoubleIndex,
                    """
                    invoke-static {v$postedTimestampRegister, v1 }, ${PATCHES_DESCRIPTOR}/story/StoryTimestamp;->customiseStoryTimestamp(J)Ljava/lang/String;
                    move-result-object v$dummyRegister
                    if-eqz v$dummyRegister, :piko
                    return-object v$dummyRegister
                    """.trimIndent(),
                    ExternalLabel("piko", longToDoubleInstruction),
                )

                enableSettings("customiseStoryTimestamp")
            }
        }
    }
