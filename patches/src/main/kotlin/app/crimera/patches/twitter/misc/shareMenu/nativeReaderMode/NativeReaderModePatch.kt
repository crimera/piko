package app.crimera.patches.twitter.misc.shareMenu.nativeReaderMode

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.*
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeReaderModePatch =
    bytecodePatch(
        name = "Native reader mode",
        description = "Requires X 11.0.0-release.0 or higher.",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, entityGenerator)

        execute {
            val actionName = "ReaderMode"
            val prefFunctionName = "enableNativeReaderMode"
            val stringId = "piko_title_native_reader_mode"
            val iconId = "ic_vector_book_stroke_on"
            val functionReference = "/readerMode/ReaderModeUtils;->launchReaderMode"
            val statusFunctionName = "nativeReaderMode"
            shareMenuButtonInjection(actionName, prefFunctionName, stringId, iconId, functionReference, statusFunctionName)

//            val actionName = "ReaderMode"
//
//            val viewDebugDialogReference =
//                (
//                    shareMenuButtonAddHook
//                        .method
//                        .implementation
//                        ?.instructions
//                        ?.last { it.opcode == Opcode.SGET_OBJECT } as Instruction21c
//                ).reference
//            // Add action
//            val downloadActionReference = addAction(actionName)
//
//            // Register button
//            registerButton(actionName, "enableNativeReaderMode")
//
//            // Set Button Text
//            setButtonText(actionName, "piko_title_native_reader_mode")
//            setButtonIcon(actionName, "ic_vector_book_stroke_on")
//
//            // TODO: handle possible nulls
//            val buttonFuncMethod =
//                shareMenuButtonFuncCallFingerprint.method
//                    .implementation
//                    ?.instructions
//                    ?.toList()
//            val deleteStatusLoc = shareMenuButtonFuncCallFingerprint.stringMatches?.first { it.string == "Delete Status" }?.index!!
//            val OkLoc = shareMenuButtonFuncCallFingerprint.stringMatches?.first { it.string == "OK" }?.index!!
//            val conversationalRepliesLoc =
//                shareMenuButtonFuncCallFingerprint.stringMatches
//                    ?.first {
//                        it.string ==
//                            "conversational_replies_android_pinned_replies_creation_enabled"
//                    }?.index
//
//            val timelineRef =
//                (
//                    buttonFuncMethod
//                        ?.filterIndexed { i, ins ->
//                            i > conversationalRepliesLoc!! && ins.opcode == Opcode.IGET_OBJECT
//                        }?.first() as Instruction22c?
//                ) ?: throw PatchException("Failed to find timelineRef")
//            val timelineRefReg = (buttonFuncMethod?.get(deleteStatusLoc - 1) as Instruction35c).registerD
//
//            val activityRefReg = (buttonFuncMethod[OkLoc - 3] as Instruction35c).registerD
//
//            // Add Button function
//            addButtonInstructions(
//                downloadActionReference,
//                """
//                check-cast v$timelineRefReg, ${timelineRef.reference.extractDescriptors()[0]}
//                iget-object v1, v$timelineRefReg, ${timelineRef.reference}
//
//                invoke-static {v$activityRefReg, v1}, $NATIVE_DESCRIPTOR/readerMode/ReaderModeUtils;->launchReaderMode(Landroid/content/Context;Ljava/lang/Object;)V
//                """.trimIndent(),
//                viewDebugDialogReference,
//            )
//
//            settingsStatusLoadFingerprint.enableSettings("nativeReaderMode")
        }
    }
