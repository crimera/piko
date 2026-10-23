/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.notification

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

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
            NotificationTokenRegistrationFingerprint.matchSingle().let { match ->
                match.method.apply {
                    // Request code and flags share the zero register.
                    val callIndex = match.instructionMatches.first().index
                    val reference = match.instructionMatches.first().methodReference
                    val registers = match.instructionMatches.first().instruction.registersUsed

                    val flagsRegister =
                        getFreeRegisterProvider(
                            index = callIndex,
                            numberOfFreeRegistersNeeded = 1,
                            *registers.toIntArray(),
                        ).getFreeRegister()

                    replaceInstruction(
                        callIndex,
                        "invoke-static {v${registers[0]}, v${registers[1]}, " +
                            "v${registers[2]}, v$flagsRegister}, $reference",
                    )
                    addInstruction(callIndex, "const v$flagsRegister, $FLAG_IMMUTABLE")
                }
            }
        }
    }
