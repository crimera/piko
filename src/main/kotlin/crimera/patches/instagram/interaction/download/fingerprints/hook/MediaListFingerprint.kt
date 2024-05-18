package crimera.patches.instagram.interaction.download.fingerprints.hook

import app.revanced.patcher.fingerprint.MethodFingerprint

object MediaListFingerprint : MethodFingerprint(
    strings = listOf("XDTMediaDict"),
    customFingerprint = { methodDef, classDef ->
        classDef.interfaces.size == 2
    }
)