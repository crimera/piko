package crimera.patches.twitter.premium.readermode

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

// TODO: make separate integrations
@Suppress("unused")
val enableReaderModePatch = bytecodePatch(
    name = "Enable Reader Mode",
    description = "Enables \"Reader Mode\" on long threads",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result1 by enableReaderMode1Fingerprint()
    val result2 by enableReaderMode2Fingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val PREF = "invoke-static {}, ${PREF_DESCRIPTOR};->enableReaderMode()Z"

        //find location of the flag
        var strLoc: Int = 0
        result1.stringMatches?.forEach { match ->
            val str = match.string
            if (str.equals("subscriptions_feature_1005")) {
                strLoc = match.index
                return@forEach
            }
        } ?: throw PatchException("No string found")

        if (strLoc == 0) {
            throw PatchException("hook not found")
        }
        //remove the flag check
        val methods = result1.mutableMethod
        val instructions = methods.instructions
        val filters = instructions.filter { it.opcode == Opcode.IF_EQZ }
        for (item in filters) {
            val loc = item.location.index
            if (loc > strLoc) {
                methods.addInstruction(loc - 1, PREF.trimIndent())
                break
            }
        }

        //remove the flag check
        val methods2 = result2.mutableMethod
        val loc = methods2.instructions.first { it.opcode == Opcode.IF_EQZ }.location.index
        methods2.addInstruction(loc - 1, PREF.trimIndent())

        settingsStatusMatch.enableSettings("enableReaderMode")
    }


}