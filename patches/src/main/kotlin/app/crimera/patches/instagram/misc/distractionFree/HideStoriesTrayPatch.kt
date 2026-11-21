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

private object FloatingStoryTrayFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/view/View;", "Ljava/lang/String;", "I"),
    filters = listOf(string("floating_tray_overlay_container")),
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
            listOf(StoryTrayRowsFingerprint.method, FloatingStoryTrayFingerprint.method).forEach { method ->
                val scratch = method.findFreeRegister(0)
                method.addInstructionsWithLabels(
                    0,
                    """
                    $PREF_CALL_DESCRIPTOR->hideStoriesTray()Z
                    move-result v$scratch
                    if-eqz v$scratch, :show_tray
                    return-void
                    """.trimIndent(),
                    ExternalLabel("show_tray", method.getInstruction(0)),
                )
            }
            enableSettings("hideStoriesTray")
        }
    }
