/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.internalStuffs.whitehatSettings

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

internal object AllowUserCertificateCheckFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("debug_allow_user_certs_ttl", "debug_allow_user_certs"),
)

@Suppress("unused")
val allowUserNetworkCertificatePatch =
    bytecodePatch(
        name = "Allow user network certificate",
        description = "Allows user network certificate for whitehat testing",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            hookFlagsPatch,
        )
        execute {

            AllowUserCertificateCheckFingerprint.method.addInstructions(
                0,
                """
                 $PREF_CALL_DESCRIPTOR->allowUserNetworkCertificate()Z
                move-result v0
                return v0
                """.trimIndent(),
            )
            enableSettings("allowUserNetworkCertificate")
        }
    }
