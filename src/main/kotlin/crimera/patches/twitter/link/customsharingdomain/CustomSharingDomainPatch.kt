package crimera.patches.twitter.link.customsharingdomain

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.link.cleartrackingparams.fingerprints.AddSessionTokenFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Custom sharing domain",
    description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class],
)
object CustomSharingDomainPatch: BytecodePatch(
    setOf(AddSessionTokenFingerprint, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        val getSharingLinkDescriptor =
            "invoke-static {p0}, ${SettingsPatch.PREF_DESCRIPTOR};->getSharingLink(Ljava/lang/String;)Ljava/lang/String;"

        val addSessionTokenResult = AddSessionTokenFingerprint.result
            ?: throw PatchException("setUserNameLoc not found")

        addSessionTokenResult.mutableMethod.addInstructions(
            0,
            """
                $getSharingLinkDescriptor
                move-result-object p0
            """.trimIndent()
        )

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->enableCustomSharingDomain()V"
        )
    }
}