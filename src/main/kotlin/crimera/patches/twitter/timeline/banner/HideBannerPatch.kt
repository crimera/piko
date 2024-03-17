package crimera.patches.twitter.timeline.banner


import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.timeline.banner.fingerprints.HideBannerFingerprint

@Patch(
    name = "Hide Banner",
    description = "Hide new post banner",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
object HideBannerPatch : BytecodePatch(
    setOf(HideBannerFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = HideBannerFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instuctions = method.getInstructions()

        val loc = instuctions.first{it.opcode == Opcode.IF_NEZ}.location.index

//        TODO: make the register dynamic
        val HIDE_BANNER_DESCRIPTOR =
            "invoke-static {}, ${SettingsPatch.UTILS_DESCRIPTOR};->hideBanner()Z"

        method.addInstructions(loc, """
            $HIDE_BANNER_DESCRIPTOR
            move-result v0
        """.trimIndent())

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "invoke-static {}, Lapp/revanced/integrations/twitter/settings/SettingsStatus;->hideBanner()V"
        )

//        method.removeInstruction(loc)

    }
}