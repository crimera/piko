package app.crimera.patches.twitter.misc.disUnifyXChatSystem

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

internal val xchatSubSystemUserCheckFingerprint =
    fingerprint {
        returns("Z")
        strings(
            "userId",
            "xchat_unified_tab_min_snowflake_user_id",
        )
    }

@Suppress("unused")
val disUnifyXchatSystemPatch =
    bytecodePatch(
        name = "Disunify xchat system",
        description = "Bring back legacy features like messages and share sheet.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val strIndx = xchatSubSystemUserCheckFingerprint.stringMatches!!.first { it.string == "userId" }.index
            xchatSubSystemUserCheckFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->disUnifyXChatSystem()Z
                    move-result v0
                    if-nez v0, :piko
                    return v0
                    """.trimIndent(),
                    ExternalLabel("piko", instructions[strIndx]),
                )
                settingsStatusLoadFingerprint.enableSettings("disUnifyXChatSystem")
            }
        }
    }
