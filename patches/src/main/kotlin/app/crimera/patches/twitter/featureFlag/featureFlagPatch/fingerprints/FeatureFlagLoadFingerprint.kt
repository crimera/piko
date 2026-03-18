/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints

import app.morphe.patcher.Fingerprint

internal object FeatureFlagLoadFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/twitter/patches/FeatureSwitchPatch;",
    name = "load",
)
