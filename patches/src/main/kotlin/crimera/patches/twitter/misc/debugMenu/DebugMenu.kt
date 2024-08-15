package crimera.patches.twitter.misc.debugMenu

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val debugMenuFingerprint = fingerprint {
    strings(
        "unblock",
        "report",
        "report_dsa",
        "Debug",
        "View in Tweet Sandbox",
        "View in Spaces Sandbox",
    )
}

val debugMenu = bytecodePatch(
    name = "Enable debug menu for posts",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val debugMenuFingerprintMatch by debugMenuFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val methods = debugMenuFingerprintMatch.mutableClass.methods

        val method = methods.first { it.name == "a" }

        val instructions = method.instructions

        val M = "invoke-static{}, ${PREF_DESCRIPTOR};->enableDebugMenu()Z"

        val move_res = instructions.first { it.opcode == Opcode.MOVE_RESULT }.location.index

        method.addInstructions(move_res, M)

        settingsStatusMatch.enableSettings("enableDebugMenu")
    }


}