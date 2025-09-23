package app.crimera.patches.twitter.misc.shareMenu.fingerprints

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.reference.Reference

internal val shareMenuButtonFuncCallFingerprint =
    fingerprint {
        returns("V")
        strings(
            "OK",
            "Delete Status",
            "click",
            "tweet_analytics",
            "author_moderated_replies_author_enabled",
            "conversational_replies_android_pinned_replies_creation_enabled",
        )
    }

context(BytecodePatchContext)
fun addButtonInstructions(
    reference: String,
    instructions: String,
    debugDialogReference: Reference,
) {
    val fingerprint = shareMenuButtonFuncCallFingerprint
    fingerprint.method.apply {
        val deleteStatusLoc =
            fingerprint.stringMatches!!
                .first { it.string == "Delete Status" }
                .index
        val gotoLoc = deleteStatusLoc + 8

        val condRegisters =
            getButtonEntry(this.instructions, debugDialogReference)?.let {
                it as TwoRegisterInstruction
                listOf(it.registerA, it.registerB)
            }!!

        addInstructionsWithLabels(
            gotoLoc,
            """  
            sget-object v${condRegisters[1]}, $reference  
              
            if-ne v${condRegisters[0]}, v${condRegisters[1]}, :nextbtn  
              
            $instructions  
              
            return-void  
            """.trimIndent(),
            ExternalLabel("nextbtn", getInstruction(gotoLoc)),
        )

        getButtonEntry(this.instructions, debugDialogReference)?.let {
            val loc = it.location.index

            addInstructionsWithLabels(
                loc,
                "if-ne v${condRegisters[0]}, v${condRegisters[1]}, :downloadbtn",
                ExternalLabel("downloadbtn", getInstruction(gotoLoc)),
            )

            removeInstruction(loc + 1)
        } ?: throw PatchException("Failed to get button EntryPoint")
    }
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
