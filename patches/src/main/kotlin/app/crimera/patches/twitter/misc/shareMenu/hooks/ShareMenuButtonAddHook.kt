/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.hooks

import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

internal object ShareMenuButtonAddHook : Fingerprint(
    definingClass = "Lcom/twitter/tweet/action/",
    returnType = "V",
    parameters =
        listOf(
            "Lcom/twitter/model/timeline/",
            "I",
            "Lcom/twitter/util/collection/",
        ),
)

context(patchContext: BytecodePatchContext)
fun registerButton(
    buttonActionReference: String,
    functionName: String,
) {
    val shareMenuButtonAdd = ShareMenuButtonAddHook.method
    val lastParamIndex = shareMenuButtonAdd.parameters.lastIndex

    val addToCollection =
        (shareMenuButtonAdd.instructions.lastOrNull { it.opcode == Opcode.INVOKE_VIRTUAL }
            ?: throw PatchException("Expected an INVOKE_VIRTUAL instruction to add the button to the share menu collection"))
            as Instruction35c
    val nextLabelTarget =
        shareMenuButtonAdd.instructions.firstOrNull { it.opcode == Opcode.INVOKE_STATIC }
            ?: throw PatchException("Expected an INVOKE_STATIC instruction to use as the share menu button fallback label")

    shareMenuButtonAdd.addInstructionsWithLabels(
        0,
        """
        invoke-static{}, $PREF_DESCRIPTOR;->$functionName()Z
        move-result v0
        if-eqz v0, :next
        sget-object v0, $buttonActionReference
        invoke-virtual {p$lastParamIndex, v0}, ${addToCollection.reference}
        """.trimIndent(),
        ExternalLabel("next", nextLabelTarget),
    )
}
