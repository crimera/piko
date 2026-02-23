package app.crimera.patches.twitter.misc.shareMenu.hooks

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.shareMenu.fingerprints.addAction
import app.crimera.patches.twitter.misc.shareMenu.fingerprints.addButtonInstructions
import app.crimera.patches.twitter.misc.shareMenu.fingerprints.shareMenuButtonFuncCallFingerprint
import app.crimera.utils.Constants
import app.crimera.utils.enableSettings
import app.crimera.utils.extractDescriptors
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
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
            ShareMenuButtonAddHook
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

    val buttonFuncMethod =
        shareMenuButtonFuncCallFingerprint.method
            .implementation
            ?.instructions
            ?.toList()
            ?: throw PatchException("Failed to resolve share menu instructions")
    val deleteStatusLoc =
        shareMenuButtonFuncCallFingerprint.stringMatches
            ?.firstOrNull { it.string == "Delete Status" }
            ?.index
            ?: throw PatchException("Failed to find Delete Status string")
    val okLoc =
        shareMenuButtonFuncCallFingerprint.stringMatches
            ?.firstOrNull { it.string == "OK" }
            ?.index
            ?: throw PatchException("Failed to find OK string")
    val conversationalRepliesLoc =
        shareMenuButtonFuncCallFingerprint.stringMatches
            ?.firstOrNull {
                it.string ==
                    "conversational_replies_android_pinned_replies_creation_enabled"
            }?.index
            ?: throw PatchException("Failed to find conversational replies string")

    val timelineRef =
        (
            buttonFuncMethod
                .filterIndexed { i, ins ->
                    i > conversationalRepliesLoc && ins.opcode == Opcode.IGET_OBJECT
                }.firstOrNull() as Instruction22c?
        ) ?: throw PatchException("Failed to find timelineRef")
    val timelineRefReg = (buttonFuncMethod[deleteStatusLoc - 1] as Instruction35c).registerD

    val activityRefReg = (buttonFuncMethod[okLoc - 3] as Instruction35c).registerD

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

    SettingsStatusLoadFingerprint.enableSettings(statusFunctionName)
}
