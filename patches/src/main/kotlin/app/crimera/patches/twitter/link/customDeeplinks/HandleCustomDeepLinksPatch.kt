package app.crimera.patches.twitter.link.customDeeplinks

import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private val urlInterpreterActivityCreateFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
        custom { methodDef, _ ->
            methodDef.definingClass == "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;" &&
                    methodDef.name == "onCreate"
        }
    }

val handleCustomDeepLinksPatch =
    bytecodePatch(
        description = "handle custom deeplink",
    ) {
        execute {
            urlInterpreterActivityCreateFingerprint.method.addInstruction(
                0,
                "invoke-static {p0}, $PATCHES_DESCRIPTOR/links/HandleCustomDeepLinksPatch;->rewriteCustomDeepLinks(Landroid/app/Activity;)V"
            )
        }
    }
