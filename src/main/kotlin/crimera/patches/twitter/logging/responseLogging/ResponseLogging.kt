package crimera.patches.twitter.logging.responseLogging

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object InpStreamFingerprint : MethodFingerprint(
    returnType = "Ljava/io/InputStream",
    customFingerprint = { methodDef, classDef ->
        classDef.type.contains("fasterxml/jackson/core/") &&
            methodDef.parameters.size == 2
    },
)

@Patch(
    name = "Log server response",
    description = "Log json responses received from server",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true,
)
@Suppress("unused")
object RequestLogging : BytecodePatch(
    setOf(SettingsStatusLoadFingerprint, InpStreamFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result = InpStreamFingerprint.result ?: throw PatchException("InpStreamFingerprint is not found")

        result.mutableMethod.addInstructions(
            0,
            """
            invoke-static {p1}, ${SettingsPatch.PATCHES_DESCRIPTOR}/loggers/ResponseLogger;->saveInputStream(Ljava/io/InputStream;)Ljava/io/InputStream;
            move-result-object p1
            """.trimIndent(),
        )

        SettingsStatusLoadFingerprint.enableSettings("serverResponseLogging")
        SettingsStatusLoadFingerprint.enableSettings("serverResponseLoggingOverwriteFile")
    }
}
