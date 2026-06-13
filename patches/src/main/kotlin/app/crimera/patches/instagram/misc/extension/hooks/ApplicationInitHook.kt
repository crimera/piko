/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
*/

package app.crimera.patches.instagram.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patches.all.misc.extension.ExtensionHook
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal val instagramInitHook =
    ExtensionHook(
        fingerprint =
            Fingerprint(
                name = "onCreate",
                custom = { _, classDef ->
                    classDef.endsWith("/InstagramAppShell;")
                },
            ),
        insertIndexResolver = { method ->
            val index = method.instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.INVOKE_SUPER
            }
            if (index == -1) throw PatchException("Failed to find INVOKE_SUPER in ${method.name}")
            index + 1
        },
        contextRegisterResolver = { method ->
            val invokeSuperInstruction =
                method.instructions.firstOrNull { instruction ->
                    instruction.opcode == Opcode.INVOKE_SUPER
                } ?: throw PatchException("Failed to find INVOKE_SUPER in ${method.name}")

            "v${invokeSuperInstruction.registersUsed[0]}"
        },
    )
