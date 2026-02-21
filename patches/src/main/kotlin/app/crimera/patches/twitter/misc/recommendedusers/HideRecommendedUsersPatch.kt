package app.crimera.patches.twitter.misc.recommendedusers

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object HideRecommendedUsersFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/people/JsonProfileRecommendationModuleResponse;",
    filters = listOf(
        opcode(Opcode.IGET_OBJECT)
    )
)

@Suppress("unused")
val hideRecommendedUsers =
    bytecodePatch(
        name = "Hide Recommended Users",
        description = "Hide recommended users that pops up when you follow someone",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = HideRecommendedUsersFingerprint.method
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

            SettingsStatusLoadFingerprint.enableSettings("hideRecommendedUsers")
        }
    }
