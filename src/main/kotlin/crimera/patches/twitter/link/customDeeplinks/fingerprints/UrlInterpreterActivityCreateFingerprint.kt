package crimera.patches.twitter.link.customDeeplinks.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object UrlInterpreterActivityCreateFingerprint : MethodFingerprint(
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
    customFingerprint = { methodDef, _ ->
        methodDef.definingClass == "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;"
                && methodDef.name == "onCreate"
    }
)
