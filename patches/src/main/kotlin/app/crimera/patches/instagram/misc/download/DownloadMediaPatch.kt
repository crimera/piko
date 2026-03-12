package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.mediadata.mediaDataPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.MethodFieldMetadata
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode


@Suppress("unused")
val downloadMediaPatch = bytecodePatch(
    name = "Download media",
    description = "Adds ability to download posts, reels, stories and highlights",
) {
    dependsOn(settingsPatch, mediaDataPatch, handleStoryButtonPatch)
    compatibleWith("com.instagram.android")

    execute {
        val appActivity = "Landroidx/fragment/app/FragmentActivity;"

        val enumBtnClass = classNameToExtension(EnumButtonClassFingerprint.classDef.type)
        GetEnumButtonClassExtensionFingerprint.changeFirstString(enumBtnClass)

        MediaOptionsOverflowMenuCreatorConstructorFingerprint.classDef.apply {
            val addingFeedButtonMethodName = methods.first { it.parameters.size > 1 }.name
            AddFeedButtonExtensionFingerprint.changeFirstString(addingFeedButtonMethodName)
        }


        val addingReelButtonMethodName = AddingReelButtonMethodFingerprint.method.name
        AddReelButtonExtensionFingerprint.changeFirstString(addingReelButtonMethodName)

        val currentViewingMediaFieldData = EditMediaInfoFragmentFingerprint.method.instructions.last { it.opcode == Opcode.IGET }.fieldExtractor()

        AddFeedButtonFingerprint.method.apply {
            var arrayListRegister = -1
            var checkCastRegister = -1
            var checkCastIndex = -1

            if(getInstruction(0).opcode == Opcode.INVOKE_STATIC){
                arrayListRegister = getInstruction(1).registersUsed[0]
                checkCastIndex = indexOfFirstInstruction(Opcode.CHECK_CAST)
                checkCastRegister = getInstruction(checkCastIndex).registersUsed[0]
            }else {
                instructions.filter { it.opcode == Opcode.RETURN_OBJECT }.forEach {
                    val index = it.location.index
                    val nextInstruction = getInstruction(index + 1)
                    if (nextInstruction.opcode == Opcode.NEW_INSTANCE) {
                        arrayListRegister = nextInstruction.registersUsed[0]
                        checkCastIndex = indexOfFirstInstruction(index, Opcode.CHECK_CAST)
                        checkCastRegister = getInstruction(checkCastIndex).registersUsed[0]
                        return@apply
                    }
                }
            }

            if(arrayListRegister!= -1 && checkCastRegister!=-1 && checkCastIndex!=-1){
                addInstructions(
                    checkCastIndex + 1, """
                        invoke-static {v$checkCastRegister,v$arrayListRegister},$FEED_BUTTON_DESCRIPTOR->addFeedButton(Ljava/lang/Object;Ljava/util/ArrayList;)V
                    """.trimIndent()
                )
            }
        }

        FeedButtonOnClickFingerprint.method.apply {
            val classDef = FeedButtonOnClickFingerprint.classDef
            val className = classDef.type
            val classFields = classDef.fields

            val appActivityField = classFields.first { it.type == appActivity }

            val mediaObjectIGetFieldData = instructions[indexOfFirstInstruction(Opcode.IGET_OBJECT)].fieldExtractor()
            val mediaObjectClass = extensionToClassName(mediaObjectIGetFieldData.returnType)
            val mediaObjectField = mediaObjectIGetFieldData.name

            val mediaExtraDataClass = currentViewingMediaFieldData.definingClass
            val mediaExtraDataField = classDef.fields.first { it.type == extensionToClassName(mediaExtraDataClass) }
            val currentViewingMediaIndexField = currentViewingMediaFieldData.name

            addInstructionsWithLabels(0,"""
                move-object/from16 v1, p1
                invoke-static {v1}, $FEED_BUTTON_DESCRIPTOR->isDownloadButton(Lcom/instagram/feed/media/mediaoption/MediaOption${'$'}Option;)Z
                move-result v0
                if-eqz v0, :piko
                
                move-object/from16 v0, p0
                iget-object v5, v0, $appActivityField
                iget-object v2, v0, $className->$mediaObjectField:$mediaObjectClass
                iget-object v4, v0, $mediaExtraDataField
                iget v4, v4, ${mediaExtraDataField.type}->${currentViewingMediaIndexField}:I
                
                invoke-static {v5, v2, v4}, $FEED_BUTTON_DESCRIPTOR->downloadPost(Landroid/content/Context;Ljava/lang/Object;I)V
                return-void
                
            """.trimIndent(), ExternalLabel("piko",getInstruction(0)))
        }

        AddReelButtonFingerprint.method.apply {
            val classDef = AddReelButtonFingerprint.classDef
            val className = classDef.type
            val classFields = classDef.fields

            val appActivityField = classFields.first { it.type == appActivity }

            val selfClassRegister = instructions[indexOfFirstInstruction(Opcode.MOVE_OBJECT_FROM16)].registersUsed[0]
            val buttonAdderInstanceRegister = instructions[indexOfFirstInstruction(Opcode.NEW_INSTANCE)].registersUsed[0]

            val firstIfEqzIndex = indexOfFirstInstruction(Opcode.IF_EQZ)
            val mediaObjectRegister = instructions[firstIfEqzIndex-1].registersUsed[0]

            val freeRegisterOne = instructions[indexOfFirstInstruction(Opcode.MOVE_RESULT_OBJECT)].registersUsed[0]

            addInstructions(firstIfEqzIndex,"""
                iget-object v$freeRegisterOne, v$selfClassRegister, $appActivityField
                invoke-static {v$freeRegisterOne,v$buttonAdderInstanceRegister,v$mediaObjectRegister},$REEL_BUTTON_DESCRIPTOR->addReelButton(Landroid/content/Context;Ljava/lang/Object;Ljava/lang/Object;)V
            """.trimIndent())
        }

        enableSettings("downloadMedia")
    }
}