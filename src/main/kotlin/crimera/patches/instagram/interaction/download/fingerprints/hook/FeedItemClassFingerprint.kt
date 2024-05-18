package crimera.patches.instagram.interaction.download.fingerprints.hook

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeedItemClassFingerprint : MethodFingerprint(
    customFingerprint = { methodDef, _ ->
        methodDef.parameters.size == 3 &&
                methodDef.parameters[0].toString() == "Lcom/instagram/api/schemas/MediaOptionStyle;" &&
                methodDef.parameters[2].toString() == "Ljava/lang/CharSequence;"
    }
)