package crimera.patches.twitter.featureFlag

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import app.revanced.patches.shared.misc.integrations.fingerprint.IntegrationsUtilsFingerprint
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.featureFlag.fingerprints.FeatureFlagFingerprint
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
    setOf(FeatureFlagFingerprint, IntegrationsUtilsFingerprint, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        val result = FeatureFlagFingerprint.result
            ?: throw PatchException("FeatureFlagFingerprint not found")

        val methods = result.mutableClass.methods


        fun patch(register: Int) = """
            invoke-static {p1,v0}, ${SettingsPatch.PATCHES_DESCRIPTOR}/FeatureSwitchPatch;->flagInfo(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
        """.trimIndent()
        fun getLoc(method: MutableMethod): Int =
            method.getInstructions().first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index + 1

        listOf("Z", "F", "D").forEach { type ->
            methods.first { it.returnType == type && it.parameters == listOf("Ljava/lang/String;", type) }
                .also { method ->
                    method.addInstructions(getLoc(method), patch(1))
                }
        }


//        Integer idk
        methods.first { it.returnType == "I" && it.parameters == listOf("I", "Ljava/lang/String;") }
            .also { method ->
                method.addInstructions(getLoc(method),
                    """
                        invoke-static {p2,v0}, ${SettingsPatch.PATCHES_DESCRIPTOR}/FeatureSwitchPatch;->flagInfo(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v0
                    """.trimIndent())
            }

//        Long idk
        methods.first { it.returnType == "J" && it.parameters == listOf("J", "Ljava/lang/String;") }
            .also { method ->
                method.addInstructions(getLoc(method),
                    """
                        invoke-static {p3,v0}, ${SettingsPatch.PATCHES_DESCRIPTOR}/FeatureSwitchPatch;->flagInfo(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v0
                    """.trimIndent())
            }

        SettingsStatusLoadFingerprint.enableSettings("enableFeatureFlags")
        IntegrationsUtilsFingerprint.result!!.mutableMethod.addInstruction(
            1,
            "${SettingsPatch.FSTS_DESCRIPTOR}->load()V"
        )

        //end
    }
}