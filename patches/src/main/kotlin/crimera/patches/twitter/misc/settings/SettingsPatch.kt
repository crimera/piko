package crimera.patches.twitter.misc.settings

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import crimera.patches.twitter.misc.extensions.sharedExtensionPatch

private const val INTEGRATIONS_PACKAGE = "Lapp/revanced/integrations/twitter"
const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Utils"
private const val ACTIVITY_HOOK_CLASS = "Lapp/revanced/integrations/twitter/settings/ActivityHook;"
private const val ADD_PREF_DESCRIPTOR =
    "$UTILS_DESCRIPTOR;->addPref([Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;"

const val PREF_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Pref"
const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
const val CUSTOMISE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/customise/Customise"
const val SSTS_DESCRIPTOR = "invoke-static {}, $INTEGRATIONS_PACKAGE/settings/SettingsStatus;"
const val FSTS_DESCRIPTOR = "invoke-static {}, $INTEGRATIONS_PACKAGE/patches/FeatureSwitchPatch;"

private const val START_ACTIVITY_DESCRIPTOR =
    "invoke-static {}, $ACTIVITY_HOOK_CLASS->startSettingsActivity()V"

val settingsPatch =
    bytecodePatch(
        description = "Adds settings",
    ) {
        dependsOn(settingsResourcePatch, sharedExtensionPatch)
        compatibleWith("com.twitter.android")

        val settingsMatch by settingsFingerprint()
        val authorizeAppActivityMatch by authorizeAppActivity()
        val integrationUtilsFingerprintMatch by integrationsUtilsFingerprint()

        execute {
            val initMethod = settingsMatch.mutableClass.methods.first()

            val arrayCreation =
                initMethod.instructions
                    .first { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }
                    .location.index + 1

            initMethod.getInstruction<BuilderInstruction11x>(arrayCreation).registerA.also { reg ->
                initMethod.addInstructions(
                    arrayCreation + 1,
                    """
                const-string v1, "pref_mod"
                invoke-static {v$reg, v1}, $ADD_PREF_DESCRIPTOR
                move-result-object v$reg
            """,
                )
            }

            val prefCLickedMethod = settingsMatch.mutableClass.methods.find { it.returnType == "Z" }!!
            val constIndex =
                prefCLickedMethod.instructions
                    .first { it.opcode == Opcode.CONST_4 }
                    .location.index

            prefCLickedMethod.addInstructionsWithLabels(
                1,
                """
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
                ExternalLabel("cont", prefCLickedMethod.getInstruction(constIndex)),
            )

            authorizeAppActivityMatch.apply {
                mutableMethod.addInstructionsWithLabels(
                    1,
                    """
                    invoke-static {p0}, $ACTIVITY_HOOK_CLASS->create(Landroid/app/Activity;)Z
                    move-result v0
                    if-nez v0, :no_piko_settings_init
                    """.trimIndent(),
                    ExternalLabel(
                        "no_piko_settings_init",
                        mutableMethod.instructions.first { it.opcode == Opcode.RETURN_VOID },
                    ),
                )
            }

            integrationUtilsFingerprintMatch.mutableMethod.addInstruction(
                0,
                "${SSTS_DESCRIPTOR}->load()V",
            )
        }
    }
