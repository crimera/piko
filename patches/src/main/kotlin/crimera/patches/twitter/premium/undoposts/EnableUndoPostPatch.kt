package crimera.patches.twitter.premium.undoposts

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

// TODO: make a separate integration
@Suppress("unused")
val enableUndoPostPatch = bytecodePatch(
    name = "Enable Undo Posts",
    description = "Enables ability to undo posts before posting",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result1 by undoPost1Fingerprint()
    val result2 by undoPost2Fingerprint()
    val result3 by undoPost3Fingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {

        val PREF = "invoke-static {}, ${PREF_DESCRIPTOR};->enableUndoPosts()Z"

        //flag check 1
        val method1 = result1.mutableMethod
        val loc1 = method1.instructions.first { it.opcode == Opcode.IF_EQZ }.location.index
        method1.addInstruction(loc1 - 1, PREF.trimIndent())

        //flag check 2
        val method2 = result2.mutableMethod
        val loc2 = method2.instructions.first { it.opcode == Opcode.IF_EQZ }.location.index
        method2.addInstruction(loc2 - 1, PREF.trimIndent())

        //flag check 3
        val method3 = result3.mutableMethod
        val loc3 = method3.instructions.last { it.opcode == Opcode.INVOKE_STATIC }.location.index
        method3.addInstruction(loc3 - 1, PREF.trimIndent())


        settingsStatusMatch.enableSettings("enableUndoPosts")
    }

}