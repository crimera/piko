/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object NotesTrayBuilderConstructorFingerprint : Fingerprint(
    strings = listOf("NotesTray"),
    returnType = "V",
    name = "<init>",
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
                val strIndex = stringMatches[0].index
                method.apply {

                    val notesRecyclerViewIDIndex =
                        instructions
                            .last { it.location.index < strIndex && it.opcode == Opcode.CONST }
                            .location.index
                    val notesRecyclerViewIndex = indexOfFirstInstruction(notesRecyclerViewIDIndex, Opcode.IPUT_OBJECT)
                    val notesRecyclerViewInstruction = getInstruction(notesRecyclerViewIndex)
                    val notesTrayRegister = notesRecyclerViewInstruction.registersUsed[0]
                    val freeRegister = findFreeRegister(notesRecyclerViewIndex)

                    val iPutObjectInstruction = getInstruction(notesRecyclerViewIndex)

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
