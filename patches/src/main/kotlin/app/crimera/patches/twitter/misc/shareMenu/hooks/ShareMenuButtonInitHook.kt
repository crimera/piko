package app.crimera.patches.twitter.misc.shareMenu.hooks

import app.crimera.utils.Constants.UTILS_DESCRIPTOR
import app.crimera.utils.instructionToString
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patches.shared.misc.mapping.get
import app.revanced.patches.shared.misc.mapping.resourceMappings
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c

internal val shareMenuButtonInitHook =
    fingerprint {
        strings("Debug")
        custom { _, classDef ->
            classDef.type.contains("Lcom/twitter/tweet/action/legacy")
        }
    }

context(BytecodePatchContext)
fun setButtonText(
    name: String,
    stringId: String,
) {
    shareMenuButtonInitHook.stringMatches.let {
        val match = it!!.first()
        val setTextStart = match.index - 1
        val setTextEnd = match.index + 3
        val method = shareMenuButtonInitHook.method
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
    val allMethods = shareMenuButtonInitHook.classDef.methods
    val method = allMethods.first { it.returnType == "V" }

    val iconAdditionStart =
        method
            .instructions
            .first { it.opcode == Opcode.MOVE_RESULT_OBJECT }
            .location.index + 1
    val iconAdditionEnd = iconAdditionStart + 4

    val iconId = resourceMappings["drawable", iconStr]

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
