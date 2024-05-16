package crimera.patches.instagram.interaction.download

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.util.exception
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31i
import crimera.patches.instagram.interaction.download.fingerprints.FeedItemIconsFingerprint

object AddDownloadIconPatch : BytecodePatch(
    setOf(FeedItemIconsFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        FeedItemIconsFingerprint.result?.mutableMethod?.let { method ->
            val instructions = method.getInstructions()

            val turnOnThirdPartyDownloadsLoc = instructions.first {
                it.opcode == Opcode.CONST_STRING &&
                        (it as BuilderInstruction21c).reference.toString() == "TURN_ON_THIRD_PARTY_DOWNLOADS"
            }.location.index - 1

            val downloadIconIdRegister =
                method.getInstruction<BuilderInstruction31i>(turnOnThirdPartyDownloadsLoc).registerA

            val putInstruction = instructions.let { it[it.lastIndex - 1] }
            val iconsArrayRegister = (putInstruction as BuilderInstruction21c).registerA
//                                name   index: 0x91=145    downloadIconReg
            //     invoke-static {v1, v0, v2}, LX/HgY;->A00(Ljava/lang/String;II)LX/HgY;

//            method.addInstructions(putInstruction.location.index, """
//                    const-string v${iconsArrayRegister+1}, "MEDIA_DOWNLOAD"
//                    const/16 v${iconsArrayRegister+2}, 0x91
//
//                    invokjjj
//                """
//            )

        } ?: throw FeedItemIconsFingerprint.exception
    }
}