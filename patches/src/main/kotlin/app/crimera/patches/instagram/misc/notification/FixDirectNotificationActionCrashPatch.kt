/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.notification

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val REMIND_ME_ACTION = "direct_remind_me"

// Builds a message notification and its actions; the like action skips itself without "x", the remind me action does not.
private object DirectNotificationBuilderFingerprint : Fingerprint(
    strings = listOf("notification_builder_start"),
    filters =
        listOf(
            // The check that throws when "x" is null sits just before the action starts.
            opcode(Opcode.IF_EQZ),
            string(REMIND_ME_ACTION, location = MatchAfterWithin(5)),
            // The action ends when it is added to the list of actions of the notification.
            methodCall(name = "add", returnType = "Z", opcode = Opcode.INVOKE_VIRTUAL),
        ),
)

@Suppress("unused")
val fixDirectNotificationActionCrashPatch =
    bytecodePatch(
        description = "Skips the remind me action of a message notification instead of crashing when its link has no \"x\" parameter.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            DirectNotificationBuilderFingerprint.method.apply {
                val implementation = implementation!!
                val guardIndex = DirectNotificationBuilderFingerprint.instructionMatches.first().index
                val addIndex = DirectNotificationBuilderFingerprint.instructionMatches.last().index

                implementation.replaceInstruction(
                    guardIndex,
                    BuilderInstruction21t(
                        Opcode.IF_EQZ,
                        DirectNotificationBuilderFingerprint.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA,
                        implementation.newLabelForIndex(addIndex + 1),
                    ),
                )
            }
        }
    }
