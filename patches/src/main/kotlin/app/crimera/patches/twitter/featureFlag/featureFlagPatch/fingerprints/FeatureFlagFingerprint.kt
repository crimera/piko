package app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints

import app.revanced.patcher.fingerprint

val featureFlagFingerprint =
    fingerprint {
        strings("feature_switches_configs_crashlytics_enabled")
    }
