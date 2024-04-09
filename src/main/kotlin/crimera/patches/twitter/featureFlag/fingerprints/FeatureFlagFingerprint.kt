package crimera.patches.twitter.featureFlag.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object FeatureFlagFingerprint :MethodFingerprint(
    strings = listOf("feature_switches_configs_crashlytics_enabled")
)