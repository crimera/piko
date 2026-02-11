package app.crimera.patches.twitter.misc.shareMenu.hooks

import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.shareMenu.fingerprints.addAction
import app.crimera.patches.twitter.misc.shareMenu.fingerprints.addButtonInstructions
import app.crimera.patches.twitter.misc.shareMenu.fingerprints.shareMenuButtonFuncCallFingerprint
import app.crimera.utils.Constants
import app.crimera.utils.enableSettings
import app.crimera.utils.extractDescriptors
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

context(BytecodePatchContext)
fun shareMenuButtonInjection(
    actionName: String,
    prefFunctionName: String,
    stringId: String,
    iconId: String,
    functionReference: String,
    statusFunctionName: String,
) {
    val viewDebugDialogReference =
        (
            shareMenuButtonAddHook
                .method
                .implementation
                ?.instructions
                ?.last { it.opcode == Opcode.SGET_OBJECT } as Instruction21c
        ).reference

    // Add action
    val buttonActionReference = addAction(actionName)

    // Register button
    registerButton(buttonActionReference, prefFunctionName)

    // Set Button Text
    setButtonText(actionName, stringId)
    setButtonIcon(actionName, iconId)

    // TODO: handle possible nulls
    val buttonFuncMethod =
        shareMenuButtonFuncCallFingerprint.method
            .implementation
            ?.instructions
            ?.toList()
    val deleteStatusLoc = shareMenuButtonFuncCallFingerprint.stringMatches?.first { it.string == "Delete Status" }?.index!!
    val OkLoc = shareMenuButtonFuncCallFingerprint.stringMatches?.first { it.string == "OK" }?.index!!
    val conversationalRepliesLoc =
        shareMenuButtonFuncCallFingerprint.stringMatches
            ?.first {
                it.string ==
                    "conversational_replies_android_pinned_replies_creation_enabled"
            }?.index

    val timelineRef =
        (
            buttonFuncMethod
                ?.filterIndexed { i, ins ->
                    i > conversationalRepliesLoc!! && ins.opcode == Opcode.IGET_OBJECT
                }?.first() as Instruction22c?
        ) ?: throw PatchException("Failed to find timelineRef")
    val timelineRefReg = (buttonFuncMethod?.get(deleteStatusLoc - 1) as Instruction35c).registerD

    val activityRefReg = (buttonFuncMethod[OkLoc - 3] as Instruction35c).registerD

    // Add Button function
    addButtonInstructions(
        buttonActionReference,
        """
        check-cast v$timelineRefReg, ${timelineRef.reference.extractDescriptors()[0]}
        iget-object v1, v$timelineRefReg, ${timelineRef.reference}
        
        invoke-static {v$activityRefReg, v1}, ${Constants.NATIVE_DESCRIPTOR}$functionReference(Landroid/content/Context;Ljava/lang/Object;)V
        """.trimIndent(),
        viewDebugDialogReference,
    )

    settingsStatusLoadFingerprint.enableSettings(statusFunctionName)
}
