package app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints

import app.revanced.patcher.fingerprint

val featureFlagLoadFingerprint =
    fingerprint {
        custom { methodDef, _ ->
            methodDef.definingClass.endsWith("Lapp/revanced/extension/twitter/patches/FeatureSwitchPatch;") &&
                methodDef.name == "load"
        }
    }
