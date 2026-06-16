/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.disUnifyXChatSystem

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X_11_69
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.is_11_70_or_greater
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import java.util.logging.Logger

internal object XchatSubSystemUserCheckFingerprint : Fingerprint(
    returnType = "Z",
    strings =
        listOf(
            "userId",
            "xchat_unified_tab_min_snowflake_user_id",
        ),
)

@Suppress("unused")
val disUnifyXchatSystemPatch =
    bytecodePatch(
        name = "Disunify xchat system",
        description = "Bring back legacy features like messages and share sheet.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X_11_69)
        dependsOn(settingsPatch, versionCheckPatch)

        execute {
            if (is_11_70_or_greater) {
                return@execute Logger.getLogger(this::class.java.name).warning(
                    "The patch \"Disunify xchat system\" is force succeeded and does not work on any version above 11.69.\n" +
                            "Please unselect the patch if you are using a version higher than 11.69."
                )
            }

            XchatSubSystemUserCheckFingerprint
                .apply {
                    val strIndx = stringMatches.first { it.string == "userId" }.index
                    method.apply {
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
                        enableSettings("disUnifyXChatSystem")
                    }
                }
        }
    }
