package crimera.patches.instagram.interaction.download.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeedItemClassNameHookFingerprint: MethodFingerprint(
    customFingerprint = { methodDef, classDef ->  
        methodDef.name == "feedItemClassName"
    }
)