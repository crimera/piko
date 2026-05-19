/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints

import app.morphe.patcher.Fingerprint

internal object FeatureFlagLoadFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/twitter/patches/FeatureSwitchPatch;",
    name = "load",
)
