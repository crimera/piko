package crimera.patches.twitter.link.browserchooser

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val openLinkFingerprint = fingerprint {
    returns("V")
    parameters("Landroid/content/Context;", "Landroid/content/Intent;", "Landroid/os/Bundle;")
}

@Suppress("unused")
val browserChooserPatch = bytecodePatch(
    name = "Open browser chooser on opening links",
    description = "Instead of open the link directly in one of the installed browsers",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by openLinkFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val inject = """
            invoke-static {p0, p1, p2}, ${PATCHES_DESCRIPTOR}/links/OpenLinksWithAppChooserPatch;->openWithChooser(Landroid/content/Context;Landroid/content/Intent;Landroid/os/Bundle;)V
            return-void
        """.trimIndent()

        result.mutableMethod.addInstructions(0, inject)

        settingsStatusMatch.enableSettings("enableBrowserChooser")
    }
}