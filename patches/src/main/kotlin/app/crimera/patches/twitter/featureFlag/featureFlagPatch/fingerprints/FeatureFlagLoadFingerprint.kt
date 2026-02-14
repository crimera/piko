package app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints

import app.morphe.patcher.Fingerprint

internal object FeatureFlagLoadFingerprint : Fingerprint(
    definingClass = "Lapp/revanced/extension/twitter/patches/FeatureSwitchPatch;",
    name = "load"
)
