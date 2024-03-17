package crimera.patches.twitter.misc.settings

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import crimera.patches.twitter.misc.integrations.IntegrationsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsFingerprint

@Patch(
    description = "Adds settings",
    requiresIntegrations = true,
    dependencies = [SettingsResourcePatch::class, IntegrationsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
@Suppress("unused")
object SettingsPatch : BytecodePatch(
    setOf(SettingsFingerprint)
) {
    private const val INTEGRATIONS_PACKAGE = "Lapp/revanced/integrations/twitter"
    const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Utils"
    const val PREF_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Pref"
    private const val ADD_PREF_DESCRIPTOR = "$UTILS_DESCRIPTOR;->addPref([Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;"
    private const val START_ACTIVITY_DESCRIPTOR =
        "invoke-static {}, $UTILS_DESCRIPTOR;->startSettingsActivity()V"

    override fun execute(context: BytecodeContext) {
        val result = SettingsFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val initMethod = result.mutableClass.methods.first()

        val arrayCreation = initMethod.getInstructions()
            .first { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }.location.index+1

        initMethod.getInstruction<BuilderInstruction11x>(arrayCreation).registerA.also { reg->
            initMethod.addInstructions(arrayCreation+1, """
                const-string v1, "pref_mod"
                invoke-static {v$reg, v1}, $ADD_PREF_DESCRIPTOR
                move-result-object v$reg
            """)
        }

        val prefCLickedMethod = result.mutableClass.methods.find { it.returnType == "Z" }!!
        val constIndex = prefCLickedMethod.getInstructions().first{ it.opcode == Opcode.CONST_4 }.location.index

        prefCLickedMethod.addInstructionsWithLabels(1, """
            const-string v1, "pref_mod" 
            invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v2

            if-nez v2, :start
            goto :cont
            
            :start
            $START_ACTIVITY_DESCRIPTOR
            const/4 v3, 0x1
            return v3 
        """,
            ExternalLabel("cont", prefCLickedMethod.getInstruction(constIndex))
        )
    }
}