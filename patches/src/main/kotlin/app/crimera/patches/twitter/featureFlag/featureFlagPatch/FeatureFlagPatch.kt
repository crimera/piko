/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.featureFlag.featureFlagPatch

import app.crimera.patches.twitter.misc.extension.twitterInitHook
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.FSTS_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object FeatureFlagFingerprint : Fingerprint(
    filters =
        listOf(
            string("feature_switches_configs_crashlytics_enabled"),
        ),
)

val featureFlagPatch =
    bytecodePatch(
        name = "Hook feature flag",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(featureFlagResourcePatch, settingsPatch)
        execute {

            val methods = FeatureFlagFingerprint.classDef.methods
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

            twitterInitHook.fingerprint.method.addInstruction(
                0,
                "$FSTS_DESCRIPTOR->load()V",
            )

            enableSettings("enableFeatureFlags")
        }
    }
