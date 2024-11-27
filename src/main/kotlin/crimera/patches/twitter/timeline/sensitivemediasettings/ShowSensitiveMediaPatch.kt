package crimera.patches.twitter.timeline.sensitivemediasettings

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.timeline.sensitivemediasettings.fingerprints.SensitiveMediaSettingsPatchFingerprint

// Credits to @Cradlesofashes
@Patch(
    name = "Show sensitive media",
    description = "Shows sensitive media",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
@Suppress("unused")
object SensitiveMediaPatch : BytecodePatch(
    setOf(SensitiveMediaSettingsPatchFingerprint, SettingsStatusLoadFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val TIMELINE_ENTRY_DESCRIPTOR = "${SettingsPatch.PATCHES_DESCRIPTOR}/TimelineEntry"

        val result =
            SensitiveMediaSettingsPatchFingerprint.result
                ?: throw PatchException("SensitiveMediaSettingsPatchFingerprint not found")

        val methods = result.mutableMethod
        val instructions = methods.getInstructions()

        val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

        methods.addInstructions(
            returnObj,
            """
            invoke-static {p1}, $TIMELINE_ENTRY_DESCRIPTOR;->sensitiveMedia(Lcom/twitter/model/json/core/JsonSensitiveMediaWarning;)Lcom/twitter/model/json/core/JsonSensitiveMediaWarning;
            move-result-object p1
            """.trimIndent(),
        )

        SettingsStatusLoadFingerprint.enableSettings("showSensitiveMedia")
    }
}
