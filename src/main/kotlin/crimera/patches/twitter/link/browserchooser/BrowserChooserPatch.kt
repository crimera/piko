package crimera.patches.twitter.link.browserchooser

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.link.browserchooser.fingerprints.OpenLinkFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import java.lang.IllegalStateException

@Patch(
    name = "Open browser chooser on opening links",
    description = "Instead of open the link directly in one of the installed browsers",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object BrowserChooserPatch : BytecodePatch(
    setOf(OpenLinkFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        val result = OpenLinkFingerprint.result
            ?: throw IllegalStateException("Fingerprint not found")

        val inject = """
            invoke-static {p0, p1, p2}, ${SettingsPatch.PATCHES_DESCRIPTOR}/links/OpenLinksWithAppChooserPatch;->openWithChooser(Landroid/content/Context;Landroid/content/Intent;Landroid/os/Bundle;)V
            return-void
        """.trimIndent()

        result.mutableMethod.addInstructions(0, inject)

    }
}