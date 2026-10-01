/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.notification

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PENDING_INTENT_CLASS = "Landroid/app/PendingIntent;"
private const val FLAG_IMMUTABLE = 0x04000000
private val GET_BROADCAST_PARAMETERS =
    listOf(
        "Landroid/content/Context;",
        "I",
        "Landroid/content/Intent;",
        "I",
    )

private object NotificationTokenRegistrationFingerprint : Fingerprint(
    returnType = "Landroid/os/Bundle;",
    strings =
        listOf(
            "com.google.iid.TOKEN_REQUEST",
            "com.google.example.invalidpackage",
        ),
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = PENDING_INTENT_CLASS,
                name = "getBroadcast",
                parameters = GET_BROADCAST_PARAMETERS,
                returnType = PENDING_INTENT_CLASS,
            ),
        ),
)

@Suppress("unused")
val fixNotificationRegistrationCrashPatch =
    bytecodePatch(
        description =
            "Prevents Instagram from crashing on launch while setting up push notifications on Android 12 and later.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            NotificationTokenRegistrationFingerprint
                .matchAll(1..1)
                .single()
                .method
                .apply {
                    val calls =
                        instructions.mapIndexedNotNull { index, instruction ->
                            if (instruction.opcode != Opcode.INVOKE_STATIC) return@mapIndexedNotNull null
                            val reference =
                                instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
                            if (
                                reference.definingClass == PENDING_INTENT_CLASS &&
                                reference.name == "getBroadcast" &&
                                reference.parameterTypes == GET_BROADCAST_PARAMETERS &&
                                reference.returnType == PENDING_INTENT_CLASS
                            ) {
                                IndexedValue(index, reference)
                            } else {
                                null
                            }
                        }
                    if (calls.size != 1) {
                        throw PatchException(
                            "Expected one PendingIntent.getBroadcast call, found ${calls.size}",
                        )
                    }

                    val (callIndex, reference) = calls.single()
                    val registers = getInstruction(callIndex).registersUsed
                    if (registers.size != GET_BROADCAST_PARAMETERS.size) {
                        throw PatchException(
                            "PendingIntent.getBroadcast register count does not match its parameters",
                        )
                    }
                    if (registers[1] != registers[3]) {
                        throw PatchException(
                            "Expected notification registration request code and flags to share a register",
                        )
                    }

                    val zeroInstruction = getInstruction(callIndex - 1)
                    if (
                        zeroInstruction.opcode != Opcode.CONST_4 ||
                        (zeroInstruction as? OneRegisterInstruction)?.registerA != registers[3] ||
                        (zeroInstruction as? NarrowLiteralInstruction)?.narrowLiteral != 0
                    ) {
                        throw PatchException(
                            "Expected PendingIntent flags to be initialized to zero immediately before the call",
                        )
                    }

                    val flagsRegister =
                        getFreeRegisterProvider(
                            index = callIndex,
                            numberOfFreeRegistersNeeded = 1,
                            *registers.toIntArray(),
                        ).getFreeRegister()
                    if (flagsRegister > 0xF) {
                        throw PatchException("PendingIntent flags require a 4-bit register")
                    }

                    replaceInstruction(
                        callIndex,
                        "invoke-static {v${registers[0]}, v${registers[1]}, " +
                            "v${registers[2]}, v$flagsRegister}, $reference",
                    )
                    addInstruction(callIndex, "const v$flagsRegister, $FLAG_IMMUTABLE")
                }
        }
    }
