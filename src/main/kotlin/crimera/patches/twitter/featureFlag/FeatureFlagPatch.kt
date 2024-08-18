package crimera.patches.twitter.featureFlag

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.shared.misc.integrations.fingerprint.IntegrationsUtilsFingerprint
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.featureFlag.fingerprints.*
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Hook feature flag",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [FeatureFlagResourcePatch::class, SettingsPatch::class],
    use = true
)
@Suppress("unused")
object FeatureFlagPatch : BytecodePatch(
    setOf(
        FeatureFlagFingerprint,
        IntegrationsUtilsFingerprint,
        SettingsStatusLoadFingerprint
    )
) {
    override fun execute(context: BytecodeContext) {

        val result = FeatureFlagFingerprint.result ?: throw PatchException("FeatureFlagFingerprint not found")

        val methods = result.mutableClass.methods
        val booleanMethod = methods.first { it.returnType == "Z" && it.parameters == listOf("Ljava/lang/String;", "Z") }

        val METHOD = """
            invoke-static {p1,v0}, ${SettingsPatch.PATCHES_DESCRIPTOR}/FeatureSwitchPatch;->flagInfo(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
        """.trimIndent()

        val loc = booleanMethod.getInstructions().first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index

        booleanMethod.addInstructions(loc + 1, METHOD)

        SettingsStatusLoadFingerprint.enableSettings("enableFeatureFlags")
        IntegrationsUtilsFingerprint.result!!.mutableMethod.addInstruction(
            1, "${SettingsPatch.FSTS_DESCRIPTOR}->load()V"
        )
    }
}