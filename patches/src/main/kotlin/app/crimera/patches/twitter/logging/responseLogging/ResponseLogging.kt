package app.crimera.patches.twitter.logging.responseLogging

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch

internal val inpStreamFingerprint =
    fingerprint {
        returns("Ljava/io/InputStream")
        custom { methodDef, classDef ->
            classDef.type.contains("fasterxml/jackson/core/") &&
                methodDef.parameters.size == 2
        }
    }

@Suppress("unused")
val responseLoggingPatch =
    bytecodePatch(
        name = "Log server response",
        description = "Log json responses received from server",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            inpStreamFingerprint.method.addInstructions(
                0,
                """
                invoke-static {p1}, $PATCHES_DESCRIPTOR/loggers/ResponseLogger;->saveInputStream(Ljava/io/InputStream;)Ljava/io/InputStream;
                move-result-object p1
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.enableSettings("serverResponseLogging")

            settingsStatusLoadFingerprint.enableSettings("serverResponseLoggingOverwriteFile")
        }
    }
