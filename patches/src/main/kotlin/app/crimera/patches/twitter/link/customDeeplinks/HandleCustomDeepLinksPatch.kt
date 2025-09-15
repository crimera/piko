package app.crimera.patches.twitter.link.customDeeplinks

import app.crimera.patches.twitter.link.customDeeplinks.fingerprints.urlInterpreterActivityCreateFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val handleCustomDeepLinksPatch =
    bytecodePatch(
        description = "handle custom deeplink",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val bytecode =
                """
                invoke-static {p0}, Lapp/revanced/integrations/twitter/patches/links/HandleCustomDeepLinksPatch;->rewriteCustomDeepLinks(Landroid/app/Activity;)V
                """.trimIndent()

            urlInterpreterActivityCreateFingerprint.method.addInstructions(0, bytecode)
        }
    }
