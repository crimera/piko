package crimera.patches.twitter.settings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object InitActivityFingerprint: MethodFingerprint(
    strings = listOf("com.twitter.util.config.ApplicationObjectGraphConfig"),
)
