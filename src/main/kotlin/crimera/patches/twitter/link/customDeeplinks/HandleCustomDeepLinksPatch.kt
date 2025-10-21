package crimera.patches.twitter.link.customDeeplinks

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import crimera.patches.twitter.link.customDeeplinks.fingerprints.UrlInterpreterActivityCreateFingerprint

object HandleCustomDeepLinksPatch : BytecodePatch(
    fingerprints = setOf(UrlInterpreterActivityCreateFingerprint),
    requiresIntegrations = true,
) {
    override fun execute(context: BytecodeContext) {
        val fingerprint = UrlInterpreterActivityCreateFingerprint.result
            ?: throw PatchException("UrlInterpreterActivityCreateFingerprint not found")
        val method = fingerprint.mutableMethod

        val bytecode = """
            invoke-static {p0}, Lapp/revanced/integrations/twitter/patches/links/HandleCustomDeepLinksPatch;->rewriteCustomDeepLinks(Landroid/app/Activity;)V
        """.trimIndent()

        method.addInstructions(0, bytecode)
    }
}
