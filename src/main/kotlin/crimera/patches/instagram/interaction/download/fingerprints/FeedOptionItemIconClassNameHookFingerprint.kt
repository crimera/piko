package crimera.patches.instagram.interaction.download.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeedOptionItemIconClassNameHookFingerprint: MethodFingerprint(
    customFingerprint = { methodDef, classDef ->
        methodDef.name == "feedOptionItemIconClassName"
    },
)