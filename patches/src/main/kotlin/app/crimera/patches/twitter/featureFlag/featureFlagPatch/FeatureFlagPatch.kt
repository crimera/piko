package app.crimera.patches.twitter.featureFlag.featureFlagPatch

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.FSTS_DESCRIPTOR
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.shared.misc.extension.integrationsUtilsFingerprint
import com.android.tools.smali.dexlib2.Opcode

val featureFlagPatch =
    bytecodePatch(
        name = "Hook feature flag",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(featureFlagResourcePatch, settingsPatch)
        execute {

            val methods = featureFlagFingerprint.classDef.methods
            val booleanMethod = methods.first { it.returnType == "Z" && it.parameters == listOf("Ljava/lang/String;", "Z") }

            val METHOD =
                """
                invoke-static {p1,v0}, $PATCHES_DESCRIPTOR/FeatureSwitchPatch;->flagInfo(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v0
                """.trimIndent()

            val loc =
                booleanMethod.instructions
                    .first { it.opcode == Opcode.MOVE_RESULT_OBJECT }
                    .location.index

            booleanMethod.addInstructions(loc + 1, METHOD)

            integrationsUtilsFingerprint.method.addInstruction(
                1,
                "$FSTS_DESCRIPTOR->load()V",
            )

            settingsStatusLoadFingerprint.enableSettings("enableFeatureFlags")
        }
    }
