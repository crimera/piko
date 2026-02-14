package app.crimera.patches.twitter.misc.shareMenu.hooks

import app.crimera.utils.Constants.UTILS_DESCRIPTOR
import app.crimera.utils.instructionToString
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.string
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.revanced.patches.shared.misc.mapping.ResourceType
import app.revanced.patches.shared.misc.mapping.getResourceId
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c

internal object ShareMenuButtonInitHook : Fingerprint(
    definingClass = "Lcom/twitter/tweet/action/legacy",
    filters = listOf(
        string("Debug")
    )
)

context(BytecodePatchContext)
fun setButtonText(
    name: String,
    stringId: String,
) {
    ShareMenuButtonInitHook.instructionMatches.first().let {
        val match = it.index
        val setTextStart = match - 1
        val setTextEnd = match + 3
        val method = ShareMenuButtonInitHook.method
        val buttonInitInstructions =
            method.instructions
                .filterIndexed { index, _ ->
                    index in setTextStart..setTextEnd
                }.mapIndexed { index, ins ->
                    when (index) {
                        0 -> instructionToString(ins).replace("ViewDebugDialog", name)
                        1 -> {
                            ins as Instruction21c
                            """
                            const-string v${ins.registerA}, "$stringId"
                            invoke-static {v${ins.registerA}},$UTILS_DESCRIPTOR;->strRes(Ljava/lang/String;)Ljava/lang/String;
                            move-result-object v${ins.registerA}
                            """.trimIndent()
                        }

                        else -> instructionToString(ins)
                    }
                }.joinToString("\n")

        method.addInstructions(setTextEnd + 1, buttonInitInstructions)
    }
}

context(BytecodePatchContext)
fun setButtonIcon(
    name: String,
    iconStr: String,
) {
    val allMethods = ShareMenuButtonInitHook.classDef.methods
    val method = allMethods.first { it.returnType == "V" }

    val iconAdditionStart = method.indexOfFirstInstructionOrThrow(Opcode.MOVE_RESULT_OBJECT) + 1
    val iconAdditionEnd = iconAdditionStart + 4

    val iconId = getResourceId(ResourceType.DRAWABLE, iconStr)

    val buttonInitInstructions =
        method
            .instructions
            .filterIndexed { index, _ ->
                index in iconAdditionStart..iconAdditionEnd
            }.mapIndexed { index, ins ->
                when (index) {
                    0 -> instructionToString(ins).replace(Regex("->(\\w+):"), "->$name:")
                    1 -> "const v2, $iconId"
                    else -> instructionToString(ins)
                }
            }.joinToString("\n")

    method.addInstructions(iconAdditionStart, buttonInitInstructions)
}
