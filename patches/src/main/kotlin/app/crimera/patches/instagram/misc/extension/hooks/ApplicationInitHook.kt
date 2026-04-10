/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
*/

package app.crimera.patches.instagram.misc.extension.hooks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.shared.misc.extension.extensionHook
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal val applicationInitHook =
    extensionHook(
        fingerprint =
            Fingerprint(
                name = "onCreate",
                custom = { _, classDef ->
                    classDef.endsWith("/InstagramAppShell;")
                },
            ),
        insertIndexResolver = { method ->
            method.instructions.indexOfFirst { instruction ->
                instruction.opcode == Opcode.INVOKE_SUPER
            } + 1
        },
        contextRegisterResolver = { method ->
            val invokeSuperInstruction =
                method.instructions.first { instruction ->
                    instruction.opcode == Opcode.INVOKE_SUPER
                }

            "v${invokeSuperInstruction.registersUsed[0]}"
        },
    )
