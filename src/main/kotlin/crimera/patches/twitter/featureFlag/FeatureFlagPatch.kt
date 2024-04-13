package crimera.patches.twitter.featureFlag

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import crimera.patches.twitter.featureFlag.fingerprints.FeatureFlagFingerprint
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch

@Patch(
    name = "Hook feature flag",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [FeatureFlagResourcePatch::class],
    use = true
)
@Suppress("unused")
object FeatureFlagPatch:BytecodePatch(
    setOf(FeatureFlagFingerprint)
) {
    override fun execute(context: BytecodeContext) {

        val result = FeatureFlagFingerprint.result
            ?:throw PatchException("FeatureFlagFingerprint not found")

        val methods = result.mutableClass.methods
        val booleanMethod = methods.first { it.returnType == "Z" && it.parameters == listOf("Ljava/lang/String;","Z") }

        val METHOD = """
            invoke-static {p1,v0}, ${SettingsPatch.PATCHES_DESCRIPTOR}/FeatureSwitchPatch;->flagInfo(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v0
        """.trimIndent()

        val loc = booleanMethod.getInstructions().first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index

        booleanMethod.addInstructions(loc+1,METHOD)
        //end
    }
}