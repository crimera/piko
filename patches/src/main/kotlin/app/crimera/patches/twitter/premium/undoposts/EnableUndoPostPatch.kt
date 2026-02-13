package app.crimera.patches.twitter.premium.undoposts

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private val undoPost1Fingerprint =
    fingerprint {
        opcodes(
            Opcode.MOVE_RESULT_OBJECT,
        )
        strings(
            "subscriptions_feature_1003",
            "allow_undo_replies",
            "allow_undo_tweet",
        )

        returns("Z")
    }

private val undoPost2Fingerprint =
    fingerprint {
        strings(
            "userPreferences",
            "draftTweet",
            "subscriptions_feature_1003",
            "allow_undo_replies",
            "allow_undo_tweet",
        )
        returns("Z")
    }

private val undoPost3Fingerprint =
    fingerprint {

        strings(
            "subscriptions_feature_1003",
        )

        returns("Landroid/content/Intent;")
    }

@Suppress("unused")
val enableUndoPostPatch =
    bytecodePatch(
        name = "Enable Undo Posts",
        description = "Enables ability to undo posts before posting",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val PREF = "invoke-static {}, $PREF_DESCRIPTOR;->enableUndoPosts()Z"

            // flag check 1
            val method1 = undoPost1Fingerprint.method
            val loc1 =
                method1
                    .instructions
                    .first { it.opcode == Opcode.IF_EQZ }
                    .location.index
            method1.addInstruction(loc1 - 1, PREF.trimIndent())

            // flag check 2
            val method2 = undoPost2Fingerprint.method
            val loc2 =
                method2
                    .instructions
                    .first { it.opcode == Opcode.IF_EQZ }
                    .location.index
            method2.addInstruction(loc2 - 1, PREF.trimIndent())

            // flag check 3
            val method3 = undoPost3Fingerprint.method
            val loc3 =
                method3
                    .instructions
                    .filter { it.opcode == Opcode.IF_EQZ }[1]
                    .location.index
            method3.addInstruction(loc3, PREF.trimIndent())

            settingsStatusLoadFingerprint.enableSettings("enableUndoPosts")
        }
    }
