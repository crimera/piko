/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.filter.story

import app.crimera.patches.instagram.entity.reelResponseItem.reelResponseItemEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

// Heavily based on @brosssh work.
// https://github.com/brosssh/instagram-morphe-patches-library/blob/dev/patch-library/src/main/kotlin/app/morphe/library/instagram/patches/FilterStoriesListPatch.kt

@Suppress("unused")
val filterStoriesPatch =
    bytecodePatch(
        name = "Filter stories",
        description = "Filter stories to hide based on different categories",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, reelResponseItemEntity, userDataEntity)
        execute {

            StoryResponseJsonParserFingerprint.apply {
                val strIndex = stringMatches[0].index

                method.apply {

                    val reelItemCheckInstruction = instructions.last { it.location.index < strIndex && it.opcode == Opcode.IF_EQZ }
                    val index = reelItemCheckInstruction.location.index
                    val reelResponseItemRegister = reelItemCheckInstruction.registersUsed[0]

                    addInstructionsWithLabels(
                        index + 1,
                        """
                        invoke-static{v$reelResponseItemRegister}, $PATCHES_DESCRIPTOR/filter/story/FilterStory;->filter(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$reelResponseItemRegister
                        if-eqz v$reelResponseItemRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(index + 2)),
                    )

                    enableSettings("storyFilters")
                }
            }
        }
    }
