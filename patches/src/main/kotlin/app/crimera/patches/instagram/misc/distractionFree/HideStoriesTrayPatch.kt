/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.smali.ExternalLabel

private object StoryTrayRowsFingerprint : Fingerprint(
    name = "buildRowViewTypes",
    returnType = "V",
    filters = listOf(string("MainFeedStoryTrayBinderGroup.buildRowViewTypes")),
)

@Suppress("unused")
val hideStoriesTrayPatch =
    patch(
        name = "Hide stories tray",
        description = "Hides stories tray from main feed.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            StoryTrayRowsFingerprint.method.apply {
                val scratch = findFreeRegister(0)
                addInstructionsWithLabels(
                    0,
                    """
                    $PREF_CALL_DESCRIPTOR->hideStoriesTray()Z
                    move-result v$scratch
                    if-eqz v$scratch, :native_rows
                    return-void
                    """.trimIndent(),
                    ExternalLabel("native_rows", getInstruction(0)),
                )
            }
            enableSettings("hideStoriesTray")
        }
    }
