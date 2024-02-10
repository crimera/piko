package crimera.patches.twitter.link.cleartrackingparams.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

// Reference:
object AddSessionTokenFingerprint: MethodFingerprint(
    strings = listOf(
        "<this>",
        "shareParam",
        "sessionToken"
    )
)