/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.utils.Constants.ACTIVITY_SETTINGS_STATUS_CLASS
import app.crimera.patches.instagram.utils.Constants.HOOK_FLAGS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.morphe.patcher.Fingerprint

internal object HookFlagsLoadFingerprint : Fingerprint(
    definingClass = HOOK_FLAGS_DESCRIPTOR,
    name = "load",
)

internal object SettingsStatusLoadFingerprint : Fingerprint(
    definingClass = ACTIVITY_SETTINGS_STATUS_CLASS,
    name = "load",
)

internal object SignatureCheckExtensionFingerprint : Fingerprint(
    definingClass = LINKS_DESCRIPTOR,
    name = "signatureCheck",
)

// ----------------------

internal object UriTrustingMethodFingerprint : Fingerprint(
    strings = listOf("\' is not trusted: ", "The provider for uri \'"),
)

internal object AppIdentityToStringFingerprint : Fingerprint(
    strings = listOf("AppIdentity{uid=", ", packageNames=", ", sha2=", ", version="),
)

internal object MainFeedFragmentOnCreateFingerprint : Fingerprint(
    name = "onCreate",
    strings = listOf("MainFeedFragment.onCreate"),
)
