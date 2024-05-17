package crimera.patches.instagram.interaction.download.fingerprints.hook

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeedItemClassNameHookFingerprint: MethodFingerprint(
    customFingerprint = { methodDef, classDef ->  
        methodDef.name == "feedItemClassName"
    }
)