package crimera.patches.twitter.premium.readermode

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.premium.readermode.fingerprints.EnableReaderMode1Fingerprint
import crimera.patches.twitter.premium.readermode.fingerprints.EnableReaderMode2Fingerprint

@Patch(
    name = "Enable Reader Mode",
    description = "Enables reader mode on long threads",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
object EnableReaderModePatch:BytecodePatch(
    setOf(EnableReaderMode1Fingerprint,EnableReaderMode2Fingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result1 = EnableReaderMode1Fingerprint.result
            ?: throw PatchException("EnableReaderMode1Fingerprint not found")

        //find location of the flag
        var strLoc: Int = 0
        result1.scanResult.stringsScanResult!!.matches.forEach{ match ->
            val str = match.string
            if(str.equals("subscriptions_feature_1005")){
                strLoc = match.index
                return@forEach
            }
        }

        if(strLoc==0){
            throw PatchException("hook not found")
        }
        //remove the flag check
        val methods = result1.mutableMethod
        val instructions = methods.getInstructions()
        val filters = instructions.filter { it.opcode == Opcode.IF_EQZ }
        for(item in filters){
            val loc = item.location.index
            if(loc > strLoc){
                methods.removeInstruction(loc)
                break
            }
        }


        val result2 = EnableReaderMode2Fingerprint.result
            ?: throw PatchException("EnableReaderMode2Fingerprint not found")

        //remove the flag check
        val methods2 = result2.mutableMethod
        val loc = methods2.getInstructions().first{it.opcode == Opcode.IF_EQZ}.location.index
        methods2.removeInstruction(loc)


        //end
    }


}