package crimera.patches.twitter.misc.recommendedusers

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val hideRecommendedUsersFingerprint = fingerprint {
    opcodes(Opcode.IGET_OBJECT)

    custom { it, _ ->
        it.definingClass == "Lcom/twitter/model/json/people/JsonProfileRecommendationModuleResponse;"
    }
}

@Suppress("unused")
val hideRecommendedUsers = bytecodePatch(
    name = "Hide Recommended Users",
    description = "Hide recommended users that pops up when you follow someone",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val hideRecommendedUsersFingerprintMatch by hideRecommendedUsersFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val method = hideRecommendedUsersFingerprintMatch.mutableMethod
        val instructions = method.instructions

        val check = instructions.last { it.opcode == Opcode.IGET_OBJECT }.location.index
        val reg = method.getInstruction<OneRegisterInstruction>(check).registerA

        val HIDE_RECOMMENDED_USERS_DESCRIPTOR =
            "invoke-static {v$reg}, ${PREF_DESCRIPTOR};->hideRecommendedUsers(Ljava/util/ArrayList;)Ljava/util/ArrayList;"

        method.addInstructions(
            check + 1, """
            $HIDE_RECOMMENDED_USERS_DESCRIPTOR
            move-result-object v$reg
        """.trimIndent()
        )

        settingsStatusMatch.enableSettings("hideRecommendedUsers")
    }
}