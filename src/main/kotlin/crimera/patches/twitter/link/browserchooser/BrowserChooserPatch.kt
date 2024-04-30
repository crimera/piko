package crimera.patches.twitter.link.browserchooser

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.link.browserchooser.fingerprints.OpenLinkFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Open browser chooser on opening links",
    description = "Instead of open the link directly in one of the installed browsers",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class]
)
@Suppress("unused")
object BrowserChooserPatch : BytecodePatch(
    setOf(OpenLinkFingerprint, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        val result = OpenLinkFingerprint.result
            ?: throw IllegalStateException("Fingerprint not found")

        val inject = """
            invoke-static {p0, p1, p2}, ${SettingsPatch.PATCHES_DESCRIPTOR}/links/OpenLinksWithAppChooserPatch;->openWithChooser(Landroid/content/Context;Landroid/content/Intent;Landroid/os/Bundle;)V
            return-void
        """.trimIndent()

        result.mutableMethod.addInstructions(0, inject)

        SettingsStatusLoadFingerprint.result?.mutableMethod?.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->enableBrowserChooser()V"
        ) ?: throw PatchException("SettingsStatusLoadFingerprint not found")
    }
}