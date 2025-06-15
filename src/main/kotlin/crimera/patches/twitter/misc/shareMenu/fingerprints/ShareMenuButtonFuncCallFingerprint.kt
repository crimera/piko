package crimera.patches.twitter.misc.shareMenu.fingerprints

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.reference.Reference
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.exception

object ShareMenuButtonFuncCallFingerprint : MethodFingerprint(
    returnType = "V",
    strings =
        listOf(
            "OK",
            "Delete Status",
            "click",
            "tweet_analytics",
            "author_moderated_replies_author_enabled",
            "conversational_replies_android_pinned_replies_creation_enabled",
        ),
) {
    fun addButtonInstructions(
        reference: String,
        instructions: String,
        debugDialogReference: Reference,
    ) {
        val buttonFunc = result ?: throw ShareMenuButtonFuncCallFingerprint.exception

        val buttonFuncMethod = buttonFunc.mutableMethod

        val deleteStatusLoc =
            buttonFunc.scanResult.stringsScanResult
                ?.matches!!
                .first { it.string == "Delete Status" }
                .index
        val gotoLoc = deleteStatusLoc + 8

        val buttonFuncInstructions = buttonFuncMethod.getInstructions()

        val condRegisters =
            getButtonEntry(buttonFuncInstructions, debugDialogReference)?.let {
                it as TwoRegisterInstruction
                listOf(it.registerA, it.registerB)
            } ?: throw PatchException("Failed to get button EntryPoint")

        // TODO: timeline n2 and core should be dynamically allocated
        buttonFuncMethod.addInstructionsWithLabels(
            gotoLoc,
            """
            sget-object v${condRegisters[1]}, $reference
            
            if-ne v${condRegisters[0]}, v${condRegisters[1]}, :nextbtn
            
            $instructions
            
            return-void
            """.trimIndent(),
            ExternalLabel("nextbtn", buttonFuncMethod.getInstruction(gotoLoc)),
        )

        getButtonEntry(buttonFuncInstructions, debugDialogReference)?.let {
            val loc = it.location.index

            buttonFuncMethod.addInstructionsWithLabels(
                loc,
                "if-ne v${condRegisters[0]}, v${condRegisters[1]}, :downloadbtn",
                ExternalLabel("downloadbtn", buttonFuncMethod.getInstruction(gotoLoc)),
            )

            // Remove the previous instruction
            buttonFuncMethod.removeInstruction(loc + 1)
        } ?: PatchException("Failed to get button EntryPoint")
    }

    private fun getButtonEntry(
        instructions: MutableList<BuilderInstruction>,
        reference: Reference,
    ): BuilderInstruction? {
        var out: BuilderInstruction? = null
        instructions.filter { it.opcode == Opcode.GOTO_16 }.forEach { ins ->
            val sGet = instructions[ins.location.index + 1]

            if (sGet.opcode == Opcode.SGET_OBJECT && (sGet as Instruction21c).reference == reference) {
                var index = ins.location.index
                while (instructions[index].opcode != Opcode.IGET_OBJECT) {
                    val currentIns = instructions[index]
                    if (currentIns.opcode == Opcode.IF_NE) {
                        out = currentIns
                    }
                    index += 1
                }
            }
        }
        return out
    }
}
