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
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

object MainFeedStoryTrayBinderGroupViewBinderFingerprint : Fingerprint(
    strings = listOf("MainFeedStoryTrayBinderGroup", "litho_main_feed_stories_tray"),
    filters = listOf(
        // Stories tray view group.
        fieldAccess(type = "Landroid/view/ViewGroup;", opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        string("floating_tray_spacer", location = MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val hideStoriesTrayPatch =
    bytecodePatch(
        name = "Hide stories tray",
        description = "Hides stories tray from main feed.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            MainFeedStoryTrayBinderGroupViewBinderFingerprint.apply {
                method.apply {
                    val iGetObjectIndex = instructionMatches.first().index
                    val targetIndex = iGetObjectIndex + 1

                    val storiesTrayViewGroupRegistry = instructionMatches.first().getInstruction<TwoRegisterInstruction>().registerA
                    val freeRegister = instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA

                    addInstructionsWithLabels(
                        targetIndex,
                        """
                        $PREF_CALL_DESCRIPTOR->hideStoriesTray()Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko
                        const v$storiesTrayViewGroupRegistry, 0x0
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(targetIndex)),
                    )

                    enableSettings("hideStoriesTray")
                }
            }
        }
    }
