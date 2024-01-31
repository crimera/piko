package crimera.patches.twitter.download

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.download.fingerprints.DownloadPatchFingerprint
import crimera.patches.twitter.download.fingerprints.FIleDownloaderFingerprint

@Patch(
    name = "Download patch",
    description = "Unlocks the ability to download videos from Twitter",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
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
//        instructions.forEach {
//            println(it.opcode)
//        }

        val index = instructions.filter { it.opcode == Opcode.IF_EQ }[1].location.index

        method.addInstructionsWithLabels(
            index + 1,
            """
               const/4 v5, 0x2
               
               if-eq v4, v5, :cond_0  
            """,
            ExternalLabel("cond_0", method.getInstructions().first { it.opcode == Opcode.NEW_INSTANCE })
        )

        instructions.first { it.opcode == Opcode.IGET_BOOLEAN }.location.index.apply {
            method.removeInstruction(this)
            method.removeInstruction(this)
        }

        val f2Result = FIleDownloaderFingerprint.result
            ?: throw PatchException("FIleDownloaderFingerprint not found")

        print(f2Result.classDef.superclass)

        f2Result.mutableClass.methods.forEach {
            if (it.name == "a") {
                // get first if
                val i = it.getInstructions().first { inst -> inst.opcode == Opcode.IF_EQZ }.location.index
                it.addInstructions(i, "const/4 v0, 0x1")
            }
        }
    }
}