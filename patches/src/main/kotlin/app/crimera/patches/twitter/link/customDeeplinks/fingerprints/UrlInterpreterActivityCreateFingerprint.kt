package app.crimera.patches.twitter.link.customDeeplinks.fingerprints

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

val urlInterpreterActivityCreateFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
        custom { methodDef, _ ->
            methodDef.definingClass == "Lcom/twitter/deeplink/implementation/UrlInterpreterActivity;" &&
                methodDef.name == "onCreate"
        }
    }
