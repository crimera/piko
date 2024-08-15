package crimera.patches.twitter.premium.enableForcePip

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val enableForcePipPatch = bytecodePatch(
    name = "Enable PiP mode automatically",
    description = "Enables PiP mode when you close the app",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result1 by enableForcePip1Fingerprint()
    val result2 by enableForcePip2Fingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {

        val PREF = "invoke-static {}, ${PREF_DESCRIPTOR};->enableForcePip()Z"

        val methods1 = result1.mutableMethod
        val first_if_nez_loc = methods1.instructions.first { it.opcode == Opcode.IF_NEZ }.location.index
        methods1.addInstruction(first_if_nez_loc - 1, PREF)

        val methods2 = result2.mutableMethod
        val first_sget_loc = methods2.instructions.first { it.opcode == Opcode.SGET_OBJECT }.location.index
        methods2.addInstruction(first_sget_loc + 2, PREF)

        settingsStatusMatch.enableSettings("enableForcePip")
    }
}