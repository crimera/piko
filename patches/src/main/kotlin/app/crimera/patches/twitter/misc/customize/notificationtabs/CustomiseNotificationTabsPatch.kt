/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.customize.notificationtabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

private object CustomiseNotificationTabsFingerprint : Fingerprint(
    strings =
        listOf(
            "android_ntab_verified_tab_enabled",
            "all",
            "mentions",
        ),
)

@Suppress("unused")
val customiseNotificationTabsPatch =
    bytecodePatch(
        name = "Customize notification tabs",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            CustomiseNotificationTabsFingerprint.method.apply {
                val strIndex = CustomiseNotificationTabsFingerprint.stringMatches[2].index
                val index = indexOfFirstInstruction(strIndex, Opcode.CHECK_CAST)
                val reg = getInstruction(index).registersUsed[0]

                addInstructions(
                    index + 1,
                    """
                    invoke-static {v$reg}, $CUSTOMISE_DESCRIPTOR;->notificationTabs(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$reg
                    """.trimIndent(),
                )
                enableSettings("notificationTabCustomisation")
            }
        }
    }
