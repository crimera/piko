package app.crimera.patches.twitter.logging.responseLogging

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
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
        compatibleWith("com.twitter.android")
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
