package app.crimera.patches.twitter.premium.enableForcePip

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object EnableForcePip1Fingerprint : Fingerprint(
    strings = listOf(
        "impl",
        "unsupported",
        "android_immersive_media_player_native_pip_enabled",
    )
)

private object EnableForcePip2Fingerprint : Fingerprint(
    returnType = "Ljava/lang/Object",
    strings = listOf("android_immersive_media_player_native_pip_enabled")
)

@Suppress("unused")
val enableForcePipPatch =
    bytecodePatch(
        name = "Enable PiP mode automatically",
        description = "Enables PiP mode when you close the app",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val PREF = "invoke-static {}, $PREF_DESCRIPTOR;->enableForcePip()Z"

            val methods1 = EnableForcePip1Fingerprint.method
            val first_if_nez_loc =
                methods1.instructions
                    .first { it.opcode == Opcode.IF_NEZ }
                    .location.index
            methods1.addInstruction(first_if_nez_loc - 1, PREF)

            val methods2 = EnableForcePip2Fingerprint.method
            val first_sget_loc =
                methods2.instructions
                    .first { it.opcode == Opcode.SGET_OBJECT }
                    .location.index
            methods2.addInstruction(first_sget_loc + 2, PREF)

            SettingsStatusLoadFingerprint.enableSettings("enableForcePip")
        }
    }
