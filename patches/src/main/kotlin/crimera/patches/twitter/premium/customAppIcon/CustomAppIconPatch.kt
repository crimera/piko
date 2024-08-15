package crimera.patches.twitter.premium.customAppIcon

import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val customiseAppIconFingerprint = fingerprint {
    strings("current_app_icon_id")
}


// TODO: make a separate integrations
@Suppress("unused")
val customiseAppIcon = bytecodePatch(
    name = "Enable app icon settings",
) {
    dependsOn(settingsPatch, redirectBMTab)
    compatibleWith("com.twitter.android")

    val result by customiseAppIconFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val methods = result.mutableClass.methods.last()
        val loc = methods.instructions.last { it.opcode == Opcode.CONST }.location.index

        //removes toast condition
        methods.removeInstruction(loc)
        methods.removeInstruction(loc - 1)

        settingsStatusMatch.enableSettings("customAppIcon")
    }
}