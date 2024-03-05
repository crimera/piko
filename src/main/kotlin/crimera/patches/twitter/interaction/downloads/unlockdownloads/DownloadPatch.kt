package crimera.patches.twitter.interaction.downloads.unlockdownloads

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import crimera.patches.twitter.interaction.downloads.unlockdownloads.fingerprints.DownloadPatchFingerprint
import crimera.patches.twitter.interaction.downloads.unlockdownloads.fingerprints.FIleDownloaderFingerprint

// Credits to @iKirby
@Patch(
    name = "Download patch",
    description = "Unlocks the ability to download videos and gifs from Twitter/X",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
object DownloadPatch : BytecodePatch(
    setOf(DownloadPatchFingerprint, FIleDownloaderFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = DownloadPatchFingerprint.result
            ?: throw PatchException("DownloadPatchFingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        val first_if_loc = instructions.first { it.opcode == Opcode.IF_EQ }.location.index
        val reg = method.getInstruction<TwoRegisterInstruction>(first_if_loc)
        val r1 = reg.registerA
        val r2 = reg.registerB

        //add support for gif
        method.addInstructionsWithLabels(
            first_if_loc + 1,
            """
               const/4 v$r2, 0x2
               
               if-eq v$r1, v$r2, :cond_1212
            """,
            ExternalLabel("cond_1212", method.getInstructions().first { it.opcode == Opcode.NEW_INSTANCE })
        )
        
        //enable download for all media
        instructions.first { it.opcode == Opcode.IGET_BOOLEAN }.location.index.apply {
            method.removeInstruction(this)
            method.removeInstruction(this)
        }
        
        val f2Result = FIleDownloaderFingerprint.result
            ?: throw PatchException("FIleDownloaderFingerprint not found")


        val method2 = f2Result.mutableMethod
        val instructions2 = method2.getInstructions()
        val first_if2_loc = instructions2.first { it.opcode == Opcode.IF_EQZ }.location.index
        val r3 = method2.getInstruction<OneRegisterInstruction>(first_if2_loc).registerA
        
        //remove premium restriction
        method2.addInstructions(first_if2_loc,"""
            const v$r3, true
        """.trimIndent())
    }
}