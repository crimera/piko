package app.crimera.patches.twitter.link.customDeeplinks

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private val urlInterpreterActivityCreateFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
        custom { methodDef, _ ->
            methodDef.definingClass == "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;" &&
                    methodDef.name == "onCreate"
        }
    }

@Suppress("unused")
val handleCustomDeepLinksPatch =
    bytecodePatch(
        description = "handle custom deeplink",
    ) {
        dependsOn(settingsPatch)

        execute {

            val bytecode =
                """
                invoke-static {p0}, Lapp/revanced/integrations/twitter/patches/links/HandleCustomDeepLinksPatch;->rewriteCustomDeepLinks(Landroid/app/Activity;)V
                """.trimIndent()

            urlInterpreterActivityCreateFingerprint.method.addInstructions(0, bytecode)
        }
    }
