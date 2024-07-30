package crimera.patches.twitter.premium.enableForcePip

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object EnableForcePip1Fingerprint: MethodFingerprint(
    strings = listOf(
        "impl",
        "unsupported",
        "android_immersive_media_player_native_pip_enabled"
    )
)

object EnableForcePip2Fingerprint: MethodFingerprint(
    returnType = "Ljava/lang/Object",
    strings = listOf(
        "android_immersive_media_player_native_pip_enabled"
    )
)


@Patch(
    name = "Enable PiP mode automatically",
    description = "Enables PiP mode when you close the app",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true
)
object EnableForcePipPatch:BytecodePatch(
    setOf(EnableForcePip1Fingerprint,EnableForcePip2Fingerprint,SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        val PREF = "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->enableForcePip()Z"

        val result1 = EnableForcePip1Fingerprint.result
            ?:throw PatchException("EnableForcePip1Fingerprint not found")

        val methods1 = result1.mutableMethod
        val first_if_nez_loc = methods1.getInstructions().first{ it.opcode == Opcode.IF_NEZ }.location.index
        methods1.addInstruction(first_if_nez_loc-1,PREF)


        val result2 = EnableForcePip2Fingerprint.result
            ?:throw PatchException("EnableForcePip2Fingerprint not found")

        val methods2 = result2.mutableMethod
        val first_sget_loc = methods2.getInstructions().first{ it.opcode == Opcode.SGET_OBJECT }.location.index
        methods2.addInstruction(first_sget_loc+2,PREF)


        SettingsStatusLoadFingerprint.enableSettings("enableForcePip")
        //end


    }
}