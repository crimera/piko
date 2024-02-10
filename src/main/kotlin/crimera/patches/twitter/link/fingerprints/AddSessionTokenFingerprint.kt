package crimera.patches.twitter.link.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

// Reference:
object AddSessionTokenFingerprint: MethodFingerprint(
    strings = listOf(
        "<this>",
        "shareParam",
        "sessionToken"
    )
)