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
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val REMIND_ME_ACTION = "direct_remind_me"

// Builds a message notification and its actions; the like action skips itself without "x", the remind me action does not.
private object DirectNotificationBuilderFingerprint : Fingerprint(
    strings = listOf("notification_builder_start"),
    filters =
        listOf(
            // The check that throws when "x" is null sits just before the action starts.
            opcode(Opcode.IF_EQZ),
            string(REMIND_ME_ACTION, location = MatchAfterWithin(5)),
            // Resolve the constructed action before looking for its insertion.
            methodCall(
                name = "<init>",
                parameters = listOf("Landroid/app/PendingIntent;", "Ljava/lang/CharSequence;", "I"),
                returnType = "V",
            ),
        ),
)

@Suppress("unused")
val fixDirectNotificationActionCrashPatch =
    bytecodePatch(
        description = "Skips the remind me action of a message notification instead of crashing when its link has no \"x\" parameter.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val match = DirectNotificationBuilderFingerprint.matchSingle()
            match.method.apply {
                val implementation = implementation!!
                val guardIndex = match.instructionMatches.first().index
                val constructorIndex = match.instructionMatches.last().index
                val constructor = getInstruction(constructorIndex)
                val actionType = ((constructor as ReferenceInstruction).reference as MethodReference).definingClass
                val actionRegister = constructor.registersUsed.first()
                val nextIndex = constructorIndex + 1
                val next = getInstruction(nextIndex)
                val addIndex = if (next.opcode == Opcode.IGET_OBJECT) nextIndex + 1 else nextIndex
                val insertion = getInstruction(addIndex)
                val reference = (insertion as? ReferenceInstruction)?.reference as? MethodReference
                    ?: throw PatchException("Could not resolve notification action insertion")
                val registers = insertion.registersUsed
                val insertsAction = if (addIndex == nextIndex) {
                    reference.parameterTypes == listOf(actionType) && reference.returnType == "V"
                } else {
                    reference.toString() == "Ljava/util/AbstractCollection;->add(Ljava/lang/Object;)Z" &&
                        next.registersUsed.first() == registers.firstOrNull() &&
                        next.registersUsed.first() != actionRegister
                }
                if (insertion.opcode != Opcode.INVOKE_VIRTUAL || registers.size != 2 ||
                    registers.last() != actionRegister || !insertsAction
                ) {
                    throw PatchException("Notification insertion does not consume the constructed action")
                }
                val continuation = addIndex + 1
                // Both native feature guards and the null-link guard must skip the same action.
                val nativeSkips = implementation.instructions.take(guardIndex).count {
                    it is BuilderOffsetInstruction && it.opcode in setOf(Opcode.IF_EQZ, Opcode.IF_NEZ, Opcode.IF_NE, Opcode.IF_GE) &&
                        it.target.location.index == continuation
                }
                if (nativeSkips < 2) {
                    throw PatchException("Notification action continuation does not match native guards")
                }
                implementation.replaceInstruction(
                    guardIndex,
                    BuilderInstruction21t(
                        Opcode.IF_EQZ,
                        match.instructionMatches.first().instruction.registersUsed.single(),
                        implementation.newLabelForIndex(continuation),
                    ),
                )
            }
        }
    }