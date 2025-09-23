package app.crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeDownloaderPatch =
    bytecodePatch(
        name = "Custom downloader",
        description = "Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, entityGenerator)

        execute {
            val actionName = "Download"
            val prefFunctionName = "enableNativeDownloader"
            val stringId = "piko_pref_native_downloader_alert_title"
            val iconId = "ic_vector_incoming"
            val functionReference = "/NativeDownloader;->downloader"
            val statusFunctionName = "nativeDownloader"
            shareMenuButtonInjection(actionName, prefFunctionName, stringId, iconId, functionReference, statusFunctionName)

//
//            val buttonName = "Download"
//
//            val viewDebugDialogReference =
//                (
//                    shareMenuButtonAddHook.method
//                        .implementation
//                        ?.instructions
//                        ?.last { it.opcode == Opcode.SGET_OBJECT } as Instruction21c
//                ).reference
//            // Add action
//            val downloadActionReference = addAction(buttonName)
//            println("--------------------")
//            println(downloadActionReference)
//            println(viewDebugDialogReference)
//            println("--------------------")
//            // Register button
//            registerButton(buttonName, "enableNativeDownloader")
//
//            // Set Button Text
//            setButtonText(buttonName, "piko_pref_native_downloader_alert_title")
//            setButtonIcon(buttonName, "ic_vector_incoming")
//
//            // Add Button function
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
//            val activityRefReg = (buttonFuncMethod[OkLoc - 3] as Instruction35c).registerD
//
//            addButtonInstructions(
//                downloadActionReference,
//                """
//                check-cast v$timelineRefReg, ${timelineRef.reference.extractDescriptors()[0]}
//                iget-object v1, v$timelineRefReg, ${timelineRef.reference}
//
//                invoke-static {v$activityRefReg, v1}, $NATIVE_DESCRIPTOR/NativeDownloader;->downloader(Landroid/content/Context;Ljava/lang/Object;)V
//                """.trimIndent(),
//                viewDebugDialogReference,
//            )
//
//            settingsStatusLoadFingerprint.enableSettings("nativeDownloader")
        }
    }
