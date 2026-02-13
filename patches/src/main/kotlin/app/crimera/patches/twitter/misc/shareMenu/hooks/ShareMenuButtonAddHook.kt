package app.crimera.patches.twitter.misc.shareMenu.hooks

import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

internal val shareMenuButtonAddHook =
    fingerprint {
        returns("V")
        custom { methodDef, _ ->
            val params = methodDef.parameters.joinToString("") { it.type }
            methodDef.name == "a" &&
                params.contains("com/twitter/model/timeline") &&
                params.contains("com/twitter/util/collection")
        }
    }

context(BytecodePatchContext)
fun registerButton(
    buttonActionReference: String,
    functionName: String,
) {
    val shareMenuButtonAdd = shareMenuButtonAddHook.method
    val lastParamIndex = shareMenuButtonAdd.parameters.lastIndex

    // TODO: handle nulls
    val addToCollection =
        shareMenuButtonAdd.instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL } as Instruction35c

    shareMenuButtonAdd.addInstructionsWithLabels(
        0,
        """
        invoke-static{}, $PREF_DESCRIPTOR;->$functionName()Z
        move-result v0
        if-eqz v0, :next
        sget-object v0, $buttonActionReference
        invoke-virtual {p$lastParamIndex, v0}, ${addToCollection.reference}
        """.trimIndent(),
        ExternalLabel("next", shareMenuButtonAdd.instructions.first { it.opcode == Opcode.INVOKE_STATIC }),
    )
}
