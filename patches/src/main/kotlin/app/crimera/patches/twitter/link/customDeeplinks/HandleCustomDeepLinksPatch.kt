package app.crimera.patches.twitter.link.customDeeplinks

import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private object UrlInterpreterActivityCreateFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;",
    name = "onCreate",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

val handleCustomDeepLinksPatch =
    bytecodePatch(
        description = "handle custom deeplink",
    ) {
        execute {
            UrlInterpreterActivityCreateFingerprint.method.addInstruction(
                0,
                "invoke-static {p0}, $PATCHES_DESCRIPTOR/links/HandleCustomDeepLinksPatch;->rewriteCustomDeepLinks(Landroid/app/Activity;)V"
            )
        }
    }
