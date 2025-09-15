package app.revanced.patches.shared.misc.settings

import app.revanced.patcher.fingerprint
import app.revanced.patches.shared.misc.extension.EXTENSION_CLASS_DESCRIPTOR
import com.android.tools.smali.dexlib2.AccessFlags

internal val themeLightColorResourceNameFingerprint = fingerprint {
    accessFlags(AccessFlags.PRIVATE, AccessFlags.STATIC)
    returns("Ljava/lang/String;")
    parameters()
    custom { method, classDef ->
        method.name == "getThemeLightColorResourceName" && classDef.type == EXTENSION_CLASS_DESCRIPTOR
    }
}

internal val themeDarkColorResourceNameFingerprint = fingerprint {
    accessFlags(AccessFlags.PRIVATE, AccessFlags.STATIC)
    returns("Ljava/lang/String;")
    parameters()
    custom { method, classDef ->
        method.name == "getThemeDarkColorResourceName" && classDef.type == EXTENSION_CLASS_DESCRIPTOR
    }
}
