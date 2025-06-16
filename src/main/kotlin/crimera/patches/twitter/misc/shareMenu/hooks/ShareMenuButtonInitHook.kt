package crimera.patches.twitter.misc.shareMenu.hooks

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.exception
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.instructionToString

object ShareMenuButtonInitHook : MethodFingerprint(
    strings =
        listOf(
            "Debug",
        ),
    customFingerprint = { _, classDef ->
        classDef.type.contains("Lcom/twitter/tweet/action/legacy")
    },
) {
    fun setButtonText(
        name: String,
        stringId: String,
    ) {
        val buttonInitResult = ShareMenuButtonInitHook.result ?: throw ShareMenuButtonInitHook.exception

        buttonInitResult.scanResult.stringsScanResult?.let {
            val match = it.matches.first()
            val setTextStart = match.index - 1
            val setTextEnd = match.index + 3
            val method = buttonInitResult.mutableMethod
            val buttonInitInstructions =
                method
                    .getInstructions()
                    .filterIndexed { index, _ ->
                        index in setTextStart..setTextEnd
                    }.mapIndexed { index, ins ->
                        when (index) {
                            0 -> instructionToString(ins).replace("ViewDebugDialog", name)
                            1 -> {
                                ins as Instruction21c
                                """
                                const-string v${ins.registerA}, "$stringId"
                                invoke-static {v${ins.registerA}},${SettingsPatch.UTILS_DESCRIPTOR};->strRes(Ljava/lang/String;)Ljava/lang/String;
                                move-result-object v${ins.registerA}
                                """.trimIndent()
                            }

                            else -> instructionToString(ins)
                        }
                    }.joinToString("\n")

            method.addInstructions(setTextEnd + 1, buttonInitInstructions)
        } ?: throw PatchException("Could not find \"Debug\" string")
    }

    fun setButtonIcon(
        name: String,
        iconStr: String,
    ) {
        val result = result ?: throw PatchException("ShareMenuButtonInitHook not found")
        val allMethods = result.mutableClass.methods
        val method = allMethods.first { it.returnType == "V" }

        val iconAdditionStart =
            method
                .getInstructions()
                .first { it.opcode == Opcode.MOVE_RESULT_OBJECT }
                .location.index + 1
        val iconAdditionEnd = iconAdditionStart + 4

        val iconId = ResourceMappingPatch["drawable", iconStr]

        val buttonInitInstructions =
            method
                .getInstructions()
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
}
