package crimera.patches.twitter.premium.readermode.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object EnableReaderMode1Fingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "android_audio_protected_account_creation_enabled",
        "subscriptions_feature_1005"
    )
)