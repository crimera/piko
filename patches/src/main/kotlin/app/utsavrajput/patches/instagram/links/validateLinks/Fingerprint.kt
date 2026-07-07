/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.instagram.links.validateLinks

import app.utsavrajput.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.morphe.patcher.Fingerprint

internal object UriTrustingMethodFingerprint : Fingerprint(
    strings = listOf("\' is not trusted: ", "The provider for uri \'"),
)

internal object AppIdentityToStringFingerprint : Fingerprint(
    strings = listOf("AppIdentity{uid=", ", packageNames=", ", sha2=", ", version="),
)

internal object SignatureCheckExtensionFingerprint : Fingerprint(
    definingClass = LINKS_DESCRIPTOR,
    name = "signatureCheck",
)
