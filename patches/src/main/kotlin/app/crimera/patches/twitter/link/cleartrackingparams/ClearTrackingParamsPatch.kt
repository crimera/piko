/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.link.cleartrackingparams

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.shared.Constants.COMPATIBILITY_X
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

// https://github.com/FrozenAlex/revanced-patches-new
internal object AddSessionTokenFingerprint : Fingerprint(
    parameters = listOf(
        "Ljava/lang/String;",
        "L",
        "Ljava/lang/String;"
    ),
    returnType = "Ljava/lang/String;",
    strings = listOf(
        "<this>",
        "shareParam",
        "sessionToken",
    )
)

@Suppress("unused")
val clearTrackingParamsPatch =
    bytecodePatch(
        name = "Clear tracking params",
        description = "Removes tracking parameters when sharing links",
        default = true
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            AddSessionTokenFingerprint.method.addInstruction(0, "return-object p0")

            SettingsStatusLoadFingerprint.enableSettings("cleartrackingparams")
        }
    }
