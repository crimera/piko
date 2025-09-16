package app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints

import app.revanced.patcher.fingerprint

val customAdapterFingerprint =
    fingerprint {
        custom { methodDef, _ ->
            methodDef.definingClass.endsWith("Lapp/revanced/extension/twitter/settings/featureflags/CustomAdapter;") &&
                methodDef.name == "getCount"
        }
    }
