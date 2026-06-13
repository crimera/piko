/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.MethodFieldMetadata
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
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

// The method is obfuscated but this is where the onclick call executes.
internal object OnCLickStoryButtonFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("explore_viewer", "friendships/mute_friend_reel/%s/", "[INTERNAL] Pause Playback"),
)

val handleStoryButtonPatch =
    bytecodePatch(
        description = "This patch is used for handing button interaction on stories",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val STORY_BUTTON_EXTENSION_CLASS = "${PATCHES_DESCRIPTOR}/story/StoryButton;"

            var mediaObjectFromReelItemFieldExtraction: MethodFieldMetadata
            // Add button on story bottom sheet.
            AddStoryButtonFingerprint.method.apply {
                val igetObjectList = instructions.filter { it.opcode == Opcode.IGET_OBJECT }
                val secondIGetObject = igetObjectList.getOrNull(1)
                    ?: throw PatchException("Failed to find second IGET_OBJECT in ${AddStoryButtonFingerprint.definingClass}")

                mediaObjectFromReelItemFieldExtraction = secondIGetObject.fieldExtractor()

                // The last invoke-virtual instruction is arrayList.add().
                val lastInvokeVirtual = instructions.lastOrNull { it.opcode == Opcode.INVOKE_VIRTUAL }
                    ?: throw PatchException("Failed to find INVOKE_VIRTUAL in ${AddStoryButtonFingerprint.definingClass}")

                val lastInvokeVirtualRegisters = lastInvokeVirtual.registersUsed
                val arrayListRegister = lastInvokeVirtualRegisters[0]

                val firstMoveResultObjectInstruction = indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)
                if (firstMoveResultObjectInstruction == -1) throw PatchException("Failed to find MOVE_RESULT_OBJECT in ${AddStoryButtonFingerprint.definingClass}")

                addInstructions(
                    firstMoveResultObjectInstruction + 1,
                    """
                    invoke-static {v$arrayListRegister},$STORY_BUTTON_EXTENSION_CLASS ->addButtons(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                    move-result-object v$arrayListRegister
                    """.trimIndent(),
                )
            }

            OnCLickStoryButtonFingerprint.method.apply {
                val classDef = OnCLickStoryButtonFingerprint.classDef
                val reelItemClassName = "Lcom/instagram/model/reels/ReelItem;"
                val appActivity = "Landroid/app/Activity;"
                val charSequence = "Ljava/lang/CharSequence;"

                val classFields = classDef.fields
                val reelItemField = classFields.firstOrNull { it.type == reelItemClassName }
                    ?: throw PatchException("Failed to find reelItemField in ${classDef.type}")
                val reelItemFieldName = reelItemField.name

                val appActivityField = classFields.firstOrNull { it.type == appActivity }
                    ?: throw PatchException("Failed to find appActivityField in ${classDef.type}")
                val appActivityFieldName = appActivityField.name

                val characterSequenceParameterIndex =
                    OnCLickStoryButtonFingerprint.method.parameters.indexOfLast {
                        it.type == charSequence
                    }
                if (characterSequenceParameterIndex == -1) throw PatchException("Failed to find CharSequence parameter")

                val selfClassParameterIndex = OnCLickStoryButtonFingerprint.method.parameters.indexOfLast { it.type == classDef.type }
                if (selfClassParameterIndex == -1) throw PatchException("Failed to find self class parameter")

                // Hard coding registries as it's going to be the first line in the method.
                addInstructionsWithLabels(
                    0,
                    """
                    move-object/from16 v0, p$characterSequenceParameterIndex
                    move-object/from16 v1, p$selfClassParameterIndex
                    
                    iget-object v2, v1, ${classDef.type}->$appActivityFieldName:$appActivity
                    iget-object v3, v1, ${classDef.type}->$reelItemFieldName:$reelItemClassName
                    iget-object v3, v3, $reelItemClassName->${mediaObjectFromReelItemFieldExtraction.name}:${extensionToClassName(
                        mediaObjectFromReelItemFieldExtraction.returnType,
                    )}
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
