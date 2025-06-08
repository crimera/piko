package crimera.patches.twitter.premium.undoposts

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.premium.undoposts.fingerprints.UndoPost1Fingerprint
import crimera.patches.twitter.premium.undoposts.fingerprints.UndoPost2Fingerprint
import crimera.patches.twitter.premium.undoposts.fingerprints.UndoPost3Fingerprint

@Patch(
    name = "Enable Undo Posts",
    description = "Enables ability to undo posts before posting",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true
)
object EnableUndoPostPatch :BytecodePatch(
    setOf(UndoPost1Fingerprint,UndoPost2Fingerprint,UndoPost3Fingerprint,SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {

        val result1 = UndoPost1Fingerprint.result
            ?: throw PatchException("UndoPost1Fingerprint not found")

        val PREF = "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->enableUndoPosts()Z"

        //flag check 1
        val method1 = result1.mutableMethod
        val loc1 = method1.getInstructions().first { it.opcode == Opcode.IF_EQZ }.location.index
        method1.addInstruction(loc1-1,PREF.trimIndent())



        val result2 = UndoPost2Fingerprint.result
            ?: throw PatchException("UndoPost2Fingerprint not found")

        //flag check 2
        val method2 = result2.mutableMethod
        val loc2 = method2.getInstructions().first { it.opcode == Opcode.IF_EQZ }.location.index
        method2.addInstruction(loc2-1,PREF.trimIndent())



        val result3 = UndoPost3Fingerprint.result
            ?: throw PatchException("UndoPost3Fingerprint not found")

        //flag check 3
        val method3 = result3.mutableMethod
        val loc3 = method3.getInstructions().filter { it.opcode == Opcode.IF_EQZ }[1].location.index
        method3.addInstruction(loc3,PREF.trimIndent())


        SettingsStatusLoadFingerprint.enableSettings("enableUndoPosts")
        //end
    }

}