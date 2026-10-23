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
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.findFreeRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

object NotesTrayBuilderConstructorFingerprint : Fingerprint(
    filters =
        listOf(
            resourceLiteral(ResourceType.ID, "cf_hub_recycler_view"),
            // Notes tray view.
            fieldAccess(opcode = Opcode.IPUT_OBJECT),
        ),
)

@Suppress("unused")
val hideNotesTrayPatch =
    bytecodePatch(
        name = "Hide notes tray",
        description = "Hides notes tray in DM section",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            NotesTrayBuilderConstructorFingerprint.apply {

                method.apply {
                    val notesRecyclerViewIndex = instructionMatches.last().index
                    val iPutObjectInstruction = getInstruction<TwoRegisterInstruction>(notesRecyclerViewIndex)
                    val notesTrayRegister = iPutObjectInstruction.registerA
                    val freeRegister = findFreeRegister(notesRecyclerViewIndex)

                    addInstructionsWithLabels(
                        notesRecyclerViewIndex,
                        """
                        ${PREF_CALL_DESCRIPTOR}->hideNotesTray()Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko
                        const/16 v$freeRegister, 0x8
                        invoke-virtual {v$notesTrayRegister, v$freeRegister}, Landroid/view/View;->setVisibility(I)V
                        """.trimIndent(),
                        ExternalLabel("piko", iPutObjectInstruction),
                    )

                    enableSettings("hideNotesTray")
                }
            }
        }
    }
