package crimera.patches.twitter.misc.settings

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.shared.misc.integrations.fingerprint.IntegrationsUtilsFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import crimera.patches.twitter.misc.integrations.IntegrationsPatch
import crimera.patches.twitter.misc.settings.fingerprints.AuthorizeAppActivity
import crimera.patches.twitter.misc.settings.fingerprints.SettingsFingerprint
import crimera.patches.twitter.misc.settings.fingerprints.UrlInterpreterActivity

@Patch(
    description = "Adds settings",
    requiresIntegrations = true,
    dependencies = [SettingsResourcePatch::class, IntegrationsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object SettingsPatch : BytecodePatch(
    setOf(SettingsFingerprint, AuthorizeAppActivity, IntegrationsUtilsFingerprint,UrlInterpreterActivity)
) {
    private const val INTEGRATIONS_PACKAGE = "Lapp/revanced/integrations/twitter"
    private const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Utils"
    private const val ACTIVITY_HOOK_CLASS = "Lapp/revanced/integrations/twitter/settings/ActivityHook;"
    private const val DEEPLINK_HOOK_CLASS = "Lapp/revanced/integrations/twitter/settings/DeepLink;"
    private const val ADD_PREF_DESCRIPTOR =
        "$UTILS_DESCRIPTOR;->addPref([Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;"

    const val PREF_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Pref"
    const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
    const val CUSTOMISE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/customise/Customise"
    const val SSTS_DESCRIPTOR = "invoke-static {}, $INTEGRATIONS_PACKAGE/settings/SettingsStatus;"
    const val FSTS_DESCRIPTOR = "invoke-static {}, $INTEGRATIONS_PACKAGE/patches/FeatureSwitchPatch;"

    private const val START_ACTIVITY_DESCRIPTOR =
        "invoke-static {}, $ACTIVITY_HOOK_CLASS->startSettingsActivity()V"

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

        AuthorizeAppActivity.result?.apply {
            mutableMethod.addInstructionsWithLabels(
                1, """
                invoke-static {p0}, $ACTIVITY_HOOK_CLASS->create(Landroid/app/Activity;)Z
                move-result v0
                if-nez v0, :no_piko_settings_init
                """.trimIndent(),
                ExternalLabel(
                    "no_piko_settings_init",
                    mutableMethod.getInstructions().first { it.opcode == Opcode.RETURN_VOID })
            )
        } ?: throw PatchException("ProxySettingsActivityFingerprint not found")

        UrlInterpreterActivity.result?.apply {
            val instructions = mutableMethod.getInstructions()
            val loc = instructions.first { it.opcode == Opcode.INVOKE_SUPER }.location.index+1
            mutableMethod.addInstructionsWithLabels(
                loc, """
                invoke-static {p0}, $DEEPLINK_HOOK_CLASS->deeplink(Landroid/app/Activity;)Z
                move-result v0
                if-nez v0, :deep_link
                """.trimIndent(),
                ExternalLabel(
                    "deep_link",
                    instructions.first { it.opcode == Opcode.RETURN_VOID })
            )
        } ?: throw PatchException("UrlInterpreterActivity not found")

        IntegrationsUtilsFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SSTS_DESCRIPTOR}->load()V"
        )
    }
}