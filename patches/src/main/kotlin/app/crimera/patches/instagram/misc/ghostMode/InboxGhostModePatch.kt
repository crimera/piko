/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

val inboxGhostModePatch =
    bytecodePatch(
        description = "Hooks the inbox button on the main feed to toggle ghost mode on long press.",
    ) {
        dependsOn(resourceMappingPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            InboxButtonFingerprint.matchAll().forEach { match ->
                match.method.apply {
                    match.instructionMatches.reversed().forEach { instructionMatch ->
                        val setIdIndex = indexOfFirstInstruction(instructionMatch.index, Opcode.INVOKE_VIRTUAL)
                        val setIdInstruction = getInstruction(setIdIndex)
                        val viewRegister = setIdInstruction.registersUsed[0]

                        addInstruction(
                            setIdIndex + 1,
                            "invoke-static {v$viewRegister}, $ACTIONBAR_DESCRIPTOR->hookInboxButton(Landroid/view/View;)V",
                        )
                    }
                }
            }

            CreateTabButtonFingerprint.matchAll().forEach { match ->
                match.method.apply {
                    instructions.filter { it.opcode == Opcode.RETURN_OBJECT }.reversed().forEach {
                        val returnIndex = it.location.index
                        val viewRegister = it.registersUsed[0]

                        addInstruction(
                            returnIndex,
                            "invoke-static {v$viewRegister}, $ACTIONBAR_DESCRIPTOR->hookTabButton(Landroid/view/View;)V",
                        )
                    }
                }
            }

        }
    }
