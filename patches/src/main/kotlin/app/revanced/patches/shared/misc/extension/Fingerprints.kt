package app.revanced.patches.shared.misc.extension

import app.morphe.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val revancedUtilsPatchesVersionFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
        returns("Ljava/lang/String;")
        parameters()
        custom { method, _ ->
            method.name == "getPatchesReleaseVersion" && method.definingClass == EXTENSION_CLASS_DESCRIPTOR
        }
    }

internal val integrationsUtilsFingerprint =
    fingerprint {
        returns("V")
        custom { method, _ ->
            method.definingClass.contains(EXTENSION_CLASS_DESCRIPTOR) && method.name == "load"
        }
    }
