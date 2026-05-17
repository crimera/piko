/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.OpcodeFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal object MessageOnKeyFingerprint : Fingerprint(
    returnType = "Z",
    name = "onKey",
    custom = { method, _ ->
        method.implementation?.registerCount == 6
    },
    filters =
        listOf(
            opcode(Opcode.CONST_4, MatchFirst()),
            opcode(Opcode.INVOKE_STATIC, MatchAfterImmediately()),
        ),
)

@Suppress("unused")
val disableDoubleTapOnMessagePatch =
    bytecodePatch(
        description = "Disable double tap like on messages",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            MessageOnKeyFingerprint.apply {
                classDef.methods.first { it.name == "onDoubleTap" }.apply {
                    addInstructions(
                        0,
                        DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapMessage"),
                    )
                }
            }
        }
    }
