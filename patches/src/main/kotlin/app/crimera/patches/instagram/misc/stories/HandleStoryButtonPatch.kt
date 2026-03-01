package app.crimera.patches.instagram.misc.stories

import app.crimera.patches.instagram.misc.stories.viewstorymention.GetMediaObjectFromReelItemExtensionFingerprint
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

// The method is obfuscated but this is where it adds buttons.
object AddStoryButtonFingerprint:Fingerprint(
    returnType = "[Ljava/lang/CharSequence;",
    strings = listOf("[INTERNAL] Pause Playback")
)

// The method is obfuscated but this is where the onclick call executes.
internal object OnCLickStoryButtonFingerprint:Fingerprint(
    returnType = "V",
    strings = listOf("explore_viewer","friendships/mute_friend_reel/%s/","[INTERNAL] Pause Playback")
)

val handleStoryButtonPatch = bytecodePatch(
    description = "This patch is used for handing button interaction on stories",
) {
    compatibleWith("com.instagram.android")

    execute {

        val STORY_BUTTON_EXTENSION_CLASS = "${PATCHES_DESCRIPTOR}/story/StoryButton;"

        // Add button on story bottom sheet.
        AddStoryButtonFingerprint.method.apply {
            // The last invoke-virtual instruction is arrayList.add().
            val lastInvokeVirtualRegisters = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }.registersUsed
            val arrayListRegister = lastInvokeVirtualRegisters[0]

            val firstMoveResultObjectInstruction = indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)

            addInstructions(
                firstMoveResultObjectInstruction+1,
                """
                    invoke-static {v$arrayListRegister},$STORY_BUTTON_EXTENSION_CLASS ->addButtons(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                    move-result-object v$arrayListRegister
                """.trimIndent()
            )
        }

        OnCLickStoryButtonFingerprint.method.apply {
            val classDef = OnCLickStoryButtonFingerprint.classDef
            val reelItemClassName = "Lcom/instagram/model/reels/ReelItem;"
            val appActivity = "Landroid/app/Activity;"
            val userSession = "Lcom/instagram/common/session/UserSession;"
            val charSequence = "Ljava/lang/CharSequence;"

            val classFields = classDef.fields
            val reelItemFieldName  = classFields.first { it.type == reelItemClassName }.name
            val appActivityFieldName  = classFields.first { it.type == appActivity }.name
            val userSessionFieldName  = classFields.first { it.type == userSession }.name

            val characterSequenceParameterIndex = OnCLickStoryButtonFingerprint.method.parameters.indexOfLast { it.type == charSequence }
            val selfClassParameterIndex = OnCLickStoryButtonFingerprint.method.parameters.indexOfLast { it.type == classDef.type }


            // Hard coding registries as it's going to be the first line in the method.
            addInstructionsWithLabels(
                0,
                """
                    move-object/from16 v0, p$characterSequenceParameterIndex
                    move-object/from16 v1, p$selfClassParameterIndex
                    
                    iget-object v2, v1, ${classDef.type}->$appActivityFieldName:$appActivity
                    iget-object v3, v1, ${classDef.type}->$reelItemFieldName:$reelItemClassName
                    iget-object v4, v1, ${classDef.type}->$userSessionFieldName:$userSession
                    invoke-static {v0,v2,v3,v4}, $STORY_BUTTON_EXTENSION_CLASS->storyButtonAction($charSequence Landroid/content/Context;Ljava/lang/Object;$userSession)Z
                    move-result v0
                    if-eqz v0, :revanced
                    return-void
                """.trimIndent(),
                ExternalLabel("revanced", getInstruction(0))
            )
        }

    }
}