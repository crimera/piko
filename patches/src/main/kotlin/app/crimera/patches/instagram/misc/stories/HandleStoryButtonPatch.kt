/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

// The method is obfuscated but this is where it adds buttons.
object AddStoryButtonFingerprint : Fingerprint(
    returnType = "[Ljava/lang/CharSequence;",
    strings = listOf("[INTERNAL] Pause Playback"),
    custom = { methodDef, _ ->
        methodDef.parameters.size == 1
    },
)

object SelfStoryAddStoryButtonFingerprint : Fingerprint(
    returnType = "[Ljava/lang/CharSequence;",
    strings = listOf("ReelOptionsOverflowHelper"),
)

// The method is obfuscated but this is where the onclick call executes.
internal object OnCLickStoryButtonFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("explore_viewer", "friendships/mute_friend_reel/%s/", "[INTERNAL] Pause Playback"),
)

internal object SelfStoryOnCLickStoryButtonFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("story_interactions/bulk_story_like/", "[INTERNAL] Pause Playback"),
)

val handleStoryButtonPatch =
    bytecodePatch(
        description = "This patch is used for handing button interaction on stories",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(decoderEntity)
        execute {

            val STORY_BUTTON_EXTENSION_CLASS = "${PATCHES_DESCRIPTOR}/story/StoryButton;"
            // Add button on self story bottom sheet.
            SelfStoryAddStoryButtonFingerprint.method.apply {
                val firstIfEqz = indexOfFirstInstruction(Opcode.IF_EQZ)
                val arrayMoveResultObjectIndex = firstIfEqz - 1
                val arrayListRegister = getInstruction(arrayMoveResultObjectIndex).registersUsed[0]

                addInstructions(
                    arrayMoveResultObjectIndex + 1,
                    """
                    invoke-static {v$arrayListRegister},$STORY_BUTTON_EXTENSION_CLASS ->addButtons(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                    move-result-object v$arrayListRegister
                    """.trimIndent(),
                )
            }

            // Add button on story bottom sheet.
            AddStoryButtonFingerprint.method.apply {
                val firstMoveResultObjectInstruction = indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)
                val arrayListRegister = getInstruction(firstMoveResultObjectInstruction).registersUsed[0]

                addInstructions(
                    firstMoveResultObjectInstruction + 1,
                    """
                    invoke-static {v$arrayListRegister},$STORY_BUTTON_EXTENSION_CLASS ->addButtons(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                    move-result-object v$arrayListRegister
                    """.trimIndent(),
                )
            }

            val onClickFingerprints = listOf(SelfStoryOnCLickStoryButtonFingerprint, OnCLickStoryButtonFingerprint)
            onClickFingerprints.forEach { fingerprint ->
                fingerprint.method.apply {
                    val classDef = fingerprint.classDef
                    val reelItemClassName = "Lcom/instagram/model/reels/ReelItem;"
                    val appActivity = "Landroid/app/Activity;"
                    val charSequence = "Ljava/lang/CharSequence;"

                    val classFields = classDef.fields
                    val reelItemField = classFields.first { it.type == reelItemClassName }
                    val appActivityField = classFields.first { it.type == appActivity }
                    val reelItemMediaField =
                        mutableClassDefBy { it.type == reelItemClassName }.fields.last { it.type == MEDIA_CLASS_NAME }

                    val characterSequenceParameterIndex = parameters.indexOfLast { it.type == charSequence }
                    val selfClassParameterIndex = parameters.indexOfLast { it.type == classDef.type }

                    // Hard coding registries as it's going to be the first line in the method.
                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p$characterSequenceParameterIndex
                        move-object/from16 v1, p$selfClassParameterIndex
                        
                        iget-object v2, v1, $appActivityField
                        iget-object v3, v1, $reelItemField
                        iget-object v3, v3, $reelItemMediaField
                        invoke-static {v0,v2,v3}, $STORY_BUTTON_EXTENSION_CLASS->storyButtonAction($charSequence Landroid/content/Context;Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :piko
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }
            }
        }
    }
