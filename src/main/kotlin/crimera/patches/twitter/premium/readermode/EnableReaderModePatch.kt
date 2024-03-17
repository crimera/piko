package crimera.patches.twitter.premium.readermode

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.premium.readermode.fingerprints.EnableReaderMode1Fingerprint
import crimera.patches.twitter.premium.readermode.fingerprints.EnableReaderMode2Fingerprint

@Patch(
    name = "Enable Reader Mode",
    description = "Enables reader mode on long threads",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true
)
@Suppress("unused")
object EnableReaderModePatch:BytecodePatch(
    setOf(EnableReaderMode1Fingerprint,EnableReaderMode2Fingerprint, SettingsStatusLoadFingerprint)
){
    private const val GET_PREF_DESCRIPTOR =
        "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->isReaderModeEnabled()Z"

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
//                methods.removeInstruction(loc)
                methods.addInstructions(loc-1,"""
                    $GET_PREF_DESCRIPTOR
                """.trimIndent())

                break
            }
        }


        val result2 = EnableReaderMode2Fingerprint.result
            ?: throw PatchException("EnableReaderMode2Fingerprint not found")

        //remove the flag check
        val methods2 = result2.mutableMethod
        val loc = methods2.getInstructions().first{it.opcode == Opcode.IF_EQZ}.location.index
//        methods2.removeInstruction(loc)
        methods2.addInstructions(loc-1,"""
                    $GET_PREF_DESCRIPTOR
                """.trimIndent())

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/revanced/integrations/twitter/settings/SettingsStatus;->enableReaderMode()V"
        )

        //end
    }


}