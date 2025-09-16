package app.crimera.patches.twitter.misc.recommendedusers

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal val hideRecommendedUsersFingerprint =
    fingerprint {
        opcodes(
            Opcode.IGET_OBJECT,
        )

        custom { it, _ ->
            it.definingClass == "Lcom/twitter/model/json/people/JsonProfileRecommendationModuleResponse;"
        }
    }

@Suppress("unused")
val hideRecommendedUsers =
    bytecodePatch(
        name = "Hide Recommended Users",
        description = "Hide recommended users that pops up when you follow someone",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = hideRecommendedUsersFingerprint.method
            val instructions = method.instructions

            val check = instructions.last { it.opcode == Opcode.IGET_OBJECT }.location.index
            val reg = (method.getInstruction(check) as OneRegisterInstruction).registerA

            val HIDE_RECOMMENDED_USERS_DESCRIPTOR =
                "invoke-static {v$reg}, $PREF_DESCRIPTOR;->hideRecommendedUsers(Ljava/util/ArrayList;)Ljava/util/ArrayList;"

            method.addInstructions(
                check + 1,
                """
                $HIDE_RECOMMENDED_USERS_DESCRIPTOR
                move-result-object v$reg
                """.trimIndent(),
            )

            settingsStatusLoadFingerprint.method.enableSettings("hideRecommendedUsers")
        }
    }
