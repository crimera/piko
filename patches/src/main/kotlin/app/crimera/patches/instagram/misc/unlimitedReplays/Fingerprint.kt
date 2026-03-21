/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.unlimitedReplays

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object EphemeralMediaViewUpdate1Fingerprint : Fingerprint(
    strings = listOf("Entry should exist before function call", "Visual message is missing from thread entry"),
)

internal object EphemeralMediaViewUpdate2Fingerprint : Fingerprint(
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
    strings = listOf("seen_count", "tap_models"),
)

internal object EphemeralMediaViewUpdate3Fingerprint : Fingerprint(
    custom = { method, _ ->
        method.parameterTypes.firstOrNull() == "Lcom/instagram/common/session/UserSession;" &&
            method.parameterTypes.size == 3
    },
    returnType = "V",
    accessFlags = listOf(AccessFlags.DECLARED_SYNCHRONIZED, AccessFlags.PUBLIC, AccessFlags.FINAL),
    filters =
        listOf(
            opcode(Opcode.MOVE_OBJECT, location = InstructionLocation.MatchFirst()),
            opcode(Opcode.MONITOR_ENTER, location = InstructionLocation.MatchAfterImmediately()),
        ),
)
