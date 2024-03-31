package crimera.patches.twitter.link.customsharingdomain

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.link.customsharingdomain.fingerprints.CustomSharingDomainFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Custom sharing domain",
    description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
object CustomSharingDomainPatch: BytecodePatch(
    setOf(CustomSharingDomainFingerprint, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = CustomSharingDomainFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val setUsernameLoc = result.scanResult.stringsScanResult?.matches?.first()?.index
            ?: throw PatchException("setUserNameLoc not found")

        result.mutableMethod.also {
            val reg = it.getInstruction<OneRegisterInstruction>(setUsernameLoc-1).registerA
            val getSharingLinkDescriptor =
                "invoke-static {v$reg}, ${SettingsPatch.PREF_DESCRIPTOR};->getSharingLink(Ljava/lang/String;)Ljava/lang/String;"
            it.addInstructions(
                setUsernameLoc,
                """
                    $getSharingLinkDescriptor
                    move-result-object v$reg
                """.trimIndent()
            )
        }

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->enableCustomSharingDomain()V"
        )
    }
}
