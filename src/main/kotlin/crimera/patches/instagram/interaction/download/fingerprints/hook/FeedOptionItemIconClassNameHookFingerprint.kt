package crimera.patches.instagram.interaction.download.fingerprints.hook

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeedOptionItemIconClassNameHookFingerprint: MethodFingerprint(
    customFingerprint = { methodDef, classDef ->
        methodDef.name == "feedOptionItemIconClassName"
    },
)