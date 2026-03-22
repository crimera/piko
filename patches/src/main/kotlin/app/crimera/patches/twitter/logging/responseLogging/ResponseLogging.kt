/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.logging.responseLogging

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.shared.Constants.COMPATIBILITY_X
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private object InputStreamFingerprint : Fingerprint(
    definingClass = "/fasterxml/jackson/core/",
    returnType = "Ljava/io/InputStream",
    custom = { methodDef, _ ->
         methodDef.parameters.size == 2
    }
)

@Suppress("unused")
val responseLoggingPatch =
    bytecodePatch(
        name = "Log server response",
        description = "Log json responses received from server",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            InputStreamFingerprint.method.addInstructions(
                0,
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/loggers/ResponseLogger;->saveInputStream(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p1
                """.trimIndent(),
            )
            SettingsStatusLoadFingerprint.enableSettings("serverResponseLogging")

            SettingsStatusLoadFingerprint.enableSettings("serverResponseLoggingOverwriteFile")
        }
    }
