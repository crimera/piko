/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.validateLinks

import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal object UriTrustingMethodFingerprint : Fingerprint(
    strings = listOf("\' is not trusted: ", "The provider for uri \'"),
)

internal object UriTrustingCheckFingerprint : Fingerprint(
    classFingerprint = UriTrustingMethodFingerprint,
    returnType = "Z",
    parameters = listOf("L", "Z"),
)

internal object AppIdentityToStringFingerprint : Fingerprint(
    strings = listOf("AppIdentity{uid=", ", sha2=", ", version="),
    filters = listOf(
        string(", packageNames="),
        methodCall(smali = "Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;", location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object SignatureCheckExtensionFingerprint : Fingerprint(
    definingClass = LINKS_DESCRIPTOR,
    name = "signatureCheck",
)
