/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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
