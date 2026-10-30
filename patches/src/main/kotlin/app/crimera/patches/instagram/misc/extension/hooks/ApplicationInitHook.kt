/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.crimera.patches.instagram.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patches.all.misc.extension.ExtensionHook
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

internal val instagramInitHook =
    ExtensionHook(
        fingerprint =
            Fingerprint(
                definingClass = "/InstagramAppShell;",
                name = "onCreate",
            ),
        insertIndexResolver = { method ->
            method.indexOfFirstInstructionOrThrow(Opcode.INVOKE_SUPER) + 1
        },
        contextRegisterResolver = { method ->
            "v${method.getInstruction<FiveRegisterInstruction>(method.indexOfFirstInstructionOrThrow(Opcode.INVOKE_SUPER)).registerC}"
        },
    )
