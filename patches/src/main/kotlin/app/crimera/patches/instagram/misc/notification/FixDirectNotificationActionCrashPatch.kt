/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.notification

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val ILLEGAL_STATE_EXCEPTION = "Ljava/lang/IllegalStateException;"
private const val REMIND_ME_ACTION = "direct_remind_me"

// Builds a message notification and its actions; the like action skips itself without "x", the remind me action does not.
private object DirectNotificationBuilderFingerprint : Fingerprint(
    filters =
        listOf(
            string("notification_builder_start"),
            string(REMIND_ME_ACTION),
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
                val code = instructions
                val implementation = implementation!!

                val actionIndex = code.indexOfFirst { it.getReference<StringReference>()?.string == REMIND_ME_ACTION }
                if (actionIndex < 0) throw PatchException("Missing the remind me notification action")

                // The check that throws when "x" is null sits just before the action starts.
                val guardIndex =
                    (actionIndex - 1 downTo maxOf(actionIndex - 8, 0)).firstOrNull {
                        code[it].opcode == Opcode.IF_EQZ
                    } ?: throw PatchException("Missing the \"x\" check of the remind me action")
                val guard = code[guardIndex] as BuilderOffsetInstruction
                if (code[guard.target.location.index].getReference<TypeReference>()?.type != ILLEGAL_STATE_EXCEPTION) {
                    throw PatchException("The \"x\" check of the remind me action does not throw")
                }

                // The action ends when it is added to the list of actions of the notification.
                val addIndex =
                    (actionIndex + 1 until code.size).firstOrNull {
                        val method = code[it].getReference<MethodReference>()
                        code[it].opcode == Opcode.INVOKE_VIRTUAL && method?.name == "add" && method.returnType == "Z"
                    } ?: throw PatchException("Missing where the remind me action is added")

                implementation.replaceInstruction(
                    guardIndex,
                    BuilderInstruction21t(
                        Opcode.IF_EQZ,
                        guard.registersUsed.single(),
                        implementation.newLabelForIndex(addIndex + 1),
                    ),
                )
            }
        }
    }
