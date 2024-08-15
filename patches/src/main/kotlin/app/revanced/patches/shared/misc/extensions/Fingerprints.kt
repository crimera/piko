package app.revanced.patches.shared.misc.extensions

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val revancedUtilsPatchesVersionFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns("Ljava/lang/String;")
    parameters()
    custom { method, _ ->
        method.name == "getPatchesReleaseVersion" && method.definingClass == EXTENSION_CLASS_DESCRIPTOR
    }
}
