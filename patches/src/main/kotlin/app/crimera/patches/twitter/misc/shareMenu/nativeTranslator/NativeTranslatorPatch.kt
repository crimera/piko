package app.crimera.patches.twitter.misc.shareMenu.nativeTranslator

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.*
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeTranslatorModePatch =
    bytecodePatch(
        name = "Custom translator",
        description = "Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, entityGenerator)

        execute {
            val actionName = "Translate"
            val prefFunctionName = "enableNativeTranslator"
            val stringId = "translate_tweet_show"
            val iconId = "ic_vector_sparkle"
            val functionReference = "/translator/NativeTranslator;->translate"
            val statusFunctionName = "nativeTranslator"
            shareMenuButtonInjection(actionName, prefFunctionName, stringId, iconId, functionReference, statusFunctionName)
//
//            val viewDebugDialogReference =
//                (
//                    shareMenuButtonAddHook
//                        .method
//                        .implementation
//                        ?.instructions
//                        ?.last { it.opcode == Opcode.SGET_OBJECT } as Instruction21c
//                ).reference
//
//            // Add action
//            val downloadActionReference = addAction(actionName)
//
//            // Register button
//            registerButton(actionName, "enableNativeTranslator")
//
//            // Set Button Text
//            setButtonText(actionName, "translate_tweet_show")
//            setButtonIcon(actionName, "ic_vector_sparkle")
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
//                invoke-static {v$activityRefReg, v1}, $NATIVE_DESCRIPTOR(Landroid/content/Context;Ljava/lang/Object;)V
//                """.trimIndent(),
//                viewDebugDialogReference,
//            )
//
//            settingsStatusLoadFingerprint.enableSettings("")
        }
    }
